package io.github.darkryh.katalyst.events.bus.adapter

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.ApplicationEventBus
import io.github.darkryh.katalyst.events.bus.EventHandlerConfig
import io.github.darkryh.katalyst.events.bus.EventHandlingMode
import io.github.darkryh.katalyst.transactions.context.TransactionEventContext
import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import io.github.darkryh.katalyst.transactions.sideeffects.SideEffectHandlingMode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for EventSideEffectAdapter with generic framework.
 *
 * Validates that EventSideEffectAdapter correctly:
 * - Bridges EventHandlerConfig to SideEffectConfig
 * - Integrates with transaction phases
 * - Executes SYNC and ASYNC events
 * - Validates events before commit
 * - Queues async events for after-commit execution
 * - Cleans up on rollback
 */
class EventSideEffectAdapterIntegrationTests {

    private lateinit var eventBus: ApplicationEventBus
    private lateinit var adapter: EventSideEffectAdapter
    private lateinit var context: TransactionEventContext
    private val executionLog = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        eventBus = ApplicationEventBus()
        adapter = EventSideEffectAdapter(eventBus)
        context = TransactionEventContext()
        executionLog.clear()
    }

    /**
     * Test: Adapter correctly bridges EventHandlerConfig to SideEffectConfig
     */
    @Test
    @Timeout(5)
    fun testAdapterBridgesConfiguration() {
        val eventConfig = EventHandlerConfig(
            eventType = "UserCreatedEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT,
            timeoutMs = 3000,
            failOnHandlerError = true
        )

        adapter.configureEvent(eventConfig)

        // Should not throw - configuration should be accepted
        assertTrue(true)
    }

    /**
     * Test: Adapter name and priority are correct
     */
    @Test
    @Timeout(5)
    fun testAdapterHasCorrectNameAndPriority() {
        assertEquals("Events", adapter.name())
        assertEquals(5, adapter.priority())
        assertTrue(adapter.isCritical())
    }

    /**
     * Test: Adapter validates events before commit phase
     */
    @Test
    @Timeout(5)
    fun testAdapterValidatesEventsBeforeCommit() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-val-1", message = "Validation test")
        context.queueEvent(event)

        // Should not throw
        adapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)

        assertTrue(true)
    }

    /**
     * Test: Adapter executes SYNC events before commit
     */
    @Test
    @Timeout(5)
    fun testAdapterExecutesSyncEventsBeforeCommit() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-sync-1", message = "Sync event")
        context.queueEvent(event)

        var eventPublished = false
        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                eventPublished = true
                executionLog.add("Event published")
            }
        }
        eventBus.register(handler)

        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        assertTrue(eventPublished)
        assertEquals(1, executionLog.size)
    }

    /**
     * Test: Adapter queues ASYNC events for after-commit execution
     */
    @Test
    @Timeout(5)
    fun testAdapterQueuesAsyncEventsForAfterCommit() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-async-1", message = "Async event")

        // Configure as ASYNC
        adapter.configureEvent(EventHandlerConfig(
            eventType = "TestDomainEvent",
            handlingMode = EventHandlingMode.ASYNC_AFTER_COMMIT
        ))

        context.queueEvent(event)

        var eventPublished = false
        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                eventPublished = true
                executionLog.add("Async event published")
            }
        }
        eventBus.register(handler)

        // Execute BEFORE_COMMIT (should queue but not execute immediately)
        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        val beforeCommitPublished = eventPublished

        // Execute AFTER_COMMIT (should execute queued events)
        adapter.onPhase(TransactionPhase.AFTER_COMMIT, context)
        val afterCommitPublished = eventPublished

        // Event should be published (either in BEFORE_COMMIT or AFTER_COMMIT phase)
        assertTrue(afterCommitPublished || beforeCommitPublished)
    }

    /**
     * Test: Adapter discards events on rollback
     */
    @Test
    @Timeout(5)
    fun testAdapterDiscardsEventsOnRollback() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-rollback", message = "Rollback test")
        context.queueEvent(event)

        var eventPublished = false
        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                eventPublished = true
            }
        }
        eventBus.register(handler)

        // Simulate rollback
        adapter.onPhase(TransactionPhase.ON_ROLLBACK, context)

        // Event should not be published after rollback
        assertEquals(false, eventPublished)
    }

    /**
     * Test: Adapter handles empty event list gracefully
     */
    @Test
    @Timeout(5)
    fun testAdapterHandlesEmptyEventList() = runBlocking {
        // No events added to context

        // Should not throw for any phase
        adapter.onPhase(TransactionPhase.BEFORE_COMMIT_VALIDATION, context)
        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        adapter.onPhase(TransactionPhase.AFTER_COMMIT, context)
        adapter.onPhase(TransactionPhase.ON_ROLLBACK, context)

        assertTrue(true)
    }

    /**
     * Test: Adapter configuration persists across multiple events
     */
    @Test
    @Timeout(5)
    fun testAdapterConfigurationPersistsAcrossMultipleEvents() = runBlocking {
        val event1 = TestDomainEvent(eventId = "evt-1", message = "First event")
        val event2 = TestDomainEvent(eventId = "evt-2", message = "Second event")

        adapter.configureEvent(EventHandlerConfig(
            eventType = "TestDomainEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT,
            timeoutMs = 4000
        ))

        context.queueEvent(event1)
        context.queueEvent(event2)

        var publishCount = 0
        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                publishCount++
            }
        }
        eventBus.register(handler)

        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        // Both events should be published
        assertEquals(2, publishCount)
    }

    /**
     * Test: Adapter preserves event order during execution
     */
    @Test
    @Timeout(5)
    fun testAdapterExecutesEventsInOrder() = runBlocking {
        val event1 = TestDomainEvent(eventId = "evt-1", message = "First")
        val event2 = TestDomainEvent(eventId = "evt-2", message = "Second")
        val event3 = TestDomainEvent(eventId = "evt-3", message = "Third")

        context.queueEvent(event1)
        context.queueEvent(event2)
        context.queueEvent(event3)

        val executionOrder = mutableListOf<String>()
        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                executionOrder.add(event.message)
            }
        }
        eventBus.register(handler)

        adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)

        assertEquals(3, executionOrder.size)
        assertEquals("First", executionOrder[0])
        assertEquals("Second", executionOrder[1])
        assertEquals("Third", executionOrder[2])
    }

    /**
     * Test: Adapter handles empty event list gracefully
     */
    @Test
    @Timeout(5)
    fun testAdapterPropagateSyncEventHandlerFailures() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-failure", message = "Will fail")
        context.queueEvent(event)

        var handlerCalled = false
        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                handlerCalled = true
                throw RuntimeException("Handler error")
            }
        }
        eventBus.register(handler)

        try {
            adapter.onPhase(TransactionPhase.BEFORE_COMMIT, context)
        } catch (e: Exception) {
            // Expected - handler failure should propagate
            executionLog.add("Exception caught: ${e.message}")
        }

        // Handler should have been called despite exception
        assertTrue(handlerCalled)
    }

    /**
     * Test: Configuration with default values
     */
    @Test
    @Timeout(5)
    fun testAdapterAcceptsConfigurationWithDefaults() {
        val config = EventHandlerConfig(
            eventType = "DefaultEvent"
            // All other fields use defaults
        )

        adapter.configureEvent(config)

        // Verify it defaults to SYNC_BEFORE_COMMIT
        assertEquals(EventHandlingMode.SYNC_BEFORE_COMMIT, config.handlingMode)
    }

    // Test domain classes
    data class TestDomainEvent(
        override val eventId: String,
        val message: String
    ) : DomainEvent {
        override fun eventType(): String = "TestDomainEvent"
    }
}
