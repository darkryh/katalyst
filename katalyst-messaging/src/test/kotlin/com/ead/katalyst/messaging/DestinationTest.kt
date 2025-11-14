package com.ead.katalyst.messaging

import kotlin.test.*

/**
 * Comprehensive tests for Destination data class and DestinationType enum.
 *
 * Tests cover:
 * - Destination creation
 * - DestinationType enum values
 * - Data class behavior (equality, hashCode, copy)
 * - Edge cases (empty names, special characters)
 * - Practical usage scenarios (queues, topics, streams)
 */
class DestinationTest {

    // ========== DESTINATION TYPE ENUM TESTS ==========

    @Test
    fun `DestinationType should have QUEUE value`() {
        // Then
        assertNotNull(DestinationType.QUEUE)
    }

    @Test
    fun `DestinationType should have TOPIC value`() {
        // Then
        assertNotNull(DestinationType.TOPIC)
    }

    @Test
    fun `DestinationType should have STREAM value`() {
        // Then
        assertNotNull(DestinationType.STREAM)
    }

    @Test
    fun `DestinationType should have exactly 3 values`() {
        // When
        val values = DestinationType.values()

        // Then
        assertEquals(3, values.size)
    }

    @Test
    fun `DestinationType values should be in expected order`() {
        // When
        val values = DestinationType.values()

        // Then
        assertEquals(DestinationType.QUEUE, values[0])
        assertEquals(DestinationType.TOPIC, values[1])
        assertEquals(DestinationType.STREAM, values[2])
    }

    @Test
    fun `DestinationType should support valueOf`() {
        // When/Then
        assertEquals(DestinationType.QUEUE, DestinationType.valueOf("QUEUE"))
        assertEquals(DestinationType.TOPIC, DestinationType.valueOf("TOPIC"))
        assertEquals(DestinationType.STREAM, DestinationType.valueOf("STREAM"))
    }

    @Test
    fun `DestinationType valueOf should throw for invalid value`() {
        // Then
        assertFailsWith<IllegalArgumentException> {
            DestinationType.valueOf("INVALID")
        }
    }

    // ========== DESTINATION CONSTRUCTION TESTS ==========

    @Test
    fun `Destination should be created with name and type`() {
        // When
        val destination = Destination("my-queue", DestinationType.QUEUE)

        // Then
        assertEquals("my-queue", destination.name)
        assertEquals(DestinationType.QUEUE, destination.type)
    }

    @Test
    fun `Destination should support queue type`() {
        // When
        val destination = Destination("orders", DestinationType.QUEUE)

        // Then
        assertEquals(DestinationType.QUEUE, destination.type)
    }

    @Test
    fun `Destination should support topic type`() {
        // When
        val destination = Destination("events", DestinationType.TOPIC)

        // Then
        assertEquals(DestinationType.TOPIC, destination.type)
    }

    @Test
    fun `Destination should support stream type`() {
        // When
        val destination = Destination("logs", DestinationType.STREAM)

        // Then
        assertEquals(DestinationType.STREAM, destination.type)
    }

    // ========== DESTINATION NAME TESTS ==========

    @Test
    fun `Destination should support simple names`() {
        // When
        val destination = Destination("simple", DestinationType.QUEUE)

        // Then
        assertEquals("simple", destination.name)
    }

    @Test
    fun `Destination should support names with hyphens`() {
        // When
        val destination = Destination("order-created", DestinationType.QUEUE)

        // Then
        assertEquals("order-created", destination.name)
    }

    @Test
    fun `Destination should support names with underscores`() {
        // When
        val destination = Destination("user_registered", DestinationType.TOPIC)

        // Then
        assertEquals("user_registered", destination.name)
    }

    @Test
    fun `Destination should support names with dots`() {
        // When
        val destination = Destination("com.example.events", DestinationType.TOPIC)

        // Then
        assertEquals("com.example.events", destination.name)
    }

    @Test
    fun `Destination should support names with slashes`() {
        // When
        val destination = Destination("domain/subdomain/queue", DestinationType.QUEUE)

        // Then
        assertEquals("domain/subdomain/queue", destination.name)
    }

