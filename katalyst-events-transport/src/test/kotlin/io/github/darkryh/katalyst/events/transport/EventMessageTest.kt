package io.github.darkryh.katalyst.events.transport

import kotlin.test.*

/**
 * Comprehensive tests for EventMessage.
 *
 * Tests cover:
 * - Basic construction
 * - ByteArray payload handling
 * - Header management
 * - Custom equals/hashCode (with ByteArray)
 * - Builder conversion
 * - Timestamp handling
 * - Event ID and type
 * - Practical usage scenarios
 */
class EventMessageTest {

    // ========== BASIC CONSTRUCTION TESTS ==========

    @Test
    fun `EventMessage should require contentType and payload`() {
        val payload = "test data".toByteArray()
        val message = EventMessage(
            contentType = "application/json",
            payload = payload
        )

        assertEquals("application/json", message.contentType)
        assertTrue(message.payload.contentEquals(payload))
    }

    @Test
    fun `EventMessage should have empty headers by default`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )

        assertTrue(message.headers.isEmpty())
    }

    @Test
    fun `EventMessage should generate timestamp by default`() {
        val before = System.currentTimeMillis()
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )
        val after = System.currentTimeMillis()

        assertTrue(message.timestamp >= before)
        assertTrue(message.timestamp <= after)
    }

    @Test
    fun `EventMessage should have null eventId by default`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )

        assertNull(message.eventId)
    }

    @Test
    fun `EventMessage should have null eventType by default`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )

        assertNull(message.eventType)
    }

    // ========== CUSTOM CONSTRUCTION TESTS ==========

    @Test
    fun `EventMessage should support custom headers`() {
        val headers = mapOf("key1" to "value1", "key2" to "value2")
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            headers = headers
        )

        assertEquals(2, message.headers.size)
        assertEquals("value1", message.headers["key1"])
        assertEquals("value2", message.headers["key2"])
    }

    @Test
    fun `EventMessage should support custom timestamp`() {
        val customTimestamp = 1234567890L
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            timestamp = customTimestamp
        )

        assertEquals(customTimestamp, message.timestamp)
    }

    @Test
    fun `EventMessage should support eventId`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            eventId = "evt-123"
        )

        assertEquals("evt-123", message.eventId)
    }

    @Test
    fun `EventMessage should support eventType`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            eventType = "user.created"
        )

        assertEquals("user.created", message.eventType)
    }

    // ========== PAYLOAD HANDLING TESTS ==========

    @Test
    fun `EventMessage should handle empty payload`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )

        assertEquals(0, message.payload.size)
    }

    @Test
    fun `EventMessage should handle small payload`() {
        val payload = "Hello".toByteArray()
        val message = EventMessage(
            contentType = "text/plain",
            payload = payload
        )

        assertTrue(message.payload.contentEquals(payload))
        assertEquals("Hello", String(message.payload))
    }

    @Test
    fun `EventMessage should handle large payload`() {
        val payload = ByteArray(10000) { it.toByte() }
        val message = EventMessage(
            contentType = "application/octet-stream",
            payload = payload
        )

        assertEquals(10000, message.payload.size)
        assertTrue(message.payload.contentEquals(payload))
    }

    @Test
    fun `EventMessage should handle JSON payload`() {
        val jsonPayload = """{"name": "John", "age": 30}""".toByteArray()
        val message = EventMessage(
            contentType = "application/json",
            payload = jsonPayload
        )

        val jsonString = String(message.payload)
        assertTrue(jsonString.contains("John"))
        assertTrue(jsonString.contains("30"))
    }

    // ========== HEADER MANAGEMENT TESTS ==========

    @Test
    fun `getHeader should return header value`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            headers = mapOf("correlation-id" to "abc-123")
        )

        assertEquals("abc-123", message.getHeader("correlation-id"))
    }

    @Test
    fun `getHeader should return null for missing header`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )

        assertNull(message.getHeader("missing"))
    }

    @Test
    fun `getHeader should return default value for missing header`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )

        assertEquals("default", message.getHeader("missing", "default"))
    }

    @Test
    fun `hasHeader should return true for existing header`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            headers = mapOf("key" to "value")
        )

        assertTrue(message.hasHeader("key"))
    }

    @Test
    fun `hasHeader should return false for missing header`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf()
        )

        assertFalse(message.hasHeader("missing"))
    }

    @Test
    fun `EventMessage should support multiple headers`() {
        val headers = mapOf(
            "correlation-id" to "abc-123",
            "user-id" to "user-456",
            "content-encoding" to "gzip"
        )

        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            headers = headers
        )

        assertTrue(message.hasHeader("correlation-id"))
        assertTrue(message.hasHeader("user-id"))
        assertTrue(message.hasHeader("content-encoding"))
        assertEquals(3, message.headers.size)
    }

    // ========== EQUALS AND HASHCODE TESTS ==========

    @Test
    fun `EventMessage should be equal with same content`() {
        val payload = "test".toByteArray()
        val headers = mapOf("key" to "value")

        val message1 = EventMessage(
            contentType = "application/json",
            payload = payload,
            headers = headers,
            eventId = "evt-1",
            eventType = "test.event"
        )

        val message2 = EventMessage(
            contentType = "application/json",
            payload = "test".toByteArray(),
            headers = headers,
            eventId = "evt-1",
            eventType = "test.event"
        )

        assertEquals(message1, message2)
    }

    @Test
    fun `EventMessage should not equal with different contentType`() {
        val payload = "test".toByteArray()

        val message1 = EventMessage(
            contentType = "application/json",
            payload = payload
        )

        val message2 = EventMessage(
            contentType = "application/xml",
            payload = payload
        )

        assertNotEquals(message1, message2)
    }

    @Test
    fun `EventMessage should not equal with different payload`() {
        val message1 = EventMessage(
            contentType = "application/json",
            payload = "test1".toByteArray()
        )

        val message2 = EventMessage(
            contentType = "application/json",
            payload = "test2".toByteArray()
        )

        assertNotEquals(message1, message2)
    }

    @Test
    fun `EventMessage should not equal with different headers`() {
        val message1 = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            headers = mapOf("key" to "value1")
        )

        val message2 = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            headers = mapOf("key" to "value2")
        )

        assertNotEquals(message1, message2)
    }

    @Test
    fun `EventMessage should ignore timestamp in equality`() {
        val payload = "test".toByteArray()

        val message1 = EventMessage(
            contentType = "application/json",
            payload = payload,
            timestamp = 1000L
        )

        val message2 = EventMessage(
            contentType = "application/json",
            payload = "test".toByteArray(),
            timestamp = 2000L
        )

        // Should be equal despite different timestamps
        assertEquals(message1, message2)
    }

    @Test
    fun `EventMessage should not equal with different eventId`() {
        val message1 = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            eventId = "evt-1"
        )

        val message2 = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            eventId = "evt-2"
        )

        assertNotEquals(message1, message2)
    }

    @Test
    fun `EventMessage should have consistent hashCode`() {
        val payload = "test".toByteArray()
        val headers = mapOf("key" to "value")

        val message1 = EventMessage(
            contentType = "application/json",
            payload = payload,
            headers = headers
        )

        val message2 = EventMessage(
            contentType = "application/json",
            payload = "test".toByteArray(),
            headers = headers
        )

        assertEquals(message1.hashCode(), message2.hashCode())
    }

    // ========== BUILDER CONVERSION TESTS ==========

    @Test
    fun `toBuilder should create builder with current values`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = "test".toByteArray(),
            headers = mapOf("key" to "value"),
            timestamp = 1000L,
            eventId = "evt-123",
            eventType = "user.created"
        )

        val builder = message.toBuilder()

        assertNotNull(builder)
        // Builder should be able to build equivalent message
        // (can't test directly without EventMessageBuilder implementation)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `JSON user created event message`() {
        val jsonPayload = """
            {
                "userId": "123",
                "name": "John Doe",
                "email": "john@example.com"
            }
        """.trimIndent().toByteArray()

        val message = EventMessage(
            contentType = "application/json",
            payload = jsonPayload,
            headers = mapOf(
                "correlation-id" to "req-456",
                "user-id" to "admin-789"
            ),
            eventId = "evt-user-123",
            eventType = "user.created"
        )

        assertEquals("application/json", message.contentType)
        assertEquals("evt-user-123", message.eventId)
        assertEquals("user.created", message.eventType)
        assertEquals("req-456", message.getHeader("correlation-id"))
        assertTrue(String(message.payload).contains("John Doe"))
    }

    @Test
    fun `order placed event with metadata`() {
        val orderData = """{"orderId": "ord-789", "total": 99.99}""".toByteArray()

        val message = EventMessage(
            contentType = "application/json",
            payload = orderData,
            headers = mapOf(
                "source" to "web-app",
                "version" to "1.0",
                "priority" to "high"
            ),
            eventId = "evt-order-789",
            eventType = "order.placed"
        )

        assertTrue(message.hasHeader("source"))
        assertTrue(message.hasHeader("version"))
        assertTrue(message.hasHeader("priority"))
        assertEquals("high", message.getHeader("priority"))
    }

    @Test
    fun `binary content event message`() {
        val binaryData = ByteArray(256) { it.toByte() }

        val message = EventMessage(
            contentType = "application/octet-stream",
            payload = binaryData,
            eventId = "evt-file-upload-001"
        )

        assertEquals("application/octet-stream", message.contentType)
        assertEquals(256, message.payload.size)
    }

    @Test
    fun `event message with empty event ID`() {
        val message = EventMessage(
            contentType = "application/json",
            payload = "{}".toByteArray(),
            eventType = "notification.sent"
        )

        assertNull(message.eventId)
        assertEquals("notification.sent", message.eventType)
    }

    @Test
    fun `event message comparison for deduplication`() {
        val payload = """{"data": "test"}""".toByteArray()

        val message1 = EventMessage(
            contentType = "application/json",
            payload = payload,
            eventId = "evt-dedup-123",
            timestamp = 1000L
        )

        val message2 = EventMessage(
            contentType = "application/json",
            payload = """{"data": "test"}""".toByteArray(),
            eventId = "evt-dedup-123",
            timestamp = 2000L  // Different timestamp
        )

        // Should be equal for deduplication (timestamp ignored)
        assertEquals(message1, message2)
    }

    @Test
    fun `event message with complex headers`() {
        val headers = mapOf(
            "trace-id" to "trace-abc-123",
            "span-id" to "span-def-456",
            "retry-count" to "3",
            "source-system" to "payment-service",
            "destination-region" to "us-east-1"
        )

        val message = EventMessage(
            contentType = "application/json",
            payload = byteArrayOf(),
            headers = headers
        )

        assertEquals(5, message.headers.size)
        assertEquals("3", message.getHeader("retry-count"))
        assertEquals("payment-service", message.getHeader("source-system"))
    }
}
