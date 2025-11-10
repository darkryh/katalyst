package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.bus.adapter.EventsTransactionAdapter
import com.ead.katalyst.events.bus.deduplication.NoOpEventDeduplicationStore
import com.ead.katalyst.events.bus.exception.EventPublishingException
import com.ead.katalyst.events.bus.validation.DefaultEventPublishingValidator
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
 * Integration Test: User Registration with Event Handler Rollback
 *
 * This test demonstrates the user's exact use case from their bug report:
 * When registering a new account, if the event handler fails (e.g., creating user profile),
 * the entire transaction should rollback automatically without the Saga pattern.
 *
 * **Original Problem (User's Bug Report):**
 * ```
 * transactionManager.transaction {
 *     account = repository.save(account)
 *     eventBus.publish(UserRegisteredEvent(account.id))  // Handler throws exception
 * }
 * // Problem: Account was saved, but handler failed. Account remains in DB!
 * ```
 *
 * **Solution with Phase 2.5:**
 * With SYNC_BEFORE_COMMIT mode (default), handler failures cause transaction rollback.
 * No Saga pattern needed - just the simple API developers expect.
 */
class UserRegistrationIntegrationTest {

    private lateinit var eventBus: ApplicationEventBus
    private lateinit var eventsAdapter: EventsTransactionAdapter

    // Simulated repository and service
    private val savedAccounts = mutableListOf<Account>()
    private val createdProfiles = mutableListOf<UserProfile>()

    @BeforeEach
    fun setUp() {
        eventBus = ApplicationEventBus()
        eventsAdapter = EventsTransactionAdapter(eventBus)
        savedAccounts.clear()
        createdProfiles.clear()
    }

    /**
     * Test Case 1: User registration with profile creation failure
     *
     * Scenario:
     * 1. User calls register(email, password)
     * 2. Account is saved to database
     * 3. UserRegisteredEvent is published
     * 4. Event handler tries to create profile but fails (exception)
     * 5. Transaction should rollback automatically
     * 6. Account should NOT be in database
     *
     * **This is the user's exact bug report scenario**
     */
    @Test
    @Timeout(10)
    fun `when event handler fails during registration, transaction rolls back automatically`() = runBlocking {
        // Setup: Event and handler
        data class UserRegisteredEvent(val accountId: Long, val email: String) : DomainEvent {
            override val eventId: String = "user-reg-$accountId"
            override fun eventType(): String = "UserRegisteredEvent"
        }

        // Handler that fails (simulates profile creation failure)
        var handlerExecuted = false
        val profileCreationHandler = object : EventHandler<UserRegisteredEvent> {
            override val eventType = UserRegisteredEvent::class
            override suspend fun handle(event: UserRegisteredEvent) {
                handlerExecuted = true
                // Simulate: Try to create profile, but fail
                throw IllegalStateException("Failed to create profile for account ${event.accountId}")
            }
        }

        eventBus.register(profileCreationHandler)
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "UserRegisteredEvent",
                handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT  // Default: transactional
            )
        )

        // Act: Simulate registration transaction
        val transactionContext = TransactionEventContext()

        try {
            // Phase 1: Service code within transaction
            val account = Account(id = 1L, email = "user@example.com", password = "hashed")
            savedAccounts.add(account)  // Save to DB
            println("✓ Account saved: $account")

            // Phase 2: Publish event (queued, not published yet)
            val event = UserRegisteredEvent(accountId = account.id, email = account.email)
            transactionContext.queueEvent(event)
            println("✓ Event published (queued): $event")

            // Phase 3: Before commit - event handlers execute
            // THIS IS WHERE THE FAILURE HAPPENS
            eventsAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, transactionContext)

        } catch (e: EventPublishingException) {
            // Expected: Handler failed, so transaction should rollback
            println("✓ Event handler failed as expected: ${e.failures.first().exception.message}")

            // Simulate rollback: Clear saved data
            savedAccounts.clear()
            println("✓ Transaction rolled back - account removed from DB")
        }

        // Assert: Account should NOT be in the database
        assertTrue(handlerExecuted, "Handler should have executed and failed")
        assertEquals(0, savedAccounts.size, "Account should be rolled back from database")
        assertEquals(0, createdProfiles.size, "Profile should not be created")
    }

    /**
     * Test Case 2: Successful registration without handler errors
     *
     * Scenario:
     * 1. User calls register(email, password)
     * 2. Account is saved
     * 3. UserRegisteredEvent is published
     * 4. Event handler successfully creates profile
     * 5. Transaction should commit
     * 6. Both account and profile should exist
     */
    @Test
    @Timeout(10)
    fun `successful registration with working event handler commits transaction`() = runBlocking {
        data class UserRegisteredEvent(val accountId: Long, val email: String) : DomainEvent {
            override val eventId: String = "user-reg-$accountId"
            override fun eventType(): String = "UserRegisteredEvent"
        }

        // Handler that succeeds
        var handlerExecuted = false
        val profileCreationHandler = object : EventHandler<UserRegisteredEvent> {
            override val eventType = UserRegisteredEvent::class
            override suspend fun handle(event: UserRegisteredEvent) {
                handlerExecuted = true
                // Create profile successfully
                createdProfiles.add(UserProfile(accountId = event.accountId, name = "User"))
                println("✓ Handler: Profile created for account ${event.accountId}")
            }
        }

        eventBus.register(profileCreationHandler)
        eventBus.configureHandlers(
            EventHandlerConfig(
                eventType = "UserRegisteredEvent",
                handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
            )
        )

        // Act: Simulate successful registration transaction
        val transactionContext = TransactionEventContext()

        try {
            val account = Account(id = 1L, email = "user@example.com", password = "hashed")
            savedAccounts.add(account)
            println("✓ Account saved: $account")

            val event = UserRegisteredEvent(accountId = account.id, email = account.email)
            transactionContext.queueEvent(event)
            println("✓ Event queued: $event")

            // Before commit - handlers execute successfully
            eventsAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, transactionContext)
            println("✓ Event handlers executed successfully")

            // Commit
            println("✓ Transaction committed")

        } catch (e: Exception) {
            fail("Should not throw - handler should succeed")
        }

        // Assert: Both account and profile exist
        assertTrue(handlerExecuted, "Handler should have executed")
        assertEquals(1, savedAccounts.size, "Account should be in database")
        assertEquals(1, createdProfiles.size, "Profile should be created by handler")
        assertEquals("user@example.com", savedAccounts[0].email)
    }

    /**
     * Test Case 3: Async notification - handler failure doesn't rollback
     *
     * Scenario:
     * 1. User registers successfully (SYNC handler works)
     * 2. Notification event published with ASYNC mode
     * 3. Notification handler fails (e.g., email service down)
     * 4. Notification failure should NOT rollback account creation
     * 5. Account should remain in database
     */
    @Test
    @Timeout(10)
    fun `async event handler failure doesn't rollback account creation`() = runBlocking {
        data class UserRegisteredEvent(val accountId: Long, val email: String) : DomainEvent {
            override val eventId: String = "user-reg-$accountId"
            override fun eventType(): String = "UserRegisteredEvent"
        }

        data class SendWelcomeEmailEvent(val email: String) : DomainEvent {
            override val eventId: String = "email-$email"
            override fun eventType(): String = "SendWelcomeEmailEvent"
        }

        // SYNC handler: succeeds
        val profileHandler = object : EventHandler<UserRegisteredEvent> {
            override val eventType = UserRegisteredEvent::class
            override suspend fun handle(event: UserRegisteredEvent) {
                createdProfiles.add(UserProfile(accountId = event.accountId, name = "User"))
                println("✓ Profile created")
            }
        }

        // ASYNC handler: fails (email service down)
        var emailHandlerExecuted = false
        val emailHandler = object : EventHandler<SendWelcomeEmailEvent> {
            override val eventType = SendWelcomeEmailEvent::class
            override suspend fun handle(event: SendWelcomeEmailEvent) {
                emailHandlerExecuted = true
                throw RuntimeException("Email service unavailable")
            }
        }

        eventBus.register(profileHandler)
        eventBus.register(emailHandler)

        eventBus.configureHandlers(
            EventHandlerConfig("UserRegisteredEvent", EventHandlingMode.SYNC_BEFORE_COMMIT)
        )
        eventBus.configureHandlers(
            EventHandlerConfig("SendWelcomeEmailEvent", EventHandlingMode.ASYNC_AFTER_COMMIT)
        )

        val transactionContext = TransactionEventContext()

        try {
            // Save account
            val account = Account(id = 1L, email = "user@example.com", password = "hashed")
            savedAccounts.add(account)
            println("✓ Account saved")

            // Queue both events
            transactionContext.queueEvent(UserRegisteredEvent(account.id, account.email))
            transactionContext.queueEvent(SendWelcomeEmailEvent(account.email))
            println("✓ Events queued")

            // Before commit: SYNC event executes (profile creation succeeds)
            eventsAdapter.onPhase(TransactionPhase.BEFORE_COMMIT, transactionContext)
            println("✓ SYNC handler executed successfully")

            // Commit
            println("✓ Transaction committed")

            // After commit: ASYNC event executes (email handler fails, but isolated)
            eventsAdapter.onPhase(TransactionPhase.AFTER_COMMIT, transactionContext)
            println("✓ ASYNC handler failed (but isolated from transaction)")

        } catch (e: EventPublishingException) {
            fail("BEFORE_COMMIT should succeed - email event is async")
        }

        // Assert: Account and profile exist despite email failure
        assertTrue(emailHandlerExecuted, "Email handler should have executed")
        assertEquals(1, savedAccounts.size, "Account should be in database")
        assertEquals(1, createdProfiles.size, "Profile should be created")
        println("\n✓ SUCCESS: Account committed despite async email handler failure")
    }

    // Test data models
    data class Account(
        val id: Long,
        val email: String,
        val password: String
    )

    data class UserProfile(
        val accountId: Long,
        val name: String
    )

    private fun fail(message: String): Nothing {
        throw AssertionError(message)
    }
}
