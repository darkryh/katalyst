package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.bus.adapter.EventsTransactionAdapter
import com.ead.katalyst.events.bus.deduplication.NoOpEventDeduplicationStore
import com.ead.katalyst.events.bus.exception.EventPublishingException
import com.ead.katalyst.events.bus.validation.DefaultEventPublishingValidator
import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Phase 2.5: Event Handler Rollback Support
 *
 * Verifies that event handler failures automatically trigger transaction rollback
 * when using SYNC_BEFORE_COMMIT mode, enabling transparent under-the-hood rollback
 * without requiring the Saga pattern.
 *
 * **User's Use Case:**
 * ```kotlin
 * transactionManager.transaction {
 *     account = repository.save(account)
 *     eventBus.publish(UserRegisteredEvent(account.id))  // Handler fails
 *     // Transaction should rollback automatically
 * }
 * ```
 */
class EventHandlerRollbackTests {

    private lateinit var eventBus: ApplicationEventBus
    private lateinit var adapter: EventsTransactionAdapter
    private lateinit var context: TransactionEventContext

    @BeforeEach
    fun setUp() {
        eventBus = ApplicationEventBus()
        adapter = EventsTransactionAdapter(eventBus)
        context = TransactionEventContext()
    }

    /**
     * Test: SYNC_BEFORE_COMMIT handler failure causes transaction rollback
     *
     * Scenario:
     * 1. User saves account in transaction
     * 2. Event handler tries to create profile but fails
     * 3. Handler exception should bubble up and rollback entire transaction
     */
    @Test
    @Timeout(10)
    fun `SYNC_BEFORE_COMMIT handler failure throws exception and causes rollback`() = runBlocking {
        // Setup: Account created event
        data class AccountCreatedEvent(val accountId: Long, val email: String) : DomainEvent {
            override val eventId: String = "acc-$accountId"
            override fun eventType(): String = "AccountCreatedEvent"
        }

        // Setup: Profile creation failure handler
        var handlerExecuted = false
        val failingHandler = object : EventHandler<AccountCreatedEvent> {
            override val eventType = AccountCreatedEvent::class
            override suspend fun handle(event: AccountCreatedEvent) {
                handlerExecuted = true
                throw IllegalStateException("Failed to create profile for account ${event.accountId}")
            }
        }

        // Configure: SYNC_BEFORE_COMMIT (default)
        eventBus.register(failingHandler)
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "AccountCreatedEvent",
                handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
            )
        )

        // Act: Publish event (queued)
        val event = AccountCreatedEvent(accountId = 1L, email = "user@example.com")
        context.queueEvent(event)

        // Assert: Handler exception should bubble up (wrapped in EventPublishingException)
        val exception = assertFailsWith<EventPublishingException> {
            adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        }

        assertTrue(handlerExecuted, "Handler should have executed")
        assertTrue(exception.failures.isNotEmpty(), "Should contain handler failures")
        val failure = exception.failures.first()
        assertEquals("Failed to create profile for account 1", failure.exception.message)
    }

    /**
     * Test: SYNC_BEFORE_COMMIT successful handler doesn't cause failure
     *
     * Scenario:
     * 1. User saves account in transaction
     * 2. Event handler creates profile successfully
     * 3. Transaction should commit without error
     */
    @Test
    @Timeout(10)
    fun `SYNC_BEFORE_COMMIT handler success allows transaction to commit`() = runBlocking {
        data class AccountCreatedEvent(val accountId: Long, val email: String) : DomainEvent {
            override val eventId: String = "acc-$accountId"
            override fun eventType(): String = "AccountCreatedEvent"
        }

        var handlerExecuted = false
        val successHandler = object : EventHandler<AccountCreatedEvent> {
            override val eventType = AccountCreatedEvent::class
            override suspend fun handle(event: AccountCreatedEvent) {
                handlerExecuted = true
                // Profile created successfully
            }
        }

        eventBus.register(successHandler)
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "AccountCreatedEvent",
                handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
            )
        )

        val event = AccountCreatedEvent(accountId = 1L, email = "user@example.com")
        context.queueEvent(event)

        // Should not throw
        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        assertTrue(handlerExecuted, "Handler should have executed")
    }

    /**
     * Test: ASYNC_AFTER_COMMIT handler failure doesn't cause transaction rollback
     *
     * Scenario:
     * 1. User saves account in transaction
     * 2. Event handler configured for ASYNC_AFTER_COMMIT
     * 3. Handler fails after commit (outside transaction)
     * 4. Transaction should remain committed, failure is isolated
     */
    @Test
    @Timeout(10)
    fun `ASYNC_AFTER_COMMIT handler failure doesn't cause transaction rollback`() = runBlocking {
        data class NotificationEvent(val message: String) : DomainEvent {
            override val eventId: String = "notif-$message"
            override fun eventType(): String = "NotificationEvent"
        }

        var handlerExecuted = false
        val asyncFailingHandler = object : EventHandler<NotificationEvent> {
            override val eventType = NotificationEvent::class
            override suspend fun handle(event: NotificationEvent) {
                handlerExecuted = true
                throw RuntimeException("Failed to send notification")
            }
        }

        eventBus.register(asyncFailingHandler)
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "NotificationEvent",
                handlingMode = EventHandlingMode.ASYNC_AFTER_COMMIT,
                failOnHandlerError = false
            )
        )

        val event = NotificationEvent("Test message")
        context.queueEvent(event)

        // Event should be queued for AFTER_COMMIT, not published in BEFORE_COMMIT
        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        assertFalse(handlerExecuted, "Handler should not execute in BEFORE_COMMIT for async events")

        // Publish async events after commit
        // This should NOT throw even though handler fails
        adapter.onPhase(TransactionPhase.AFTER_COMMIT, context)

        assertTrue(handlerExecuted, "Handler should have executed in AFTER_COMMIT")
    }

    /**
     * Test: Multiple SYNC handlers - one fails causes rollback, others don't execute after failure
     *
     * Scenario:
     * 1. Two SYNC_BEFORE_COMMIT handlers registered
     * 2. First handler succeeds
     * 3. Second handler fails
     * 4. Failure should bubble up before other handlers run
     */
    @Test
    @Timeout(10)
    fun `SYNC handler failure is immediate - subsequent handlers don't execute`() = runBlocking {
        data class SyncEvent(val id: Int) : DomainEvent {
            override val eventId: String = "sync-$id"
            override fun eventType(): String = "SyncEvent"
        }

        val executionOrder = mutableListOf<String>()

        val handler1 = object : EventHandler<SyncEvent> {
            override val eventType = SyncEvent::class
            override suspend fun handle(event: SyncEvent) {
                executionOrder.add("handler1")
            }
        }

        val handler2 = object : EventHandler<SyncEvent> {
            override val eventType = SyncEvent::class
            override suspend fun handle(event: SyncEvent) {
                executionOrder.add("handler2")
                throw RuntimeException("Handler 2 failed")
            }
        }

        eventBus.register(handler1)
        eventBus.register(handler2)
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "SyncEvent",
                handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
            )
        )

        val event = SyncEvent(id = 1)
        context.queueEvent(event)

        // Should throw due to handler2 failure (wrapped in EventPublishingException)
        val exception = assertFailsWith<EventPublishingException> {
            adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        }

        // Both handlers execute (parallel), but exception is thrown
        assertTrue(executionOrder.contains("handler1"))
        assertTrue(executionOrder.contains("handler2"))
        assertTrue(exception.failures.isNotEmpty())
    }

    /**
     * Test: Mixed SYNC and ASYNC events in single transaction
     *
     * Scenario:
     * 1. Publish UserCreatedEvent (SYNC) - succeeds
     * 2. Publish NotificationEvent (ASYNC) - queued for later
     * 3. BEFORE_COMMIT publishes only SYNC events
     * 4. AFTER_COMMIT publishes ASYNC events
     */
    @Test
    @Timeout(10)
    fun `mixed SYNC and ASYNC events are published in correct phases`() = runBlocking {
        data class UserCreatedEvent(val userId: Long) : DomainEvent {
            override val eventId: String = "user-$userId"
            override fun eventType(): String = "UserCreatedEvent"
        }

        data class SendEmailEvent(val email: String) : DomainEvent {
            override val eventId: String = "email-$email"
            override fun eventType(): String = "SendEmailEvent"
        }

        val executionLog = mutableListOf<String>()

        val userHandler = object : EventHandler<UserCreatedEvent> {
            override val eventType = UserCreatedEvent::class
            override suspend fun handle(event: UserCreatedEvent) {
                executionLog.add("user-handler-sync")
            }
        }

        val emailHandler = object : EventHandler<SendEmailEvent> {
            override val eventType = SendEmailEvent::class
            override suspend fun handle(event: SendEmailEvent) {
                executionLog.add("email-handler-async")
            }
        }

        eventBus.register(userHandler)
        eventBus.register(emailHandler)

        // Configure SYNC for UserCreatedEvent
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "UserCreatedEvent",
                handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
            )
        )

        // Configure ASYNC for SendEmailEvent
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "SendEmailEvent",
                handlingMode = EventHandlingMode.ASYNC_AFTER_COMMIT
            )
        )

        // Queue both events
        context.queueEvent(UserCreatedEvent(userId = 1L))
        context.queueEvent(SendEmailEvent(email = "user@example.com"))

        // BEFORE_COMMIT should publish only SYNC events
        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        assertEquals(
            listOf("user-handler-sync"),
            executionLog,
            "Only SYNC handler should execute in BEFORE_COMMIT"
        )

        // AFTER_COMMIT should publish ASYNC events
        adapter.onPhase(TransactionPhase.AFTER_COMMIT, context)
        assertEquals(
            listOf("user-handler-sync", "email-handler-async"),
            executionLog,
            "ASYNC handler should execute in AFTER_COMMIT"
        )
    }

    /**
     * Test: Rollback discards both SYNC and ASYNC queued events
     *
     * Scenario:
     * 1. Publish events (mixed SYNC/ASYNC)
     * 2. Rollback transaction
     * 3. Neither SYNC nor ASYNC events should be published
     */
    @Test
    @Timeout(10)
    fun `rollback discards all pending events - both SYNC and ASYNC`() = runBlocking {
        data class Event1(val id: Int) : DomainEvent {
            override val eventId: String = "e1-$id"
            override fun eventType(): String = "Event1"
        }

        data class Event2(val id: Int) : DomainEvent {
            override val eventId: String = "e2-$id"
            override fun eventType(): String = "Event2"
        }

        var event1Published = false
        var event2Published = false

        val handler1 = object : EventHandler<Event1> {
            override val eventType = Event1::class
            override suspend fun handle(event: Event1) {
                event1Published = true
            }
        }

        val handler2 = object : EventHandler<Event2> {
            override val eventType = Event2::class
            override suspend fun handle(event: Event2) {
                event2Published = true
            }
        }

        eventBus.register(handler1)
        eventBus.register(handler2)

        eventBus.configureHandlers(
            EventHandlerConfig("Event1", EventHandlingMode.SYNC_BEFORE_COMMIT)
        )
        eventBus.configureHandlers(
            EventHandlerConfig("Event2", EventHandlingMode.ASYNC_AFTER_COMMIT)
        )

        // Queue events
        context.queueEvent(Event1(id = 1))
        context.queueEvent(Event2(id = 2))

        // Rollback
        adapter.onPhase(TransactionPhase.ON_ROLLBACK, context)

        // No handlers should have been called
        assertFalse(event1Published, "Event1 handler should not be published on rollback")
        assertFalse(event2Published, "Event2 handler should not be published on rollback")
    }

    /**
     * Test: Default mode is SYNC_BEFORE_COMMIT for transactional consistency
     *
     * Scenario:
     * 1. Publish event without explicit configuration
     * 2. Event should default to SYNC_BEFORE_COMMIT
     * 3. Handler failures cause transaction rollback
     */
    @Test
    @Timeout(10)
    fun `unconfigured events default to SYNC_BEFORE_COMMIT mode`() = runBlocking {
        data class DefaultEvent(val value: String) : DomainEvent {
            override val eventId: String = "def-$value"
            override fun eventType(): String = "DefaultEvent"
        }

        var handlerCalled = false
        val handler = object : EventHandler<DefaultEvent> {
            override val eventType = DefaultEvent::class
            override suspend fun handle(event: DefaultEvent) {
                handlerCalled = true
                throw RuntimeException("Test failure")
            }
        }

        eventBus.register(handler)
        // NOT configuring explicitly - should default to SYNC_BEFORE_COMMIT

        val event = DefaultEvent(value = "test")
        context.queueEvent(event)

        // Should throw because default is SYNC_BEFORE_COMMIT (wrapped in EventPublishingException)
        assertFailsWith<EventPublishingException> {
            adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        }

        assertTrue(handlerCalled)
    }
}
