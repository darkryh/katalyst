package com.ead.katalyst.events

import kotlin.test.*

/**
 * Comprehensive tests for DomainEvent interface.
 *
 * Tests cover:
 * - Default eventId generation
 * - Default getMetadata() implementation
 * - eventType() behavior
 * - Custom event implementations
 * - Sealed event hierarchies
 * - Edge cases and validation
 */
class DomainEventTest {

    // ========== DEFAULT IMPLEMENTATION TESTS ==========

    @Test
    fun `default eventId should generate unique IDs`() {
        // Given
        val event1 = SimpleTestEvent("test1")
        val event2 = SimpleTestEvent("test2")

        // When
        val id1 = event1.eventId
        val id2 = event2.eventId

        // Then
        assertNotNull(id1)
        assertNotNull(id2)
        assertNotEquals(id1, id2)  // Each instance should have unique ID
    }

    @Test
    fun `default getMetadata should use class simpleName as eventType`() {
        // Given
        val event = SimpleTestEvent("test")

        // When
        val metadata = event.getMetadata()

        // Then
        assertEquals("SimpleTestEvent", metadata.eventType)
    }

    @Test
    fun `eventType should return metadata eventType`() {
        // Given
        val event = EventWithCustomMetadata()

        // When
        val eventType = event.eventType()

        // Then
        assertEquals("custom.event.type", eventType)
    }

    @Test
    fun `custom eventId should be used when provided`() {
        // Given
        val customId = "custom-event-id-123"
        val event = EventWithCustomId(customId)

        // When
        val eventId = event.eventId

        // Then
        assertEquals(customId, eventId)
    }

    // ========== CUSTOM EVENT IMPLEMENTATION TESTS ==========

    @Test
    fun `event with explicit metadata should use it`() {
        // Given
        val metadata = EventMetadata(
            eventType = "user.created",
            version = 2,
            correlationId = "corr-123"
        )
        val event = UserCreatedEvent(
            userId = "user-123",
            email = "user@example.com",
            eventMetadata = metadata
        )

        // When
        val retrievedMetadata = event.getMetadata()

        // Then
        assertEquals(metadata, retrievedMetadata)
        assertEquals("user.created", retrievedMetadata.eventType)
        assertEquals(2, retrievedMetadata.version)
        assertEquals("corr-123", retrievedMetadata.correlationId)
    }

    @Test
    fun `event should preserve domain data`() {
        // Given
        val userId = "user-456"
        val email = "test@example.com"

        // When
        val event = UserCreatedEvent(
            userId = userId,
            email = email
        )

        // Then
        assertEquals(userId, event.userId)
        assertEquals(email, event.email)
    }

    @Test
    fun `data class events should support equality`() {
        // Given
        val metadata = EventMetadata(eventId = "same-id", eventType = "test")
        val event1 = UserCreatedEvent("user-1", "email@test.com", metadata)
        val event2 = UserCreatedEvent("user-1", "email@test.com", metadata)
        val event3 = UserCreatedEvent("user-2", "email@test.com", metadata)

        // Then
        assertEquals(event1, event2)
        assertNotEquals(event1, event3)
    }

    // ========== SEALED EVENT HIERARCHY TESTS ==========

    @Test
    fun `sealed event hierarchy should support polymorphism`() {
        // Given
        val createdEvent: UserEvent = UserEvent.Created("user-123", "user@test.com")
        val deletedEvent: UserEvent = UserEvent.Deleted("user-456", "Inactive")
        val updatedEvent: UserEvent = UserEvent.Updated("user-789", "New Name")

        // When/Then - All are UserEvent type
        assertTrue(createdEvent is UserEvent)
        assertTrue(deletedEvent is UserEvent)
        assertTrue(updatedEvent is UserEvent)

        // When/Then - Each has specific type
        assertTrue(createdEvent is UserEvent.Created)
        assertTrue(deletedEvent is UserEvent.Deleted)
        assertTrue(updatedEvent is UserEvent.Updated)
    }

    @Test
    fun `sealed events should have distinct eventTypes`() {
        // Given
        val created = UserEvent.Created("user-1", "email@test.com")
        val deleted = UserEvent.Deleted("user-2", "reason")
        val updated = UserEvent.Updated("user-3", "new name")

        // Then
        assertEquals("Created", created.eventType())
        assertEquals("Deleted", deleted.eventType())
        assertEquals("Updated", updated.eventType())
    }

    @Test
    fun `sealed events should support when expressions`() {
        // Given
        val events: List<UserEvent> = listOf(
            UserEvent.Created("user-1", "email1@test.com"),
            UserEvent.Deleted("user-2", "inactive"),
            UserEvent.Updated("user-3", "New Name")
        )

        // When
        val results = events.map { event ->
            when (event) {
                is UserEvent.Created -> "Created: ${event.userId}"
                is UserEvent.Deleted -> "Deleted: ${event.userId}"
                is UserEvent.Updated -> "Updated: ${event.userId}"
            }
        }

        // Then
        assertEquals("Created: user-1", results[0])
        assertEquals("Deleted: user-2", results[1])
        assertEquals("Updated: user-3", results[2])
    }

    // ========== EVENT ID TESTS ==========

