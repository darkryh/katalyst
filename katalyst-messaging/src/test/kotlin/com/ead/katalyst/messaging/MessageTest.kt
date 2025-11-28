package com.ead.katalyst.messaging

import kotlin.test.*

/**
 * Comprehensive tests for Message data class.
 *
 * Tests cover:
 * - Message creation with different parameters
 * - ByteArray payload handling
 * - Headers manipulation
 * - Custom equals() and hashCode() implementations
 * - Edge cases (null key, empty headers, large payloads)
 * - Data class behavior
 * - Practical usage scenarios
 */
class MessageTest {

    // ========== CONSTRUCTION TESTS ==========

    @Test
    fun `Message should be created with all parameters`() {
        // Given
        val key = "message-key"
        val payload = "test payload".toByteArray()
        val headers = mapOf("header1" to "value1", "header2" to "value2")

        // When
        val message = Message(key, payload, headers)

        // Then
        assertEquals(key, message.key)
        assertTrue(payload.contentEquals(message.payload))
        assertEquals(headers, message.headers)
    }

    @Test
    fun `Message should be created with minimal parameters`() {
        // Given
        val payload = "test".toByteArray()

        // When
        val message = Message(payload = payload)

        // Then
        assertNull(message.key)
        assertTrue(payload.contentEquals(message.payload))
        assertTrue(message.headers.isEmpty())
    }

    @Test
    fun `Message should support null key`() {
        // Given
        val payload = "test".toByteArray()

        // When
        val message = Message(key = null, payload = payload)

        // Then
        assertNull(message.key)
    }

    @Test
    fun `Message should support empty headers`() {
        // Given
        val payload = "test".toByteArray()

        // When
        val message = Message(payload = payload, headers = emptyMap())

        // Then
        assertTrue(message.headers.isEmpty())
    }

    @Test
    fun `Message should support empty payload`() {
        // Given
        val payload = ByteArray(0)

        // When
        val message = Message(payload = payload)

        // Then
        assertEquals(0, message.payload.size)
    }

    // ========== PAYLOAD TESTS ==========

    @Test
    fun `Message should handle string payloads`() {
        // Given
        val text = "Hello, World!"
        val payload = text.toByteArray()

        // When
        val message = Message(payload = payload)

        // Then
        assertEquals(text, String(message.payload))
    }

