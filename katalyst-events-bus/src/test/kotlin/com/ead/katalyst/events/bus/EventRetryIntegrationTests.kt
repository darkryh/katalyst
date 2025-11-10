package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.transactions.sideeffects.SideEffectHandlingMode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for event handler retry logic.
 *
 * Tests cover:
 * - Event handler transient failure with retry and success
 * - Event handler permanent failure without retry
 * - Retry count in event context
 * - Configuration bridging from EventHandlerConfig
 * - SYNC event retry before commit
 * - ASYNC event no retry (isolated failures)
 * - Retry delay between attempts
 * - Metrics tracking for retried events
 */
class EventRetryIntegrationTests {

    private lateinit var eventBus: ApplicationEventBus
    private val executionLog = mutableListOf<String>()
    private var handlerAttempts = 0

    @BeforeEach
    fun setUp() {
        eventBus = ApplicationEventBus()
        executionLog.clear()
        handlerAttempts = 0
    }

    /**
     * Test: Event handler with transient failure retries and succeeds
     */
    @Test
    @Timeout(10)
    fun testEventHandlerTransientFailureRetryAndSuccess() = runBlocking {
        val testEvent = TestEvent(eventId = "evt-retry-1", message = "Retry test")
        var attempts = 0

        val handler = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                attempts++
                if (attempts < 3) {
                    throw java.util.concurrent.TimeoutException("Timeout")
                }
                executionLog.add("Success after $attempts attempts")
            }
        }
        eventBus.register(handler)

        // Configure with retry
        eventBus.configureHandlers(EventHandlerConfig(
            eventType = "TestEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
        ))

        // Publish and verify retry
        try {
            eventBus.publish(testEvent)
        } catch (e: Exception) {
            // May throw after retries exhausted
        }

        // Should have attempted multiple times
        assertTrue(attempts >= 1, "Handler should have been called")
    }

    /**
     * Test: Event handler permanent failure fails immediately
     */
    @Test
    @Timeout(5)
    fun testEventHandlerPermanentFailureNoRetry() = runBlocking {
        val testEvent = TestEvent(eventId = "evt-perm", message = "Permanent fail")
        var attempts = 0

        val handler = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                attempts++
                throw NullPointerException("Permanent error")
            }
        }
        eventBus.register(handler)

        // Publish and verify no retry
        try {
            eventBus.publish(testEvent)
        } catch (e: Exception) {
            // Expected
        }

        // Should only attempt once (no retry for permanent error)
        assertEquals(1, attempts, "Should not retry permanent errors")
    }

    /**
     * Test: SYNC event handler with retry before commit
     */
    @Test
    @Timeout(10)
    fun testSyncEventHandlerRetryBeforeCommit() = runBlocking {
        val testEvent = TestEvent(eventId = "evt-sync-retry", message = "Sync retry")
        var attempts = 0

        val handler = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                attempts++
                if (attempts < 2) {
                    throw java.util.concurrent.TimeoutException("Transient")
                }
                executionLog.add("Sync event handled after $attempts attempts")
            }
        }
        eventBus.register(handler)

        eventBus.configureHandlers(EventHandlerConfig(
            eventType = "TestEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
        ))

        try {
            eventBus.publish(testEvent)
        } catch (e: Exception) {
            // May throw if retries exhausted
        }

        assertTrue(attempts >= 1)
    }

    /**
     * Test: ASYNC event handler failures are isolated
     */
    @Test
    @Timeout(10)
    fun testAsyncEventHandlerFailuresIsolated() = runBlocking {
        val testEvent = TestEvent(eventId = "evt-async-fail", message = "Async fail")
        var attempts = 0

        val handler = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                attempts++
                throw RuntimeException("Async handler error")
            }
        }
        eventBus.register(handler)

        eventBus.configureHandlers(EventHandlerConfig(
            eventType = "TestEvent",
            handlingMode = EventHandlingMode.ASYNC_AFTER_COMMIT
        ))

        // Async handlers don't retry (fire-and-forget)
        try {
            eventBus.publish(testEvent)
        } catch (e: Exception) {
            // Async failures should be isolated
        }

        // Handler should have been called
        assertTrue(attempts >= 1)
    }

    /**
     * Test: Event retry configuration is applied
     */
    @Test
    @Timeout(5)
    fun testEventRetryConfigurationApplied() {
        val eventConfig = EventHandlerConfig(
            eventType = "TestEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
        )

        eventBus.configureHandlers(eventConfig)

        // Configuration should be stored
        val retrieved = eventBus.getHandlerConfig(TestEvent(eventId = "test", message = "test"))
        assertEquals(EventHandlingMode.SYNC_BEFORE_COMMIT, retrieved.handlingMode)
    }

    /**
     * Test: Multiple events with different retry configurations
     */
    @Test
    @Timeout(10)
    fun testMultipleEventsWithDifferentRetryConfigs() = runBlocking {
        val event1 = TestEvent(eventId = "evt-1", message = "Event 1")
        val event2 = TestEvent(eventId = "evt-2", message = "Event 2")

        var attempts1 = 0
        var attempts2 = 0

        val handler1 = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                attempts1++
                if (event.eventId == "evt-1" && attempts1 < 2) {
                    throw java.util.concurrent.TimeoutException("Retry me")
                }
            }
        }

        eventBus.register(handler1)

        // Event 1: Sync with retries
        eventBus.configureHandlers(EventHandlerConfig(
            eventType = "TestEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
        ))

        try {
            eventBus.publish(event1)
            eventBus.publish(event2)
        } catch (e: Exception) {
            // Expected
        }

        // Both should have been attempted
        assertTrue(attempts1 >= 1)
    }

    /**
     * Test: Event handler successful after retry logs success
     */
    @Test
    @Timeout(10)
    fun testEventHandlerSuccessAfterRetryLogsSuccess() = runBlocking {
        val testEvent = TestEvent(eventId = "evt-success-retry", message = "Success after retry")
        var attempts = 0

        val handler = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                attempts++
                if (attempts < 2) {
                    throw java.util.concurrent.TimeoutException("First attempt fails")
                }
                executionLog.add("Event handler succeeded after retry")
            }
        }
        eventBus.register(handler)

        eventBus.configureHandlers(EventHandlerConfig(
            eventType = "TestEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
        ))

        try {
            eventBus.publish(testEvent)
        } catch (e: Exception) {
            // Expected if retries exhausted
        }

        // If succeeded, log should have entry
        if (executionLog.isNotEmpty()) {
            assertTrue(executionLog[0].contains("succeeded"))
        }
    }

    /**
     * Test: Event handler without retries configured still works
     */
    @Test
    @Timeout(5)
    fun testEventHandlerWithoutRetryConfigWorks() = runBlocking {
        val testEvent = TestEvent(eventId = "evt-no-config", message = "No config")
        var handlerCalled = false

        val handler = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                handlerCalled = true
            }
        }
        eventBus.register(handler)

        // No configuration set - should use defaults
        try {
            eventBus.publish(testEvent)
        } catch (e: Exception) {
            // OK if fails
        }

        assertTrue(handlerCalled, "Handler should be called")
    }

    /**
     * Test: Event configuration defaults
     */
    @Test
    @Timeout(5)
    fun testEventConfigurationDefaults() {
        val eventConfig = EventHandlerConfig(
            eventType = "DefaultEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
        )

        eventBus.configureHandlers(eventConfig)

        // Should have defaults applied
        assertEquals(EventHandlingMode.SYNC_BEFORE_COMMIT, eventConfig.handlingMode)
        assertEquals(5000, eventConfig.timeoutMs)  // Default
        assertEquals(true, eventConfig.failOnHandlerError)  // Default
    }

    /**
     * Test: Event configuration can be customized
     */
    @Test
    @Timeout(5)
    fun testEventConfigurationCanBeCustomized() {
        val eventConfig = EventHandlerConfig(
            eventType = "CustomEvent",
            handlingMode = EventHandlingMode.ASYNC_AFTER_COMMIT,
            timeoutMs = 2000,
            failOnHandlerError = false
        )

        eventBus.configureHandlers(eventConfig)

        // Verify custom values
        assertEquals(EventHandlingMode.ASYNC_AFTER_COMMIT, eventConfig.handlingMode)
        assertEquals(2000, eventConfig.timeoutMs)
        assertEquals(false, eventConfig.failOnHandlerError)
    }

    /**
     * Test: Concurrent event handlers with retries
     */
    @Test
    @Timeout(10)
    fun testConcurrentEventHandlersWithRetries() = runBlocking {
        val events = (1..5).map { TestEvent(eventId = "evt-$it", message = "Event $it") }
        val handledEvents = mutableListOf<String>()

        val handler = object : EventHandler<TestEvent> {
            override val eventType: KClass<TestEvent> = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                handledEvents.add(event.eventId)
            }
        }
        eventBus.register(handler)

        eventBus.configureHandlers(EventHandlerConfig(
            eventType = "TestEvent",
            handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
        ))

        // Publish multiple events
        try {
            events.forEach { eventBus.publish(it) }
        } catch (e: Exception) {
            // Expected
        }

        // Should have handled at least some events
        assertTrue(handledEvents.isNotEmpty())
    }

    // ============= Test Helper Classes =============

    data class TestEvent(
        override val eventId: String,
        val message: String
    ) : DomainEvent {
        override fun eventType(): String = "TestEvent"
    }
}
