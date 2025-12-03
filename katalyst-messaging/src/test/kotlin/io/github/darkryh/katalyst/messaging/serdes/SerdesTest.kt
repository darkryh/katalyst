package io.github.darkryh.katalyst.messaging.serdes

import kotlin.test.*

/**
 * Comprehensive tests for Serializer and Deserializer interfaces.
 *
 * Tests cover:
 * - Interface contract
 * - Custom implementations (String, JSON-like, Binary)
 * - Roundtrip serialization/deserialization
 * - Edge cases (empty, null, large data)
 * - Practical usage scenarios
 */
class SerdesTest {

    // ========== TEST IMPLEMENTATIONS ==========

    class StringSerializer : Serializer<String> {
        override fun serialize(input: String): ByteArray = input.toByteArray()
    }

    class StringDeserializer : Deserializer<String> {
        override fun deserialize(bytes: ByteArray): String = String(bytes)
    }

    data class User(val id: Int, val name: String, val email: String)

    class UserSerializer : Serializer<User> {
        override fun serialize(input: User): ByteArray {
            val data = "${input.id},${input.name},${input.email}"
            return data.toByteArray()
        }
    }

    class UserDeserializer : Deserializer<User> {
        override fun deserialize(bytes: ByteArray): User {
            val parts = String(bytes).split(",")
            return User(parts[0].toInt(), parts[1], parts[2])
        }
    }

    class IntSerializer : Serializer<Int> {
        override fun serialize(input: Int): ByteArray {
            return byteArrayOf(
                (input shr 24).toByte(),
                (input shr 16).toByte(),
                (input shr 8).toByte(),
                input.toByte()
            )
        }
    }

    class IntDeserializer : Deserializer<Int> {
        override fun deserialize(bytes: ByteArray): Int {
            return (bytes[0].toInt() and 0xFF shl 24) or
                    (bytes[1].toInt() and 0xFF shl 16) or
                    (bytes[2].toInt() and 0xFF shl 8) or
                    (bytes[3].toInt() and 0xFF)
        }
    }

    // ========== SERIALIZER INTERFACE TESTS ==========