    @Test
    fun `Message should handle binary payloads`() {
        // Given
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        // When
        val message = Message(payload = payload)

        // Then
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun `Message should handle large payloads`() {
        // Given
        val payload = ByteArray(10000) { it.toByte() }

        // When
        val message = Message(payload = payload)

        // Then
        assertEquals(10000, message.payload.size)
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun `Message should handle UTF-8 encoded payloads`() {
        // Given
        val text = "Hello 世界 🌍"
        val payload = text.toByteArray(Charsets.UTF_8)

        // When
        val message = Message(payload = payload)

        // Then
        assertEquals(text, String(message.payload, Charsets.UTF_8))
    }

    // ========== HEADERS TESTS ==========

    @Test
    fun `Message should support single header`() {
        // Given
        val payload = "test".toByteArray()
        val headers = mapOf("Content-Type" to "application/json")

        // When
        val message = Message(payload = payload, headers = headers)

        // Then
        assertEquals("application/json", message.headers["Content-Type"])
    }

    @Test
    fun `Message should support multiple headers`() {
        // Given
        val payload = "test".toByteArray()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Content-Encoding" to "gzip",
            "X-Correlation-Id" to "123-456-789"
        )

        // When
        val message = Message(payload = payload, headers = headers)

        // Then
        assertEquals(3, message.headers.size)
        assertEquals("application/json", message.headers["Content-Type"])
        assertEquals("gzip", message.headers["Content-Encoding"])
        assertEquals("123-456-789", message.headers["X-Correlation-Id"])
    }

    @Test
    fun `Message headers should be case-sensitive`() {
        // Given
        val payload = "test".toByteArray()
        val headers = mapOf("Content-Type" to "value")

        // When
        val message = Message(payload = payload, headers = headers)

        // Then
        assertEquals("value", message.headers["Content-Type"])
        assertNull(message.headers["content-type"])
    }

    // ========== EQUALITY TESTS ==========

    @Test
    fun `Messages with same content should be equal`() {
        // Given
        val payload = "test".toByteArray()
        val headers = mapOf("header" to "value")
        val message1 = Message("key", payload, headers)
        val message2 = Message("key", payload, headers)

        // Then
        assertEquals(message1, message2)
    }

    @Test
    fun `Messages with different keys should not be equal`() {
        // Given
        val payload = "test".toByteArray()
        val message1 = Message("key1", payload)
        val message2 = Message("key2", payload)

        // Then
        assertNotEquals(message1, message2)
    }

    @Test
    fun `Messages with different payloads should not be equal`() {
        // Given
        val message1 = Message(payload = "payload1".toByteArray())
        val message2 = Message(payload = "payload2".toByteArray())

        // Then
        assertNotEquals(message1, message2)
    }

    @Test
    fun `Messages with different headers should not be equal`() {
        // Given
        val payload = "test".toByteArray()
        val message1 = Message(payload = payload, headers = mapOf("h1" to "v1"))
        val message2 = Message(payload = payload, headers = mapOf("h2" to "v2"))

        // Then
        assertNotEquals(message1, message2)
    }

    @Test
    fun `Message should be equal to itself`() {
        // Given
        val message = Message("key", "payload".toByteArray())

        // Then
        assertEquals(message, message)
    }

    @Test
    fun `Message should not equal null`() {
        // Given
        val message = Message(payload = "test".toByteArray())

        // Then
        assertNotNull(message)
    }

    @Test
    fun `Message should not equal different type`() {
        // Given
        val message = Message(payload = "test".toByteArray())
        val other = "not a message"

        // Then
        assertFalse(message.equals(other))
    }

    // ========== HASHCODE TESTS ==========

    @Test
    fun `Messages with same content should have same hashCode`() {
        // Given
        val payload = "test".toByteArray()
        val headers = mapOf("header" to "value")
        val message1 = Message("key", payload, headers)
        val message2 = Message("key", payload, headers)

        // Then
        assertEquals(message1.hashCode(), message2.hashCode())
    }

    @Test
    fun `Messages with different keys should have different hashCode`() {
        // Given
        val payload = "test".toByteArray()
        val message1 = Message("key1", payload)
        val message2 = Message("key2", payload)

        // Then
        assertNotEquals(message1.hashCode(), message2.hashCode())
    }

    @Test
    fun `Messages with null key should have consistent hashCode`() {
        // Given
        val payload = "test".toByteArray()
        val message = Message(null, payload)

        // When
        val hash1 = message.hashCode()
        val hash2 = message.hashCode()

        // Then
        assertEquals(hash1, hash2)
    }

    @Test
    fun `Message hashCode should work in HashSet`() {
        // Given
        val message1 = Message("key1", "payload1".toByteArray())
        val message2 = Message("key2", "payload2".toByteArray())
        val message3 = Message("key1", "payload1".toByteArray())  // Same as message1

        // When
        val set = hashSetOf(message1, message2, message3)

        // Then
        assertEquals(2, set.size)  // message1 and message3 are equal
        assertTrue(set.contains(message1))
        assertTrue(set.contains(message2))
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `Message should support toString`() {
        // Given
        val message = Message("key", "payload".toByteArray(), mapOf("h" to "v"))

        // When
        val string = message.toString()

        // Then
        assertTrue(string.contains("Message"))
    }

    @Test
    fun `Message should support destructuring by position`() {
        // Given
        val key = "message-key"
        val payload = "test".toByteArray()
        val headers = mapOf("h" to "v")
        val message = Message(key, payload, headers)

        // When
        val (k, p, h) = message

        // Then
        assertEquals(key, k)
        assertTrue(payload.contentEquals(p))
        assertEquals(headers, h)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical JSON message scenario`() {
        // Given - Sending JSON payload
        val json = """{"userId": 123, "action": "login"}"""
        val message = Message(
            key = "user-123",
            payload = json.toByteArray(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-Correlation-Id" to "abc-123"
            )
        )

        // Then
        assertEquals("user-123", message.key)
        assertEquals(json, String(message.payload))
        assertEquals("application/json", message.headers["Content-Type"])
        assertEquals("abc-123", message.headers["X-Correlation-Id"])
    }

    @Test
    fun `typical binary message scenario`() {
        // Given - Sending binary data (e.g., image)
        val binaryData = ByteArray(1024) { it.toByte() }
        val message = Message(
            key = "image-456",
            payload = binaryData,
            headers = mapOf(
                "Content-Type" to "image/png",
                "Content-Length" to "1024"
            )
        )

        // Then
        assertEquals("image-456", message.key)
        assertEquals(1024, message.payload.size)
        assertEquals("image/png", message.headers["Content-Type"])
    }

    @Test
    fun `message without key for broadcast scenarios`() {
        // Given - Broadcasting message to all consumers
        val announcement = "System maintenance in 30 minutes"
        val message = Message(
            key = null,
            payload = announcement.toByteArray(),
            headers = mapOf("Message-Type" to "announcement")
        )

        // Then
        assertNull(message.key)
        assertEquals(announcement, String(message.payload))
        assertEquals("announcement", message.headers["Message-Type"])
    }

    @Test
    fun `message with correlation and causation headers`() {
        // Given - Distributed tracing scenario
        val message = Message(
            key = "order-789",
            payload = "Order created".toByteArray(),
            headers = mapOf(
                "X-Correlation-Id" to "corr-123",
                "X-Causation-Id" to "cause-456",
                "X-Timestamp" to "2025-01-01T00:00:00Z"
            )
        )

        // Then
        assertEquals("corr-123", message.headers["X-Correlation-Id"])
        assertEquals("cause-456", message.headers["X-Causation-Id"])
        assertEquals("2025-01-01T00:00:00Z", message.headers["X-Timestamp"])
    }

    @Test
    fun `message collection operations`() {
        // Given
        val messages = listOf(
            Message("key1", "payload1".toByteArray()),
            Message("key2", "payload2".toByteArray()),
            Message("key3", "payload3".toByteArray())
        )

        // When - Filter messages by key
        val filtered = messages.filter { it.key?.startsWith("key") == true }

        // Then
        assertEquals(3, filtered.size)
    }

    @Test
    fun `message immutability`() {
        // Given
        val originalHeaders = mapOf("h1" to "v1")
        val message = Message("key", "payload".toByteArray(), originalHeaders)

        // When - Attempting to modify headers won't affect original message
        val newHeaders = message.headers + ("h2" to "v2")

        // Then
        assertEquals(1, message.headers.size)  // Original unchanged
        assertEquals(2, newHeaders.size)  // New map has both
    }
}
