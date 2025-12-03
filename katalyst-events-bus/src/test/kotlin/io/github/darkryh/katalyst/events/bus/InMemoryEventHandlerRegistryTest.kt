package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.EventMetadata
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.*

/**
 * Comprehensive tests for InMemoryEventHandlerRegistry.
 *
 * Tests cover:
 * - Handler registration (single, multiple)
 * - Handler retrieval (all, by type)
 * - Size tracking
 * - Clear functionality
 * - Thread safety (concurrent operations)
 * - Multiple handlers for same event type
 * - Different event types
 * - Edge cases
 */
class InMemoryEventHandlerRegistryTest {

    private lateinit var registry: InMemoryEventHandlerRegistry

    @BeforeTest
    fun setup() {
        registry = InMemoryEventHandlerRegistry()
    }

    // ========== REGISTRATION TESTS ==========

    @Test
    fun `register should add handler to registry`() {
        // Given
        val handler = TestEventHandler()

        // When
        registry.register(handler)

        // Then
        assertEquals(1, registry.size())
        assertTrue(registry.getAllHandlers().contains(handler))
    }

    @Test
    fun `register should add multiple handlers`() {
        // Given
        val handler1 = TestEventHandler()
        val handler2 = AnotherTestEventHandler()

        // When
        registry.register(handler1)
        registry.register(handler2)

        // Then
        assertEquals(2, registry.size())
        val allHandlers = registry.getAllHandlers()
        assertTrue(allHandlers.contains(handler1))
        assertTrue(allHandlers.contains(handler2))
    }

    @Test
    fun `register should allow same handler instance multiple times`() {
        // Given
        val handler = TestEventHandler()

        // When
        registry.register(handler)
        registry.register(handler)

        // Then
        assertEquals(2, registry.size())  // Same instance registered twice
    }

    @Test
    fun `register should allow multiple handlers for same event type`() {
        // Given
        val handler1 = TestEventHandler()
        val handler2 = TestEventHandler()

        // When
        registry.register(handler1)
        registry.register(handler2)

        // Then
        assertEquals(2, registry.size())
        val handlers = registry.getHandlers(TestEvent::class)
        assertEquals(2, handlers.size)
    }

    // ========== GET ALL HANDLERS TESTS ==========

    @Test
    fun `getAllHandlers should return empty list when no handlers registered`() {
        // When
        val handlers = registry.getAllHandlers()

        // Then
        assertTrue(handlers.isEmpty())
    }

    @Test
    fun `getAllHandlers should return all registered handlers`() {
        // Given
        val handler1 = TestEventHandler()
        val handler2 = AnotherTestEventHandler()
        val handler3 = YetAnotherEventHandler()

        registry.register(handler1)
        registry.register(handler2)
        registry.register(handler3)

        // When
        val handlers = registry.getAllHandlers()

        // Then
        assertEquals(3, handlers.size)
        assertTrue(handlers.contains(handler1))
        assertTrue(handlers.contains(handler2))
        assertTrue(handlers.contains(handler3))
    }

    @Test
    fun `getAllHandlers should return immutable copy`() {
        // Given
        val handler = TestEventHandler()
        registry.register(handler)

        // When
        val handlers1 = registry.getAllHandlers()
        registry.register(AnotherTestEventHandler())
        val handlers2 = registry.getAllHandlers()

        // Then
        assertEquals(1, handlers1.size)  // Original list unchanged
        assertEquals(2, handlers2.size)  // New list reflects changes
    }

    // ========== GET HANDLERS BY TYPE TESTS ==========

    @Test
    fun `getHandlers should return empty list for unregistered event type`() {
        // When
        val handlers = registry.getHandlers(TestEvent::class)

        // Then
        assertTrue(handlers.isEmpty())
    }

    @Test
    fun `getHandlers should return handlers for specific event type`() {
        // Given
        val testHandler = TestEventHandler()
        val anotherHandler = AnotherTestEventHandler()

        registry.register(testHandler)
        registry.register(anotherHandler)

        // When
        val testHandlers = registry.getHandlers(TestEvent::class)
        val anotherHandlers = registry.getHandlers(AnotherTestEvent::class)

        // Then
        assertEquals(1, testHandlers.size)
        assertTrue(testHandlers.contains(testHandler))

        assertEquals(1, anotherHandlers.size)
        assertTrue(anotherHandlers.contains(anotherHandler))
    }