    @Test
    fun `events with same data should have different eventIds by default`() {
        // Given
        val event1 = SimpleTestEvent("same-data")
        val event2 = SimpleTestEvent("same-data")

        // Then
        assertNotEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `custom eventId should support deduplication`() {
        // Given - Same event ID for deduplication
        val eventId = "dedup-id-123"
        val event1 = EventWithCustomId(eventId)
        val event2 = EventWithCustomId(eventId)

        // Then
        assertEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `eventId should be accessible from interface`() {
        // Given
        val event: DomainEvent = SimpleTestEvent("test")

        // When
        val eventId = event.eventId

        // Then
        assertNotNull(eventId)
    }

    // ========== METADATA TESTS ==========

    @Test
    fun `getMetadata should be callable from interface`() {
        // Given
        val event: DomainEvent = SimpleTestEvent("test")

        // When
        val metadata = event.getMetadata()

        // Then
        assertNotNull(metadata)
        assertNotNull(metadata.eventType)
    }

    @Test
    fun `events should support custom metadata`() {
        // Given
        val customMetadata = EventMetadata(
            eventType = "custom.type",
            version = 5,
            correlationId = "corr-999",
            source = "test-service"
        )
        val event = EventWithCustomMetadata(customMetadata)

        // When
        val metadata = event.getMetadata()

        // Then
        assertEquals("custom.type", metadata.eventType)
        assertEquals(5, metadata.version)
        assertEquals("corr-999", metadata.correlationId)
        assertEquals("test-service", metadata.source)
    }

    // ========== EVENT TYPE TESTS ==========

    @Test
    fun `eventType should return consistent value`() {
        // Given
        val event = SimpleTestEvent("test")

        // When
        val type1 = event.eventType()
        val type2 = event.eventType()

        // Then
        assertEquals(type1, type2)
    }

    @Test
    fun `eventType should work with custom metadata`() {
        // Given
        val event = UserCreatedEvent(
            userId = "user-1",
            email = "user@test.com",
            eventMetadata = EventMetadata(eventType = "user.registered")
        )

        // When
        val eventType = event.eventType()

        // Then
        assertEquals("user.registered", eventType)
    }

    // ========== IMMUTABILITY TESTS ==========

    @Test
    fun `data class events should be immutable`() {
        // Given
        val original = UserCreatedEvent("user-1", "original@test.com")

        // When - Try to create modified version
        val modified = original.copy(email = "modified@test.com")

        // Then - Original unchanged
        assertEquals("original@test.com", original.email)
        assertEquals("modified@test.com", modified.email)
    }

    @Test
    fun `metadata should remain consistent per event instance`() {
        // Given
        val event = SimpleTestEvent("test")

        // When
        val metadata1 = event.getMetadata()
        val metadata2 = event.getMetadata()

        // Then - Should return same metadata object or equivalent
        assertEquals(metadata1.eventType, metadata2.eventType)
        assertEquals(metadata1.timestamp, metadata2.timestamp)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `event with null class name should handle gracefully`() {
        // Given - Anonymous object with no simple name
        val event = object : DomainEvent {
            override fun getMetadata(): EventMetadata {
                return EventMetadata(eventType = this::class.simpleName ?: "UnknownEvent")
            }
        }

        // When
        val metadata = event.getMetadata()

        // Then - Should fall back to "UnknownEvent" or handle null
        assertNotNull(metadata.eventType)
    }

    @Test
    fun `event with empty payload should be valid`() {
        // Given
        val event = EmptyEvent()

        // When
        val eventType = event.eventType()
        val eventId = event.eventId

        // Then
        assertNotNull(eventType)
        assertNotNull(eventId)
    }

    @Test
    fun `event with complex nested data should work`() {
        // Given
        val complexEvent = ComplexEvent(
            data = mapOf(
                "user" to mapOf("id" to "123", "name" to "Alice"),
                "items" to listOf("item1", "item2", "item3")
            )
        )

        // When
        val eventId = complexEvent.eventId
        val eventType = complexEvent.eventType()

        // Then
        assertNotNull(eventId)
        assertEquals("ComplexEvent", eventType)
    }

    // ========== TEST EVENT IMPLEMENTATIONS ==========

    private data class SimpleTestEvent(
        val data: String
    ) : DomainEvent

    private data class UserCreatedEvent(
        val userId: String,
        val email: String,
        val eventMetadata: EventMetadata = EventMetadata(eventType = "user.created")
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata = eventMetadata
    }

    private data class EventWithCustomId(
        private val customEventId: String
    ) : DomainEvent {
        override val eventId: String get() = customEventId
    }

    private data class EventWithCustomMetadata(
        private val customMetadata: EventMetadata = EventMetadata(eventType = "custom.event.type")
    ) : DomainEvent {
        override fun getMetadata(): EventMetadata = customMetadata
    }

    private sealed class UserEvent : DomainEvent {
        data class Created(val userId: String, val email: String) : UserEvent()
        data class Deleted(val userId: String, val reason: String) : UserEvent()
        data class Updated(val userId: String, val newName: String) : UserEvent()
    }

    private class EmptyEvent : DomainEvent

    private data class ComplexEvent(
        val data: Map<String, Any>
    ) : DomainEvent
}
