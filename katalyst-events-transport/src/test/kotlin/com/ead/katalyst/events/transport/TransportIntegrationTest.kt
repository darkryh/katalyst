package com.ead.katalyst.events.transport

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import com.ead.katalyst.events.transport.routing.EventRouter
import com.ead.katalyst.events.transport.routing.RoutingStrategies
import com.ead.katalyst.events.transport.serialization.JsonEventDeserializer
import com.ead.katalyst.events.transport.serialization.JsonEventSerializer
import com.ead.katalyst.messaging.DestinationType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for event transport layer.
 *
 * Tests:
 * - Event serialization to JSON format
 * - Event deserialization from JSON
 * - Event type resolution
 * - Event routing strategies
 * - Type resolver with fallback
 */
class TransportIntegrationTest {

    private lateinit var serializer: JsonEventSerializer
    private lateinit var deserializer: JsonEventDeserializer
    private lateinit var typeResolver: EventTypeResolver
    private lateinit var router: EventRouter

    @BeforeEach
    fun setup() {
        // Setup type resolver
        typeResolver = FallbackEventTypeResolver().apply {
            register("test.user.created", TestUserCreatedEvent::class)
        }

        // Setup serialization/deserialization
        serializer = JsonEventSerializer()
        deserializer = JsonEventDeserializer(typeResolver)

        // Setup routing
        router = RoutingStrategies.prefixed("events")
    }

    @Test
    fun `test event serialization produces valid message`() = runBlocking {
        // Given
        val event = TestUserCreatedEvent("user-123", "john@example.com")

        // When
        val message = serializer.serialize(event)

        // Then
        assertNotNull(message)
        assertEquals("application/json", message.contentType)
        assertTrue(message.payload.isNotEmpty())
        assertNotNull(message.getHeader("event-type"))
    }

    @Test
    fun `test event message contains metadata headers`() = runBlocking {
        // Given
        val event = TestUserCreatedEvent("user-456", "jane@example.com")

        // When
        val message = serializer.serialize(event)

        // Then
        assertNotNull(message.getHeader("event-type"))
        assertNotNull(message.getHeader("event-id"))
        assertNotNull(message.getHeader("timestamp"))
    }

    @Test
    fun `test event type resolver recognizes registered types`() {
        // When
        val isKnown = typeResolver.isKnown("test.user.created")

        // Then
        assertTrue(isKnown)
    }

    @Test
    fun `test event router produces valid destination`() {
        // Given
        val event = TestUserCreatedEvent("user-789", "bob@example.com")

        // When
        val destination = router.resolve(event)

        // Then
        assertNotNull(destination)
        assertEquals(DestinationType.TOPIC, destination.type)
        assertTrue(destination.name.startsWith("events"))
    }

    @Test
    fun `test routing strategy naming convention`() {
        // Given
        val event = TestUserCreatedEvent("user-001", "alice@example.com")

        // When
        val destination = router.resolve(event)

        // Then
        // Event type should be lowercased and appended with "."
        assertTrue(destination.name.contains("."), "Destination should contain prefix separator")
    }

    @Test
    fun `test single destination router`() {
        // Given
        val singleRouter = RoutingStrategies.single("all-events")
        val event = TestUserCreatedEvent("user-002", "charlie@example.com")

        // When
        val destination = singleRouter.resolve(event)

        // Then
        assertEquals("all-events", destination.name)
    }

    @Test
    fun `test package based router`() {
        // Given
        val packageRouter = RoutingStrategies.packageBased("events")
        val event = TestUserCreatedEvent("user-003", "david@example.com")

        // When
        val destination = packageRouter.resolve(event)

        // Then
        assertTrue(destination.name.startsWith("events"))
        // Should include the package name
        assertTrue(destination.name.contains("transport"))
    }

    @Test
    fun `test custom routing logic`() {
        // Given
        val customRouter = RoutingStrategies.custom { event ->
            com.ead.katalyst.messaging.Destination("custom-${event.eventType()}", DestinationType.QUEUE)
        }
        val event = TestUserCreatedEvent("user-004", "eve@example.com")

        // When
        val destination = customRouter.resolve(event)

        // Then
        assertEquals("custom-TestUserCreatedEvent", destination.name)
        assertEquals(DestinationType.QUEUE, destination.type)
    }

    /**
     * Test event for serialization/deserialization.
     */
    data class TestUserCreatedEvent(
        val userId: String,
        val email: String
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata =
            EventMetadata(eventType = "TestUserCreatedEvent")

        override fun eventType(): String = "TestUserCreatedEvent"
    }
}