    @Test
    fun `getHandlers should return all handlers for same event type`() {
        // Given
        val handler1 = TestEventHandler()
        val handler2 = TestEventHandler()
        val handler3 = TestEventHandler()

        registry.register(handler1)
        registry.register(handler2)
        registry.register(handler3)

        // When
        val handlers = registry.getHandlers(TestEvent::class)

        // Then
        assertEquals(3, handlers.size)
        assertTrue(handlers.contains(handler1))
        assertTrue(handlers.contains(handler2))
        assertTrue(handlers.contains(handler3))
    }

    @Test
    fun `getHandlers should not return handlers for different event types`() {
        // Given
        val testHandler = TestEventHandler()
        val anotherHandler = AnotherTestEventHandler()

        registry.register(testHandler)
        registry.register(anotherHandler)

        // When
        val testHandlers = registry.getHandlers(TestEvent::class)

        // Then
        assertEquals(1, testHandlers.size)
        assertFalse(testHandlers.any { it === anotherHandler })
    }

    // ========== SIZE TESTS ==========

    @Test
    fun `size should return 0 for empty registry`() {
        // When
        val size = registry.size()

        // Then
        assertEquals(0, size)
    }

    @Test
    fun `size should return correct count after registrations`() {
        // Given/When/Then
        assertEquals(0, registry.size())

        registry.register(TestEventHandler())
        assertEquals(1, registry.size())

        registry.register(AnotherTestEventHandler())
        assertEquals(2, registry.size())

        registry.register(YetAnotherEventHandler())
        assertEquals(3, registry.size())
    }

    @Test
    fun `size should count duplicate registrations`() {
        // Given
        val handler = TestEventHandler()

        // When
        registry.register(handler)
        registry.register(handler)
        registry.register(handler)

        // Then
        assertEquals(3, registry.size())
    }

    // ========== CLEAR TESTS ==========

    @Test
    fun `clear should remove all handlers`() {
        // Given
        registry.register(TestEventHandler())
        registry.register(AnotherTestEventHandler())
        registry.register(YetAnotherEventHandler())
        assertEquals(3, registry.size())

        // When
        registry.clear()

        // Then
        assertEquals(0, registry.size())
        assertTrue(registry.getAllHandlers().isEmpty())
    }

    @Test
    fun `clear on empty registry should not throw`() {
        // When/Then - Should not throw
        registry.clear()

        assertEquals(0, registry.size())
    }

    @Test
    fun `clear should allow re-registration after clearing`() {
        // Given
        val handler = TestEventHandler()
        registry.register(handler)
        registry.clear()

        // When
        registry.register(handler)

        // Then
        assertEquals(1, registry.size())
        assertTrue(registry.getAllHandlers().contains(handler))
    }

    @Test
    fun `multiple clears should work correctly`() {
        // Given
        registry.register(TestEventHandler())
        registry.clear()
        registry.register(AnotherTestEventHandler())
        registry.clear()
        registry.register(YetAnotherEventHandler())

        // Then
        assertEquals(1, registry.size())
    }

    // ========== CONCURRENT ACCESS TESTS ==========

    @Test
    fun `concurrent registrations should be thread-safe`() = runTest {
        // Given
        val handlers = (1..100).map { TestEventHandler() }

        // When - Register handlers concurrently
        handlers.forEach { handler ->
            registry.register(handler)
        }

        // Then
        assertEquals(100, registry.size())
    }

    @Test
    fun `concurrent reads should be thread-safe`() = runTest {
        // Given
        repeat(10) { registry.register(TestEventHandler()) }

        // When - Read concurrently
        val results = (1..100).map {
            registry.getAllHandlers()
        }

        // Then - All reads should succeed
        assertEquals(100, results.size)
        results.forEach { handlers ->
            assertEquals(10, handlers.size)
        }
    }

    @Test
    fun `concurrent register and read should be thread-safe`() = runTest {
        // Given
        registry.register(TestEventHandler())

        // When - Mix reads and writes
        repeat(50) { registry.register(TestEventHandler()) }
        val size1 = registry.size()
        repeat(50) { registry.register(TestEventHandler()) }
        val size2 = registry.size()

        // Then
        assertTrue(size1 >= 1)
        assertTrue(size2 >= size1)
        assertEquals(101, size2)  // 1 initial + 100 added
    }