    @Test
    fun `Serializer should have serialize method`() {
        // Given
        val serializer = StringSerializer()
        val input = "test"

        // When
        val bytes = serializer.serialize(input)

        // Then
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `StringSerializer should serialize strings`() {
        // Given
        val serializer = StringSerializer()
        val input = "Hello, World!"

        // When
        val bytes = serializer.serialize(input)

        // Then
        assertEquals(input, String(bytes))
    }

    @Test
    fun `StringSerializer should handle empty strings`() {
        // Given
        val serializer = StringSerializer()
        val input = ""

        // When
        val bytes = serializer.serialize(input)

        // Then
        assertEquals(0, bytes.size)
    }

    @Test
    fun `StringSerializer should handle UTF-8 characters`() {
        // Given
        val serializer = StringSerializer()
        val input = "Hello 世界 🌍"

        // When
        val bytes = serializer.serialize(input)

        // Then
        assertEquals(input, String(bytes, Charsets.UTF_8))
    }

    // ========== DESERIALIZER INTERFACE TESTS ==========

    @Test
    fun `Deserializer should have deserialize method`() {
        // Given
        val deserializer = StringDeserializer()
        val bytes = "test".toByteArray()

        // When
        val result = deserializer.deserialize(bytes)

        // Then
        assertNotNull(result)
    }

    @Test
    fun `StringDeserializer should deserialize strings`() {
        // Given
        val deserializer = StringDeserializer()
        val bytes = "Hello, World!".toByteArray()

        // When
        val result = deserializer.deserialize(bytes)

        // Then
        assertEquals("Hello, World!", result)
    }

    @Test
    fun `StringDeserializer should handle empty bytes`() {
        // Given
        val deserializer = StringDeserializer()
        val bytes = ByteArray(0)

        // When
        val result = deserializer.deserialize(bytes)

        // Then
        assertEquals("", result)
    }

    // ========== ROUNDTRIP TESTS ==========

    @Test
    fun `String roundtrip should preserve data`() {
        // Given
        val serializer = StringSerializer()
        val deserializer = StringDeserializer()
        val original = "Test message"

        // When
        val bytes = serializer.serialize(original)
        val result = deserializer.deserialize(bytes)

        // Then
        assertEquals(original, result)
    }

    @Test
    fun `User roundtrip should preserve data`() {
        // Given
        val serializer = UserSerializer()
        val deserializer = UserDeserializer()
        val original = User(123, "John Doe", "john@example.com")

        // When
        val bytes = serializer.serialize(original)
        val result = deserializer.deserialize(bytes)

        // Then
        assertEquals(original, result)
    }

    @Test
    fun `Int roundtrip should preserve data`() {
        // Given
        val serializer = IntSerializer()
        val deserializer = IntDeserializer()
        val values = listOf(0, 1, -1, 42, -42, Int.MAX_VALUE, Int.MIN_VALUE)

        // When/Then
        for (value in values) {
            val bytes = serializer.serialize(value)
            val result = deserializer.deserialize(bytes)
            assertEquals(value, result)
        }
    }

    // ========== CUSTOM TYPE TESTS ==========

    @Test
    fun `UserSerializer should serialize user objects`() {
        // Given
        val serializer = UserSerializer()
        val user = User(1, "Alice", "alice@example.com")

        // When
        val bytes = serializer.serialize(user)
        val string = String(bytes)

        // Then
        assertEquals("1,Alice,alice@example.com", string)
    }

    @Test
    fun `UserDeserializer should deserialize user objects`() {
        // Given
        val deserializer = UserDeserializer()
        val bytes = "1,Alice,alice@example.com".toByteArray()

        // When
        val user = deserializer.deserialize(bytes)

        // Then
        assertEquals(1, user.id)
        assertEquals("Alice", user.name)
        assertEquals("alice@example.com", user.email)
    }

    // ========== INT SERIALIZATION TESTS ==========

    @Test
    fun `IntSerializer should serialize integers to 4 bytes`() {
        // Given
        val serializer = IntSerializer()

        // When
        val bytes = serializer.serialize(42)

        // Then
        assertEquals(4, bytes.size)
    }

    @Test
    fun `IntSerializer should handle zero`() {
        // Given
        val serializer = IntSerializer()
        val deserializer = IntDeserializer()

        // When
        val bytes = serializer.serialize(0)
        val result = deserializer.deserialize(bytes)

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `IntSerializer should handle negative numbers`() {
        // Given
        val serializer = IntSerializer()
        val deserializer = IntDeserializer()

        // When
        val bytes = serializer.serialize(-100)
        val result = deserializer.deserialize(bytes)

        // Then
        assertEquals(-100, result)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical message serialization workflow`() {
        // Given - Sending a user object as message
        val serializer = UserSerializer()
        val user = User(123, "Bob", "bob@test.com")

        // When
        val messagePayload = serializer.serialize(user)

        // Then
        assertNotNull(messagePayload)
        assertTrue(messagePayload.isNotEmpty())
        // Payload can be sent via messaging system
    }

    @Test
    fun `typical message deserialization workflow`() {
        // Given - Receiving a message payload
        val deserializer = UserDeserializer()
        val receivedPayload = "456,Charlie,charlie@test.com".toByteArray()

        // When
        val user = deserializer.deserialize(receivedPayload)

        // Then
        assertEquals(456, user.id)
        assertEquals("Charlie", user.name)
        assertEquals("charlie@test.com", user.email)
    }

    @Test
    fun `combined serializer deserializer pattern`() {
        // Given - SerDe pattern for reusability
        class StringSerDes : Serializer<String>, Deserializer<String> {
            override fun serialize(input: String): ByteArray = input.toByteArray()
            override fun deserialize(bytes: ByteArray): String = String(bytes)
        }

        val serdes = StringSerDes()
        val message = "Hello"

        // When
        val bytes = serdes.serialize(message)
        val result = serdes.deserialize(bytes)

        // Then
        assertEquals(message, result)
    }

    @Test
    fun `list serialization scenario`() {
        // Given
        class ListSerializer<T>(private val itemSerializer: Serializer<T>) : Serializer<List<T>> {
            override fun serialize(input: List<T>): ByteArray {
                val parts = input.map { String(itemSerializer.serialize(it)) }
                return parts.joinToString("|").toByteArray()
            }
        }

        val stringSerializer = StringSerializer()
        val listSerializer = ListSerializer(stringSerializer)
        val list = listOf("apple", "banana", "cherry")

        // When
        val bytes = listSerializer.serialize(list)

        // Then
        assertEquals("apple|banana|cherry", String(bytes))
    }

    @Test
    fun `large data serialization`() {
        // Given
        val serializer = StringSerializer()
        val largeString = "X".repeat(10000)

        // When
        val bytes = serializer.serialize(largeString)

        // Then
        assertEquals(10000, bytes.size)
    }

    @Test
    fun `binary data preservation`() {
        // Given
        class BinarySerializer : Serializer<ByteArray> {
            override fun serialize(input: ByteArray): ByteArray = input
        }

        class BinaryDeserializer : Deserializer<ByteArray> {
            override fun deserialize(bytes: ByteArray): ByteArray = bytes
        }

        val serializer = BinarySerializer()
        val deserializer = BinaryDeserializer()
        val binaryData = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        // When
        val bytes = serializer.serialize(binaryData)
        val result = deserializer.deserialize(bytes)

        // Then
        assertContentEquals(binaryData, result)
    }
}
