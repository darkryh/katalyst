package io.github.darkryh.katalyst.events

import kotlin.test.*

/**
 * Comprehensive tests for EventMetadata.
 *
 * Tests cover:
 * - Creation with required and optional fields
 * - Default values (eventId, timestamp, version)
 * - Immutable data class behavior
 * - Builder methods (withCorrelationId, withCausationId, etc.)
 * - Factory methods (of())
 * - Tracing and correlation functionality
 * - Edge cases and validation
 */
class EventMetadataTest {

    // ========== CREATION TESTS ==========

    @Test
    fun `should create metadata with required field only`() {
        // When
        val metadata = EventMetadata(eventType = "user.created")

        // Then
        assertNotNull(metadata.eventId)
        assertEquals("user.created", metadata.eventType)
        assertTrue(metadata.timestamp > 0)
        assertEquals(1, metadata.version)
        assertNull(metadata.correlationId)
        assertNull(metadata.causationId)
        assertNull(metadata.source)
        assertNull(metadata.occurredAt)
    }

    @Test
    fun `should create metadata with all fields specified`() {
        // Given
        val eventId = "evt-123"
        val eventType = "order.placed"
        val timestamp = 1234567890L
        val version = 2
        val correlationId = "corr-456"
        val causationId = "cause-789"
        val source = "order-service"
        val occurredAt = 1234567800L

        // When
        val metadata = EventMetadata(
            eventId = eventId,
            eventType = eventType,
            timestamp = timestamp,
            version = version,
            correlationId = correlationId,
            causationId = causationId,
            source = source,
            occurredAt = occurredAt
        )

        // Then
        assertEquals(eventId, metadata.eventId)
        assertEquals(eventType, metadata.eventType)
        assertEquals(timestamp, metadata.timestamp)
        assertEquals(version, metadata.version)
        assertEquals(correlationId, metadata.correlationId)
        assertEquals(causationId, metadata.causationId)
        assertEquals(source, metadata.source)
        assertEquals(occurredAt, metadata.occurredAt)
    }

    // ========== DEFAULT VALUES TESTS ==========

    @Test
    fun `should generate UUID for eventId by default`() {
        // When
        val metadata1 = EventMetadata(eventType = "test.event")
        val metadata2 = EventMetadata(eventType = "test.event")

        // Then
        assertNotNull(metadata1.eventId)
        assertNotNull(metadata2.eventId)
        assertNotEquals(metadata1.eventId, metadata2.eventId)  // UUIDs should be unique
    }

    @Test
    fun `should use current timestamp by default`() {
        // Given
        val before = System.currentTimeMillis()

        // When
        val metadata = EventMetadata(eventType = "test.event")

        // Then
        val after = System.currentTimeMillis()
        assertTrue(metadata.timestamp >= before)
        assertTrue(metadata.timestamp <= after)
    }

    @Test
    fun `should default version to 1`() {
        // When
        val metadata = EventMetadata(eventType = "test.event")

        // Then
        assertEquals(1, metadata.version)
    }

    @Test
    fun `should default optional fields to null`() {
        // When
        val metadata = EventMetadata(eventType = "test.event")

        // Then
        assertNull(metadata.correlationId)
        assertNull(metadata.causationId)
        assertNull(metadata.source)
        assertNull(metadata.occurredAt)
    }

    // ========== BUILDER METHOD TESTS ==========

    @Test
    fun `withCorrelationId should create new instance with correlationId`() {
        // Given
        val original = EventMetadata(eventType = "test.event")
        val correlationId = "corr-123"

        // When
        val modified = original.withCorrelationId(correlationId)

        // Then
        assertEquals(correlationId, modified.correlationId)
        assertEquals(original.eventId, modified.eventId)
        assertEquals(original.eventType, modified.eventType)
        assertNull(original.correlationId)  // Original unchanged
    }

    @Test
    fun `withCorrelationId should replace existing correlationId`() {
        // Given
        val original = EventMetadata(
            eventType = "test.event",
            correlationId = "old-corr"
        )
        val newCorrelationId = "new-corr"

        // When
        val modified = original.withCorrelationId(newCorrelationId)

        // Then
        assertEquals(newCorrelationId, modified.correlationId)
        assertEquals("old-corr", original.correlationId)  // Original unchanged
    }

    @Test
    fun `withCausationId should create new instance with causationId`() {
        // Given
        val original = EventMetadata(eventType = "test.event")
        val causationId = "cause-456"

        // When
        val modified = original.withCausationId(causationId)

        // Then
        assertEquals(causationId, modified.causationId)
        assertEquals(original.eventId, modified.eventId)
        assertEquals(original.eventType, modified.eventType)
        assertNull(original.causationId)  // Original unchanged
    }