    @Test
    fun `Destination should support empty name`() {
        // When
        val destination = Destination("", DestinationType.QUEUE)

        // Then
        assertEquals("", destination.name)
    }

    @Test
    fun `Destination should support long names`() {
        // Given
        val longName = "a".repeat(256)

        // When
        val destination = Destination(longName, DestinationType.QUEUE)

        // Then
        assertEquals(256, destination.name.length)
    }

    // ========== EQUALITY TESTS ==========

    @Test
    fun `Destinations with same name and type should be equal`() {
        // Given
        val dest1 = Destination("queue1", DestinationType.QUEUE)
        val dest2 = Destination("queue1", DestinationType.QUEUE)

        // Then
        assertEquals(dest1, dest2)
    }

    @Test
    fun `Destinations with different names should not be equal`() {
        // Given
        val dest1 = Destination("queue1", DestinationType.QUEUE)
        val dest2 = Destination("queue2", DestinationType.QUEUE)

        // Then
        assertNotEquals(dest1, dest2)
    }

    @Test
    fun `Destinations with different types should not be equal`() {
        // Given
        val dest1 = Destination("name", DestinationType.QUEUE)
        val dest2 = Destination("name", DestinationType.TOPIC)

        // Then
        assertNotEquals(dest1, dest2)
    }

    @Test
    fun `Destination should be equal to itself`() {
        // Given
        val destination = Destination("queue", DestinationType.QUEUE)

        // Then
        assertEquals(destination, destination)
    }

    // ========== HASHCODE TESTS ==========

    @Test
    fun `Destinations with same content should have same hashCode`() {
        // Given
        val dest1 = Destination("queue1", DestinationType.QUEUE)
        val dest2 = Destination("queue1", DestinationType.QUEUE)

        // Then
        assertEquals(dest1.hashCode(), dest2.hashCode())
    }

    @Test
    fun `Destination hashCode should work in HashSet`() {
        // Given
        val dest1 = Destination("queue1", DestinationType.QUEUE)
        val dest2 = Destination("queue2", DestinationType.QUEUE)
        val dest3 = Destination("queue1", DestinationType.QUEUE)  // Same as dest1

        // When
        val set = hashSetOf(dest1, dest2, dest3)

        // Then
        assertEquals(2, set.size)
        assertTrue(set.contains(dest1))
        assertTrue(set.contains(dest2))
    }

