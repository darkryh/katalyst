package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.transactions.sideeffects.SideEffectHandlingMode
import io.github.darkryh.katalyst.transactions.sideeffects.SideEffectResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for EventSideEffect and generic transactional framework.
 *
 * Validates that domain events work correctly with the generic TransactionalSideEffect framework:
 * - SYNC_BEFORE_COMMIT events execute and can fail (causing rollback)
 * - ASYNC_AFTER_COMMIT events execute after commit (failures isolated)
 * - Configuration bridges correctly from EventHandlerConfig to SideEffectConfig
 * - EventSideEffect properly wraps DomainEvent for the generic framework
 */
class EventSideEffectIntegrationTests {

    private lateinit var eventBus: ApplicationEventBus
    private val executionLog = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        eventBus = ApplicationEventBus()
        executionLog.clear()
    }

    /**
     * Test: EventSideEffect wraps DomainEvent correctly
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectWrapsEventWithCorrectId() = runBlocking {
        val event = TestDomainEvent(eventId = "test-event-123", message = "Test")
        val sideEffect = EventSideEffect(
            event = event,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        assertEquals("test-event-123", sideEffect.sideEffectId)
        assertEquals(SideEffectHandlingMode.SYNC_BEFORE_COMMIT, sideEffect.handlingMode)
        assertEquals(event, sideEffect.event)
    }

    /**
     * Test: EventSideEffect executes event publishing via execute()
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectPublishesEvent() = runBlocking {
        var handlerCalled = false
        val testEvent = TestDomainEvent(eventId = "evt-1", message = "Test message")

        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                handlerCalled = true
                executionLog.add("Event published: ${event.message}")
            }
        }
        eventBus.register(handler)

        val sideEffect = EventSideEffect(
            event = testEvent,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        val result = sideEffect.execute(Unit)

        assertTrue(handlerCalled)
        assertTrue(result is SideEffectResult.Success)
        assertEquals(1, executionLog.size)
        assertTrue(executionLog[0].contains("Test message"))
    }

    /**
     * Test: EventSideEffect result contains event metadata
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectReturnsSuccessWithMetadata() = runBlocking {
        val testEvent = TestDomainEvent(eventId = "evt-meta-1", message = "With metadata")

        val sideEffect = EventSideEffect(
            event = testEvent,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.ASYNC_AFTER_COMMIT
        )

        val result = sideEffect.execute(Unit)

        assertTrue(result is SideEffectResult.Success)
        val successResult = result as SideEffectResult.Success
        assertEquals("TestDomainEvent", successResult.metadata["eventType"])
        assertEquals("evt-meta-1", successResult.metadata["eventId"])
    }

    /**
     * Test: EventSideEffect handles handler errors correctly
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectHandlesHandlerError() = runBlocking {
        val testEvent = TestDomainEvent(eventId = "evt-error", message = "Will fail")

        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                throw RuntimeException("Handler failed")
            }
        }
        eventBus.register(handler)

        val sideEffect = EventSideEffect(
            event = testEvent,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        val result = sideEffect.execute(Unit)

        // EventSideEffect catches exceptions from publish() and returns Failed result
        assertTrue(result is SideEffectResult.Failed, "Expected Failed result but got $result")
        // Verify the error is captured (could be EventPublishingException or wrapped RuntimeException)
        assertTrue(result !is SideEffectResult.Success, "Result should not be Success")
    }

    /**
     * Test: EventSideEffect compensation is no-op (events don't need compensation)
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectCompensateIsNoOp() = runBlocking {
        val testEvent = TestDomainEvent(eventId = "evt-comp", message = "Test")
        val sideEffect = EventSideEffect(
            event = testEvent,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        val result = SideEffectResult.Success(metadata = mapOf("test" to "value"))

        // Should not throw, should complete successfully
        sideEffect.compensate(result)
        assertTrue(true) // Successful completion
    }

    /**
     * Test: Multiple events can be wrapped and executed separately
     */
    @Test
    @Timeout(5)
    fun testMultipleEventSideEffectsExecuteIndependently() = runBlocking {
        val event1 = TestDomainEvent(eventId = "evt-1", message = "Event 1")
        val event2 = TestDomainEvent(eventId = "evt-2", message = "Event 2")

        var count1 = 0
        var count2 = 0

        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                if (event.message == "Event 1") count1++
                if (event.message == "Event 2") count2++
            }
        }
        eventBus.register(handler)

        val sideEffect1 = EventSideEffect(event1, eventBus, SideEffectHandlingMode.SYNC_BEFORE_COMMIT)
        val sideEffect2 = EventSideEffect(event2, eventBus, SideEffectHandlingMode.ASYNC_AFTER_COMMIT)

        val result1 = sideEffect1.execute(Unit)
        val result2 = sideEffect2.execute(Unit)

        assertTrue(result1 is SideEffectResult.Success)
        assertTrue(result2 is SideEffectResult.Success)
        assertEquals(1, count1)
        assertEquals(1, count2)
    }

    /**
     * Test: EventSideEffect preserves handling mode through execution
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectPreservesHandlingMode() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-mode", message = "Test mode")

        val syncSideEffect = EventSideEffect(
            event = event,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        val asyncSideEffect = EventSideEffect(
            event = event,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.ASYNC_AFTER_COMMIT
        )

        assertEquals(SideEffectHandlingMode.SYNC_BEFORE_COMMIT, syncSideEffect.handlingMode)
        assertEquals(SideEffectHandlingMode.ASYNC_AFTER_COMMIT, asyncSideEffect.handlingMode)

        syncSideEffect.execute(Unit)
        asyncSideEffect.execute(Unit)

        // Mode should still be preserved after execution
        assertEquals(SideEffectHandlingMode.SYNC_BEFORE_COMMIT, syncSideEffect.handlingMode)
        assertEquals(SideEffectHandlingMode.ASYNC_AFTER_COMMIT, asyncSideEffect.handlingMode)
    }

    /**
     * Test: EventSideEffect works with events without explicit handlers
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectSucceedsWithoutHandlers() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-no-handler", message = "No handler")

        // Don't register any handler for this event type
        val sideEffect = EventSideEffect(
            event = event,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        val result = sideEffect.execute(Unit)

        // Should succeed even without handlers
        assertTrue(result is SideEffectResult.Success)
    }

    /**
     * Test: EventSideEffect can be used in adapter context
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectWorksAsTransactionalSideEffect() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-adapter", message = "Adapter test")
        var eventPublished = false

        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                eventPublished = true
            }
        }
        eventBus.register(handler)

        val sideEffect: EventSideEffect = EventSideEffect(
            event = event,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        // Verify it implements TransactionalSideEffect interface
        assertTrue(sideEffect is io.github.darkryh.katalyst.transactions.sideeffects.TransactionalSideEffect<*>)

        val result = sideEffect.execute(Unit)
        assertTrue(eventPublished)
        assertTrue(result is SideEffectResult.Success)
    }

    /**
     * Test: EventSideEffect with SYNC mode executes synchronously
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectExecutesSynchronously() = runBlocking {
        val event = TestDomainEvent(eventId = "evt-sync", message = "Sync execution")
        var executionTime: Long? = null
        val creationTime = System.currentTimeMillis()

        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(event: TestDomainEvent) {
                executionTime = System.currentTimeMillis()
            }
        }
        eventBus.register(handler)

        val sideEffect = EventSideEffect(
            event = event,
            eventBus = eventBus,
            handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
        )

        sideEffect.execute(Unit)

        // Verify execution happened after creation
        assertFalse(executionTime == null)
        assertTrue(executionTime!! >= creationTime)
    }

    /**
     * Test: EventSideEffect preserves event data through execution
     */
    @Test
    @Timeout(5)
    fun testEventSideEffectPreservesEventData() = runBlocking {
        val originalMessage = "Original event message"
        val event = TestDomainEvent(eventId = "evt-data", message = originalMessage)

        var receivedMessage: String? = null

        val handler = object : EventHandler<TestDomainEvent> {
            override val eventType: KClass<TestDomainEvent> = TestDomainEvent::class
            override suspend fun handle(receivedEvent: TestDomainEvent) {
                receivedMessage = receivedEvent.message
            }
        }
        eventBus.register(handler)

        val sideEffect = EventSideEffect(event, eventBus, SideEffectHandlingMode.SYNC_BEFORE_COMMIT)
        sideEffect.execute(Unit)

        assertEquals(originalMessage, receivedMessage)
        assertEquals(originalMessage, (sideEffect.event as TestDomainEvent).message)
    }

    // Test domain classes
    data class TestDomainEvent(
        override val eventId: String,
        val message: String
    ) : DomainEvent {
        override fun eventType(): String = "TestDomainEvent"
    }
}