    @Test
    fun `withCausationId should replace existing causationId`() {
        // Given
        val original = EventMetadata(
            eventType = "test.event",
            causationId = "old-cause"
        )
        val newCausationId = "new-cause"

        // When
        val modified = original.withCausationId(newCausationId)

        // Then
        assertEquals(newCausationId, modified.causationId)
        assertEquals("old-cause", original.causationId)  // Original unchanged
    }

    @Test
    fun `withSource should create new instance with source`() {
        // Given
        val original = EventMetadata(eventType = "test.event")
        val source = "user-service"

        // When
        val modified = original.withSource(source)

        // Then
        assertEquals(source, modified.source)
        assertEquals(original.eventId, modified.eventId)
        assertEquals(original.eventType, modified.eventType)
        assertNull(original.source)  // Original unchanged
    }

    @Test
    fun `withSource should replace existing source`() {
        // Given
        val original = EventMetadata(
            eventType = "test.event",
            source = "old-service"
        )
        val newSource = "new-service"

        // When
        val modified = original.withSource(newSource)

        // Then
        assertEquals(newSource, modified.source)
        assertEquals("old-service", original.source)  // Original unchanged
    }

    @Test
    fun `withOccurredAt should create new instance with occurredAt`() {
        // Given
        val original = EventMetadata(eventType = "test.event")
        val occurredAt = 1234567890L

        // When
        val modified = original.withOccurredAt(occurredAt)

        // Then
        assertEquals(occurredAt, modified.occurredAt)
        assertEquals(original.eventId, modified.eventId)
        assertEquals(original.eventType, modified.eventType)
        assertNull(original.occurredAt)  // Original unchanged
    }

    @Test
    fun `withOccurredAt should replace existing occurredAt`() {
        // Given
        val original = EventMetadata(
            eventType = "test.event",
            occurredAt = 1000L
        )
        val newOccurredAt = 2000L

        // When
        val modified = original.withOccurredAt(newOccurredAt)

        // Then
        assertEquals(newOccurredAt, modified.occurredAt)
        assertEquals(1000L, original.occurredAt)  // Original unchanged
    }

    @Test
    fun `should chain builder methods`() {
        // Given
        val original = EventMetadata(eventType = "test.event")

        // When
        val modified = original
            .withCorrelationId("corr-123")
            .withCausationId("cause-456")
            .withSource("my-service")
            .withOccurredAt(1234567890L)

        // Then
        assertEquals("corr-123", modified.correlationId)
        assertEquals("cause-456", modified.causationId)
        assertEquals("my-service", modified.source)
        assertEquals(1234567890L, modified.occurredAt)

        // Original unchanged
        assertNull(original.correlationId)
        assertNull(original.causationId)
        assertNull(original.source)
        assertNull(original.occurredAt)
    }

    // ========== FACTORY METHOD TESTS ==========

    @Test
    fun `of(eventType) should create minimal metadata`() {
        // When
        val metadata = EventMetadata.of("user.created")

        // Then
        assertEquals("user.created", metadata.eventType)
        assertNotNull(metadata.eventId)
        assertTrue(metadata.timestamp > 0)
        assertEquals(1, metadata.version)
        assertNull(metadata.correlationId)
    }