    @Test
    fun `Destination hashCode should work in HashMap`() {
        // Given
        val dest = Destination("queue", DestinationType.QUEUE)
        val map = hashMapOf(dest to "value")

        // When
        val value = map[dest]

        // Then
        assertEquals("value", value)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `Destination should support toString`() {
        // Given
        val destination = Destination("my-queue", DestinationType.QUEUE)

        // When
        val string = destination.toString()

        // Then
        assertTrue(string.contains("Destination"))
        assertTrue(string.contains("my-queue"))
        assertTrue(string.contains("QUEUE"))
    }

    @Test
    fun `Destination should support copy with new name`() {
        // Given
        val original = Destination("original", DestinationType.QUEUE)

        // When
        val copied = original.copy(name = "modified")

        // Then
        assertEquals("modified", copied.name)
        assertEquals(DestinationType.QUEUE, copied.type)
        assertEquals("original", original.name)  // Original unchanged
    }

    @Test
    fun `Destination should support copy with new type`() {
        // Given
        val original = Destination("name", DestinationType.QUEUE)

        // When
        val copied = original.copy(type = DestinationType.TOPIC)

        // Then
        assertEquals("name", copied.name)
        assertEquals(DestinationType.TOPIC, copied.type)
        assertEquals(DestinationType.QUEUE, original.type)  // Original unchanged
    }

    @Test
    fun `Destination should support copy with all parameters`() {
        // Given
        val original = Destination("old", DestinationType.QUEUE)

        // When
        val copied = original.copy(name = "new", type = DestinationType.STREAM)

        // Then
        assertEquals("new", copied.name)
        assertEquals(DestinationType.STREAM, copied.type)
    }

    @Test
    fun `Destination should support destructuring`() {
        // Given
        val destination = Destination("my-queue", DestinationType.QUEUE)

        // When
        val (name, type) = destination

        // Then
        assertEquals("my-queue", name)
        assertEquals(DestinationType.QUEUE, type)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical queue destination for order processing`() {
        // Given
        val destination = Destination("orders-queue", DestinationType.QUEUE)

        // Then
        assertEquals("orders-queue", destination.name)
        assertEquals(DestinationType.QUEUE, destination.type)
    }

    @Test
    fun `typical topic destination for event broadcasting`() {
        // Given
        val destination = Destination("user-events", DestinationType.TOPIC)

        // Then
        assertEquals("user-events", destination.name)
        assertEquals(DestinationType.TOPIC, destination.type)
    }

    @Test
    fun `typical stream destination for log processing`() {
        // Given
        val destination = Destination("application-logs", DestinationType.STREAM)

        // Then
        assertEquals("application-logs", destination.name)
        assertEquals(DestinationType.STREAM, destination.type)
    }

    @Test
    fun `destination collection operations`() {
        // Given
        val destinations = listOf(
            Destination("queue1", DestinationType.QUEUE),
            Destination("topic1", DestinationType.TOPIC),
            Destination("queue2", DestinationType.QUEUE),
            Destination("stream1", DestinationType.STREAM)
        )

        // When - Filter by type
        val queues = destinations.filter { it.type == DestinationType.QUEUE }
        val topics = destinations.filter { it.type == DestinationType.TOPIC }
        val streams = destinations.filter { it.type == DestinationType.STREAM }

        // Then
        assertEquals(2, queues.size)
        assertEquals(1, topics.size)
        assertEquals(1, streams.size)
    }

    @Test
    fun `destination as map key`() {
        // Given
        val destinations = mapOf(
            Destination("orders", DestinationType.QUEUE) to "Order processing queue",
            Destination("events", DestinationType.TOPIC) to "Event broadcasting topic",
            Destination("logs", DestinationType.STREAM) to "Application logs stream"
        )

        // When
        val orderDesc = destinations[Destination("orders", DestinationType.QUEUE)]
        val eventDesc = destinations[Destination("events", DestinationType.TOPIC)]

        // Then
        assertEquals("Order processing queue", orderDesc)
        assertEquals("Event broadcasting topic", eventDesc)
    }

    @Test
    fun `hierarchical destination naming`() {
        // Given - Destinations organized hierarchically
        val destinations = listOf(
            Destination("com.example.orders.created", DestinationType.TOPIC),
            Destination("com.example.orders.updated", DestinationType.TOPIC),
            Destination("com.example.users.registered", DestinationType.TOPIC)
        )

        // When - Filter by prefix
        val orderTopics = destinations.filter { it.name.startsWith("com.example.orders") }

        // Then
        assertEquals(2, orderTopics.size)
    }

    @Test
    fun `destination name pattern matching`() {
        // Given
        val destination = Destination("order.created.v1", DestinationType.TOPIC)

        // When - Match pattern
        val isOrderEvent = destination.name.startsWith("order.")
        val isVersioned = destination.name.contains(".v")

        // Then
        assertTrue(isOrderEvent)
        assertTrue(isVersioned)
    }

    @Test
    fun `destination grouping by type`() {
        // Given
        val destinations = listOf(
            Destination("q1", DestinationType.QUEUE),
            Destination("t1", DestinationType.TOPIC),
            Destination("q2", DestinationType.QUEUE),
            Destination("s1", DestinationType.STREAM)
        )

        // When
        val grouped = destinations.groupBy { it.type }

        // Then
        assertEquals(3, grouped.keys.size)
        assertEquals(2, grouped[DestinationType.QUEUE]?.size)
        assertEquals(1, grouped[DestinationType.TOPIC]?.size)
        assertEquals(1, grouped[DestinationType.STREAM]?.size)
    }
}
