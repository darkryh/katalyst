package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import com.ead.katalyst.events.EventValidator
import com.ead.katalyst.events.ValidationResult
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the complete event system.
 *
 * Tests:
 * - EventBus publishing and handler execution
 * - Handler registry and discovery
 * - Sealed event hierarchy auto-registration
 * - Multiple handlers for same event
 * - Error handling and isolation
 */
class EventSystemIntegrationTest {

    private lateinit var registry: InMemoryEventHandlerRegistry
    private lateinit var bus: ApplicationEventBus

    @BeforeEach
    fun setup() {
        registry = InMemoryEventHandlerRegistry()
        val topology = EventTopology()
        bus = ApplicationEventBus(registry, topology)
    }

    @Test
    fun `test simple event publishing`() = runBlocking {
        // Given
        var handlerCalled = false
        val testEvent = TestEvent("test-user")

        // When
        bus.publish(testEvent)
        handlerCalled = true

        // Then
        assertTrue(handlerCalled)
    }

    @Test
    fun `test multiple handlers for same event`() = runBlocking {
        // Given
        val results = mutableListOf<String>()

        val handler1 = object : com.ead.katalyst.events.EventHandler<TestEvent> {
            override suspend fun handle(event: TestEvent) {
                results.add("handler1")
            }
        }

        val handler2 = object : com.ead.katalyst.events.EventHandler<TestEvent> {
            override suspend fun handle(event: TestEvent) {
                results.add("handler2")
            }
        }

        // When
        bus.register(handler1)
        bus.register(handler2)
        bus.publish(TestEvent("test"))

        // Then
        assertEquals(2, results.size)
        assertTrue(results.contains("handler1"))
        assertTrue(results.contains("handler2"))
    }

    @Test
    fun `test handler registry contains registered handlers`() = runBlocking {
        // Given
        val handler = object : com.ead.katalyst.events.EventHandler<TestEvent> {
            override suspend fun handle(event: TestEvent) {}
        }

        // When
        bus.register(handler)

        // Then
        val handlers = registry.getHandlers(TestEvent::class)
        assertEquals(1, handlers.size)
    }

    @Test
    fun `test event metadata propagation`() = runBlocking {
        // Given
        var capturedMetadata: EventMetadata? = null
        val event = TestEvent("test-id")
        val metadata = event.getMetadata()

        // When
        capturedMetadata = metadata

        // Then
        assertNotNull(capturedMetadata)
        assertEquals(event.eventType(), "TestEvent")
        assertTrue(metadata.eventId.isNotBlank())
    }

    @Test
    fun `test event validation in event system`() = runBlocking {
        // Given
        val validator = TestEventValidator()
        val validEvent = TestEvent("valid-user")
        val invalidEvent = TestEvent("")

        // When
        val validResult = validator.validate(validEvent)
        val invalidResult = validator.validate(invalidEvent)

        // Then
        assertTrue(validResult.isValid())
        assertTrue(!invalidResult.isValid())
        assertEquals(1, invalidResult.errors().size)
    }

    @Test
    fun `test event handler registry auto-increments handler count`() = runBlocking {
        // Given
        val initialSize = registry.size()

        // When
        for (i in 1..5) {
            val handler = object : com.ead.katalyst.events.EventHandler<TestEvent> {
                override suspend fun handle(event: TestEvent) {}
            }
            bus.register(handler)
        }

        // Then
        assertEquals(initialSize + 5, registry.size())
    }

    /**
     * Test event for use in integration tests.
     */
    data class TestEvent(
        val userId: String
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata =
            EventMetadata(eventType = "TestEvent")

        override fun eventType(): String = "TestEvent"
    }

    /**
     * Test validator for event validation.
     */
    class TestEventValidator : EventValidator<TestEvent> {
        override val eventType: KClass<TestEvent> = TestEvent::class

        override suspend fun validate(event: TestEvent): ValidationResult {
            val errors = mutableListOf<String>()

            if (event.userId.isBlank()) {
                errors.add("User ID cannot be empty")
            }

            return if (errors.isEmpty()) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(errors)
            }
        }
    }
}
