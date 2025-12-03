package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.EventMetadata
import io.github.darkryh.katalyst.events.bus.exception.EventPublishingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.*

/**
 * Comprehensive tests for ApplicationEventBus.
 *
 * Tests cover:
 * - Handler registration (single, multiple, sealed hierarchies)
 * - Event publishing
 * - Handler execution (async, parallel)
 * - Error handling (handler failures, supervisorScope)
 * - Interceptors (beforePublish, afterPublish, onPublishError)
 * - Event flows (SharedFlow, typed flows)
 * - Handler configuration
 * - Concurrent publishing
 * - Edge cases
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationEventBusTest {

    // ========== HANDLER REGISTRATION TESTS ==========

    @Test
    fun `register should add handler for event type`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler = SpyEventHandler()

        // When
        bus.register(handler)
        bus.publish(TestEvent("test"))

        // Then
        assertEquals(1, handler.invocationCount)
    }

    @Test
    fun `register should add multiple handlers for same event type`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler1 = SpyEventHandler()
        val handler2 = SpyEventHandler()

        // When
        bus.register(handler1)
        bus.register(handler2)
        bus.publish(TestEvent("test"))

        // Then
        assertEquals(1, handler1.invocationCount)
        assertEquals(1, handler2.invocationCount)
    }

    @Test
    fun `register should handle sealed event hierarchies`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val parentHandler = SealedParentHandler()

        // When
        bus.register(parentHandler)
        bus.publish(SealedTestEvent.Created("id-1", "data"))

        // Then
        assertEquals(1, parentHandler.invocationCount)
        assertTrue(parentHandler.events.first() is SealedTestEvent.Created)
    }

    @Test
    fun `register should register handler for all sealed subtypes`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val parentHandler = SealedParentHandler()

        bus.register(parentHandler)

        // When - Publish different subtypes
        bus.publish(SealedTestEvent.Created("id-1", "data"))
        bus.publish(SealedTestEvent.Updated("id-2", "new-data"))
        bus.publish(SealedTestEvent.Deleted("id-3"))

        // Then - Parent handler receives all
        assertEquals(3, parentHandler.invocationCount)
    }

    // ========== EVENT PUBLISHING TESTS ==========

    @Test
    fun `publish should invoke registered handler`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler = SpyEventHandler()
        bus.register(handler)

        val event = TestEvent("test-data")

        // When
        bus.publish(event)

        // Then
        assertEquals(1, handler.invocationCount)
        assertEquals(event, handler.events.first())
    }

    @Test
    fun `publish should invoke all registered handlers`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler1 = SpyEventHandler()
        val handler2 = SpyEventHandler()
        val handler3 = SpyEventHandler()

        bus.register(handler1)
        bus.register(handler2)
        bus.register(handler3)

        // When
        bus.publish(TestEvent("test"))

        // Then
        assertEquals(1, handler1.invocationCount)
        assertEquals(1, handler2.invocationCount)
        assertEquals(1, handler3.invocationCount)
    }

    @Test
    fun `publish with no handlers should complete without error`() = runTest {
        // Given
        val bus = ApplicationEventBus()

        // When/Then - Should not throw
        bus.publish(TestEvent("test"))
    }

    @Test
    fun `publish should only invoke handlers for matching event type`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val testHandler = SpyEventHandler()
        val anotherHandler = AnotherSpyEventHandler()

        bus.register(testHandler)
        bus.register(anotherHandler)

        // When
        bus.publish(TestEvent("test"))

        // Then
        assertEquals(1, testHandler.invocationCount)
        assertEquals(0, anotherHandler.invocationCount)
    }

    // ========== ASYNC HANDLER EXECUTION TESTS ==========

    @Test
    fun `publish should execute handlers asynchronously`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler = DelayedEventHandler(delayMs = 50)
        bus.register(handler)

        val startTime = System.currentTimeMillis()

        // When
        bus.publish(TestEvent("test"))

        // Then
        val duration = System.currentTimeMillis() - startTime
        assertTrue(duration >= 50, "Handler should execute with delay")
        assertEquals(1, handler.invocationCount)
    }

    @Test
    fun `publish should execute multiple handlers in parallel`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler1 = DelayedEventHandler(delayMs = 100)
        val handler2 = DelayedEventHandler(delayMs = 100)
        val handler3 = DelayedEventHandler(delayMs = 100)

        bus.register(handler1)
        bus.register(handler2)
        bus.register(handler3)

        val startTime = System.currentTimeMillis()

        // When
        bus.publish(TestEvent("test"))

        // Then
        val duration = System.currentTimeMillis() - startTime
        // If sequential: 300ms+, if parallel: ~100ms
        assertTrue(duration < 250, "Handlers should execute in parallel, took ${duration}ms")
        assertEquals(1, handler1.invocationCount)
        assertEquals(1, handler2.invocationCount)
        assertEquals(1, handler3.invocationCount)
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    fun `publish should catch handler exceptions and throw EventPublishingException`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val failingHandler = FailingEventHandler()
        bus.register(failingHandler)

        // When/Then
        assertFailsWith<EventPublishingException> {
            bus.publish(TestEvent("test"))
        }
    }

    @Test
    fun `publish should continue with other handlers when one fails`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val successHandler1 = SpyEventHandler()
        val failingHandler = FailingEventHandler()
        val successHandler2 = SpyEventHandler()

        bus.register(successHandler1)
        bus.register(failingHandler)
        bus.register(successHandler2)

        // When
        try {
            bus.publish(TestEvent("test"))
        } catch (e: EventPublishingException) {
            // Expected
        }

        // Then - Both success handlers should have executed
        assertEquals(1, successHandler1.invocationCount)
        assertEquals(1, successHandler2.invocationCount)
    }

    @Test
    fun `publish should collect all handler failures`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val failing1 = FailingEventHandler()
        val failing2 = FailingEventHandler()

        bus.register(failing1)
        bus.register(failing2)

        // When
        val exception = assertFailsWith<EventPublishingException> {
            bus.publish(TestEvent("test"))
        }

        // Then
        assertEquals(2, exception.failures.size)
    }

    // ========== INTERCEPTOR TESTS ==========

    @Test
    fun `beforePublish interceptor should be called before handlers`() = runTest {
        // Given
        val executionOrder = mutableListOf<String>()
        val interceptor = TrackingInterceptor(executionOrder, "interceptor")
        val handler = TrackingEventHandler(executionOrder, "handler")

        val bus = ApplicationEventBus(interceptors = listOf(interceptor))
        bus.register(handler)

        // When
        bus.publish(TestEvent("test"))

        // Then
        assertEquals("interceptor-before", executionOrder[0])
        assertEquals("handler", executionOrder[1])
        assertEquals("interceptor-after", executionOrder[2])
    }

    @Test
    fun `beforePublish can abort event publishing`() = runTest {
        // Given
        val interceptor = AbortingInterceptor()
        val handler = SpyEventHandler()

        val bus = ApplicationEventBus(interceptors = listOf(interceptor))
        bus.register(handler)

        // When
        bus.publish(TestEvent("test"))

        // Then
        assertEquals(0, handler.invocationCount)  // Handler not invoked
    }

    @Test
    fun `afterPublish should receive PublishResult`() = runTest {
        // Given
        val interceptor = CapturingInterceptor()
        val handler = SpyEventHandler()

        val bus = ApplicationEventBus(interceptors = listOf(interceptor))
        bus.register(handler)

        // When
        bus.publish(TestEvent("test"))

        // Then
        assertNotNull(interceptor.lastResult)
        assertEquals(1, interceptor.lastResult?.handlersInvoked)
        assertEquals(1, interceptor.lastResult?.handlersSucceeded)
        assertEquals(0, interceptor.lastResult?.handlersFailed)
    }

    @Test
    fun `afterPublish should report handler failures`() = runTest {
        // Given
        val interceptor = CapturingInterceptor()
        val successHandler = SpyEventHandler()
        val failingHandler = FailingEventHandler()

        val bus = ApplicationEventBus(interceptors = listOf(interceptor))
        bus.register(successHandler)
        bus.register(failingHandler)

        // When
        try {
            bus.publish(TestEvent("test"))
        } catch (e: EventPublishingException) {
            // Expected
        }

        // Then
        assertNotNull(interceptor.lastResult)
        assertEquals(2, interceptor.lastResult?.handlersInvoked)
        assertEquals(1, interceptor.lastResult?.handlersSucceeded)
        assertEquals(1, interceptor.lastResult?.handlersFailed)
    }

    @Test
    fun `onPublishError should be called when handler fails`() = runTest {
        // Given
        val interceptor = CapturingInterceptor()
        val failingHandler = FailingEventHandler()

        val bus = ApplicationEventBus(interceptors = listOf(interceptor))
        bus.register(failingHandler)

        // When
        try {
            bus.publish(TestEvent("test"))
        } catch (e: EventPublishingException) {
            // Expected
        }

        // Then
        assertNotNull(interceptor.lastError)
        assertTrue(interceptor.lastError is EventPublishingException)
    }

    @Test
    fun `multiple interceptors should all be called`() = runTest {
        // Given
        val interceptor1 = CountingInterceptor()
        val interceptor2 = CountingInterceptor()
        val handler = SpyEventHandler()

        val bus = ApplicationEventBus(interceptors = listOf(interceptor1, interceptor2))
        bus.register(handler)

        // When
        bus.publish(TestEvent("test"))

        // Then
        assertEquals(1, interceptor1.beforePublishCount)
        assertEquals(1, interceptor1.afterPublishCount)
        assertEquals(1, interceptor2.beforePublishCount)
        assertEquals(1, interceptor2.afterPublishCount)
    }

    // ========== EVENT FLOW TESTS ==========

    @Test
    fun `events() should provide SharedFlow of all published events`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val collectedEvents = mutableListOf<DomainEvent>()

        // When
        val job = launch {
            bus.events().take(2).toList(collectedEvents)
        }
        advanceUntilIdle()

        // Publish events
        bus.publish(TestEvent("event1"))
        bus.publish(TestEvent("event2"))

        job.join()

        // Then
        assertEquals(2, collectedEvents.size)
    }

    @Test
    fun `eventsOf() should filter events by type`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val collectedEvents = mutableListOf<TestEvent>()

        // When
        val job = launch {
            bus.eventsOf(TestEvent::class).take(1).toList(collectedEvents)
        }
        advanceUntilIdle()

        bus.publish(AnotherTestEvent(42))  // Different type
        bus.publish(TestEvent("event1"))   // Matching type

        job.join()

        // Then
        assertEquals(1, collectedEvents.size)
        assertEquals("event1", collectedEvents[0].data)
    }

    @Test
    fun `event flow should not block publishing`() = runTest {
        // Given
        val bus = ApplicationEventBus()

        // When - Publish without collector
        bus.publish(TestEvent("test"))

        // Then - Should complete without hanging
        assertTrue(true)
    }

    // ========== HANDLER CONFIGURATION TESTS ==========

    @Test
    fun `configureHandlers should set event-specific configuration`() {
        // Given
        val bus = ApplicationEventBus()
        val config = EventHandlerConfig(
            eventType = TestEvent::class.qualifiedName ?: "test.event",
            handlingMode = EventHandlingMode.ASYNC_AFTER_COMMIT,
            timeoutMs = 10000
        )

        // When
        bus.configureHandlers(config)

        // Then
        val retrievedConfig = bus.getHandlerConfig(TestEvent("test"))
        assertEquals(EventHandlingMode.ASYNC_AFTER_COMMIT, retrievedConfig.handlingMode)
        assertEquals(10000, retrievedConfig.timeoutMs)
    }

    @Test
    fun `getHandlerConfig should return default for unconfigured events`() {
        // Given
        val bus = ApplicationEventBus()

        // When
        val config = bus.getHandlerConfig(TestEvent("test"))

        // Then
        assertEquals(EventHandlingMode.SYNC_BEFORE_COMMIT, config.handlingMode)
    }

    @Test
    fun `hasHandlers should return true when handlers registered`() {
        // Given
        val bus = ApplicationEventBus()
        bus.register(SpyEventHandler())

        // When
        val hasHandlers = bus.hasHandlers(TestEvent("test"))

        // Then
        assertTrue(hasHandlers)
    }

    @Test
    fun `hasHandlers should return false when no handlers registered`() {
        // Given
        val bus = ApplicationEventBus()

        // When
        val hasHandlers = bus.hasHandlers(TestEvent("test"))

        // Then
        assertFalse(hasHandlers)
    }

    // ========== CONCURRENT PUBLISHING TESTS ==========

    @Test
    fun `concurrent publishes should not interfere`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler = SpyEventHandler()
        bus.register(handler)

        // When - Publish concurrently
        val job1 = launch { bus.publish(TestEvent("event1")) }
        val job2 = launch { bus.publish(TestEvent("event2")) }
        val job3 = launch { bus.publish(TestEvent("event3")) }

        job1.join()
        job2.join()
        job3.join()

        // Then
        assertEquals(3, handler.invocationCount)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `publish should handle handler with null return`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler = object : EventHandler<TestEvent> {
            override val eventType = TestEvent::class
            override suspend fun handle(event: TestEvent) {
                // Explicit null return
                return
            }
        }
        bus.register(handler)

        // When/Then - Should not throw
        bus.publish(TestEvent("test"))
    }

    @Test
    fun `publish should handle very large number of handlers`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handlers = (1..100).map { SpyEventHandler() }
        handlers.forEach { bus.register(it) }

        // When
        bus.publish(TestEvent("test"))

        // Then
        handlers.forEach { handler ->
            assertEquals(1, handler.invocationCount)
        }
    }

    @Test
    fun `publish should complete within reasonable time`() = runTest {
        // Given
        val bus = ApplicationEventBus()
        val handler = SpyEventHandler()
        bus.register(handler)

        // When
        val start = System.currentTimeMillis()
        bus.publish(TestEvent("test"))
        val duration = System.currentTimeMillis() - start

        // Then
        assertTrue(duration < 5_000, "publish should finish quickly")
    }

    // ========== TEST EVENT CLASSES ==========

    private data class TestEvent(
        val data: String,
        val eventMetadata: EventMetadata = EventMetadata(eventType = "test.event")
    ) : DomainEvent {
        override fun getMetadata() = eventMetadata
    }

    private data class AnotherTestEvent(
        val value: Int,
        val eventMetadata: EventMetadata = EventMetadata(eventType = "another.test.event")
    ) : DomainEvent {
        override fun getMetadata() = eventMetadata
    }

    private sealed class SealedTestEvent : DomainEvent {
        data class Created(val id: String, val data: String) : SealedTestEvent()
        data class Updated(val id: String, val data: String) : SealedTestEvent()
        data class Deleted(val id: String) : SealedTestEvent()
    }

    // ========== TEST HANDLER CLASSES ==========

    private class SpyEventHandler : EventHandler<TestEvent> {
        override val eventType: KClass<TestEvent> = TestEvent::class

        var invocationCount = 0
            private set
        val events = mutableListOf<TestEvent>()

        override suspend fun handle(event: TestEvent) {
            invocationCount++
            events.add(event)
        }
    }

    private class AnotherSpyEventHandler : EventHandler<AnotherTestEvent> {
        override val eventType: KClass<AnotherTestEvent> = AnotherTestEvent::class

        var invocationCount = 0
            private set

        override suspend fun handle(event: AnotherTestEvent) {
            invocationCount++
        }
    }

    private class DelayedEventHandler(private val delayMs: Long) : EventHandler<TestEvent> {
        override val eventType: KClass<TestEvent> = TestEvent::class

        var invocationCount = 0
            private set

        override suspend fun handle(event: TestEvent) {
            delay(delayMs)
            invocationCount++
        }
    }

    private class FailingEventHandler : EventHandler<TestEvent> {
        override val eventType: KClass<TestEvent> = TestEvent::class

        override suspend fun handle(event: TestEvent) {
            throw RuntimeException("Handler intentionally failed")
        }
    }

    private class SealedParentHandler : EventHandler<SealedTestEvent> {
        override val eventType: KClass<SealedTestEvent> = SealedTestEvent::class

        var invocationCount = 0
            private set
        val events = mutableListOf<SealedTestEvent>()

        override suspend fun handle(event: SealedTestEvent) {
            invocationCount++
            events.add(event)
        }
    }

    private class TrackingEventHandler(
        private val tracker: MutableList<String>,
        private val name: String
    ) : EventHandler<TestEvent> {
        override val eventType: KClass<TestEvent> = TestEvent::class

        override suspend fun handle(event: TestEvent) {
            tracker.add(name)
        }
    }

    // ========== TEST INTERCEPTOR CLASSES ==========

    private class TrackingInterceptor(
        private val tracker: MutableList<String>,
        private val name: String
    ) : EventBusInterceptor {
        override suspend fun beforePublish(event: DomainEvent): InterceptResult {
            tracker.add("$name-before")
            return InterceptResult.Continue
        }

        override suspend fun afterPublish(event: DomainEvent, result: PublishResult) {
            tracker.add("$name-after")
        }
    }

    private class AbortingInterceptor : EventBusInterceptor {
        override suspend fun beforePublish(event: DomainEvent): InterceptResult {
            return InterceptResult.Abort("Aborted for testing")
        }
    }

    private class CapturingInterceptor : EventBusInterceptor {
        var lastResult: PublishResult? = null
        var lastError: Throwable? = null

        override suspend fun afterPublish(event: DomainEvent, result: PublishResult) {
            lastResult = result
        }

        override suspend fun onPublishError(event: DomainEvent, error: Throwable) {
            lastError = error
        }
    }

    private class CountingInterceptor : EventBusInterceptor {
        var beforePublishCount = 0
        var afterPublishCount = 0
        var errorCount = 0

        override suspend fun beforePublish(event: DomainEvent): InterceptResult {
            beforePublishCount++
            return InterceptResult.Continue
        }

        override suspend fun afterPublish(event: DomainEvent, result: PublishResult) {
            afterPublishCount++
        }

        override suspend fun onPublishError(event: DomainEvent, error: Throwable) {
            errorCount++
        }
    }
}