    @Test
    fun `of(eventType, correlationId) should create metadata with correlation`() {
        // When
        val metadata = EventMetadata.of("user.created", "corr-123")

        // Then
        assertEquals("user.created", metadata.eventType)
        assertEquals("corr-123", metadata.correlationId)
        assertNotNull(metadata.eventId)
        assertTrue(metadata.timestamp > 0)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `should support data class copy`() {
        // Given
        val original = EventMetadata(
            eventType = "test.event",
            version = 1,
            correlationId = "corr-123"
        )

        // When
        val copy = original.copy(version = 2)

        // Then
        assertEquals(2, copy.version)
        assertEquals("test.event", copy.eventType)
        assertEquals("corr-123", copy.correlationId)
        assertEquals(1, original.version)  // Original unchanged
    }

    @Test
    fun `should support equality comparison`() {
        // Given
        val metadata1 = EventMetadata(
            eventId = "evt-123",
            eventType = "test.event",
            timestamp = 1000L
        )

        val metadata2 = EventMetadata(
            eventId = "evt-123",
            eventType = "test.event",
            timestamp = 1000L
        )

        val metadata3 = EventMetadata(
            eventId = "evt-456",
            eventType = "test.event",
            timestamp = 1000L
        )

        // Then
        assertEquals(metadata1, metadata2)
        assertNotEquals(metadata1, metadata3)
    }

    @Test
    fun `should generate consistent hashCode for equal objects`() {
        // Given
        val metadata1 = EventMetadata(
            eventId = "evt-123",
            eventType = "test.event",
            timestamp = 1000L
        )

        val metadata2 = EventMetadata(
            eventId = "evt-123",
            eventType = "test.event",
            timestamp = 1000L
        )

        // Then
        assertEquals(metadata1.hashCode(), metadata2.hashCode())
    }

    @Test
    fun `should generate readable toString`() {
        // Given
        val metadata = EventMetadata(
            eventId = "evt-123",
            eventType = "user.created",
            correlationId = "corr-456"
        )

        // When
        val string = metadata.toString()

        // Then
        assertTrue(string.contains("evt-123"))
        assertTrue(string.contains("user.created"))
        assertTrue(string.contains("corr-456"))
    }

    // ========== TRACING AND CORRELATION TESTS ==========

    @Test
    fun `should support distributed tracing with correlationId`() {
        // Given - Request comes in with correlation ID
        val requestCorrelationId = "req-12345"

        // When - Create events for this request
        val event1 = EventMetadata.of("user.created", requestCorrelationId)
        val event2 = EventMetadata.of("email.sent", requestCorrelationId)

        // Then - Both events have same correlation ID for tracing
        assertEquals(requestCorrelationId, event1.correlationId)
        assertEquals(requestCorrelationId, event2.correlationId)
    }

    @Test
    fun `should support causality tracking with causationId`() {
        // Given - Command that caused the event
        val commandId = "cmd-create-user-789"

        // When - Create event caused by command
        val event = EventMetadata(eventType = "user.created")
            .withCausationId(commandId)

        // Then
        assertEquals(commandId, event.causationId)
    }

    @Test
    fun `should support source tracking`() {
        // Given
        val serviceName = "user-service"

        // When
        val event = EventMetadata(eventType = "user.created")
            .withSource(serviceName)

        // Then
        assertEquals(serviceName, event.source)
    }

    @Test
    fun `should distinguish between timestamp and occurredAt`() {
        // Given
        val actualOccurrenceTime = System.currentTimeMillis() - 10000  // 10 seconds ago

        // When - Event occurred in the past but is published now
        val metadata = EventMetadata(eventType = "test.event")
            .withOccurredAt(actualOccurrenceTime)

        // Then
        assertTrue(metadata.timestamp > actualOccurrenceTime)  // Published now
        assertEquals(actualOccurrenceTime, metadata.occurredAt)  // Occurred earlier
    }

    // ========== VERSION TESTS ==========

    @Test
    fun `should support schema versioning`() {
        // Given
        val v1Event = EventMetadata(eventType = "user.created", version = 1)
        val v2Event = EventMetadata(eventType = "user.created", version = 2)

        // Then
        assertEquals(1, v1Event.version)
        assertEquals(2, v2Event.version)
    }

    @Test
    fun `should allow version 0`() {
        // When
        val metadata = EventMetadata(eventType = "test.event", version = 0)

        // Then
        assertEquals(0, metadata.version)
    }

    @Test
    fun `should allow high version numbers`() {
        // When
        val metadata = EventMetadata(eventType = "test.event", version = 100)

        // Then
        assertEquals(100, metadata.version)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `should handle empty eventType`() {
        // When
        val metadata = EventMetadata(eventType = "")

        // Then
        assertEquals("", metadata.eventType)
    }

    @Test
    fun `should handle very long eventType`() {
        // Given
        val longEventType = "a".repeat(1000)

        // When
        val metadata = EventMetadata(eventType = longEventType)

        // Then
        assertEquals(longEventType, metadata.eventType)
    }

    @Test
    fun `should handle special characters in eventType`() {
        // When
        val metadata = EventMetadata(eventType = "user.created:v2#test")

        // Then
        assertEquals("user.created:v2#test", metadata.eventType)
    }

    @Test
    fun `should handle zero timestamp`() {
        // When
        val metadata = EventMetadata(eventType = "test.event", timestamp = 0L)

        // Then
        assertEquals(0L, metadata.timestamp)
    }

    @Test
    fun `should handle negative timestamp`() {
        // When
        val metadata = EventMetadata(eventType = "test.event", timestamp = -1000L)

        // Then
        assertEquals(-1000L, metadata.timestamp)
    }

    @Test
    fun `should handle empty correlationId`() {
        // When
        val metadata = EventMetadata(eventType = "test.event", correlationId = "")

        // Then
        assertEquals("", metadata.correlationId)
    }

    @Test
    fun `should handle very long correlationId`() {
        // Given
        val longId = "x".repeat(1000)

        // When
        val metadata = EventMetadata(eventType = "test.event", correlationId = longId)

        // Then
        assertEquals(longId, metadata.correlationId)
    }
}