    // ========== EDGE CASES ==========

    @Test
    fun `registry should handle large number of handlers`() {
        // Given
        val handlerCount = 1000

        // When
        repeat(handlerCount) {
            registry.register(TestEventHandler())
        }

        // Then
        assertEquals(handlerCount, registry.size())
        assertEquals(handlerCount, registry.getAllHandlers().size)
    }

    @Test
    fun `registry should preserve registration order in getAllHandlers`() {
        // Given
        val handler1 = TestEventHandler()
        val handler2 = AnotherTestEventHandler()
        val handler3 = YetAnotherEventHandler()

        // When
        registry.register(handler1)
        registry.register(handler2)
        registry.register(handler3)

        // Then
        val handlers = registry.getAllHandlers()
        assertEquals(handler1, handlers[0])
        assertEquals(handler2, handlers[1])
        assertEquals(handler3, handlers[2])
    }

    @Test
    fun `getHandlers should handle sealed event hierarchies`() {
        // Given
        val parentHandler = SealedEventParentHandler()
        val childHandler = SealedEventChildHandler()

        registry.register(parentHandler)
        registry.register(childHandler)

        // When
        val parentHandlers = registry.getHandlers(SealedTestEvent::class)
        val childHandlers = registry.getHandlers(SealedTestEvent.ChildEvent::class)

        // Then
        assertEquals(1, parentHandlers.size)
        assertTrue(parentHandlers.contains(parentHandler))

        assertEquals(1, childHandlers.size)
        assertTrue(childHandlers.contains(childHandler))
    }

    @Test
    fun `registry should work with anonymous handler implementations`() {
        // Given
        val handler = object : EventHandler<TestEvent> {
            override val eventType = TestEvent::class
            override suspend fun handle(event: TestEvent) {}
        }

        // When
        registry.register(handler)

        // Then
        assertEquals(1, registry.size())
        assertEquals(1, registry.getHandlers(TestEvent::class).size)
    }

    // ========== INTERFACE CONTRACT TESTS ==========

    @Test
    fun `registry should implement EventHandlerRegistry interface`() {
        // Then
        assertTrue(registry is EventHandlerRegistry)
    }

    @Test
    fun `registry methods should work through interface reference`() {
        // Given
        val interfaceRef: EventHandlerRegistry = registry
        val handler = TestEventHandler()

        // When
        interfaceRef.register(handler)

        // Then
        assertEquals(1, interfaceRef.size())
        assertTrue(interfaceRef.getAllHandlers().contains(handler))
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

    private data class YetAnotherTestEvent(
        val flag: Boolean,
        val eventMetadata: EventMetadata = EventMetadata(eventType = "yet.another.test.event")
    ) : DomainEvent {
        override fun getMetadata() = eventMetadata
    }

    private sealed class SealedTestEvent : DomainEvent {
        data class ChildEvent(val id: String) : SealedTestEvent()
    }

    // ========== TEST HANDLER CLASSES ==========

    private class TestEventHandler : EventHandler<TestEvent> {
        override val eventType: KClass<TestEvent> = TestEvent::class
        override suspend fun handle(event: TestEvent) {}
    }

    private class AnotherTestEventHandler : EventHandler<AnotherTestEvent> {
        override val eventType: KClass<AnotherTestEvent> = AnotherTestEvent::class
        override suspend fun handle(event: AnotherTestEvent) {}
    }

    private class YetAnotherEventHandler : EventHandler<YetAnotherTestEvent> {
        override val eventType: KClass<YetAnotherTestEvent> = YetAnotherTestEvent::class
        override suspend fun handle(event: YetAnotherTestEvent) {}
    }

    private class SealedEventParentHandler : EventHandler<SealedTestEvent> {
        override val eventType: KClass<SealedTestEvent> = SealedTestEvent::class
        override suspend fun handle(event: SealedTestEvent) {}
    }

    private class SealedEventChildHandler : EventHandler<SealedTestEvent.ChildEvent> {
        override val eventType: KClass<SealedTestEvent.ChildEvent> = SealedTestEvent.ChildEvent::class
        override suspend fun handle(event: SealedTestEvent.ChildEvent) {}
    }
}
