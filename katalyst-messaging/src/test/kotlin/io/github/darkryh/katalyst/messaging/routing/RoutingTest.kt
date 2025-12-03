package io.github.darkryh.katalyst.messaging.routing

import kotlin.test.*

/**
 * Comprehensive tests for RoutingConfig and RoutingType.
 *
 * Tests cover:
 * - RoutingType enum values
 * - RoutingConfig creation and defaults
 * - Data class behavior
 * - Practical routing scenarios
 */
class RoutingTest {

    // ========== ROUTING TYPE ENUM TESTS ==========

    @Test
    fun `RoutingType should have DIRECT value`() {
        assertNotNull(RoutingType.DIRECT)
    }

    @Test
    fun `RoutingType should have TOPIC value`() {
        assertNotNull(RoutingType.TOPIC)
    }

    @Test
    fun `RoutingType should have FANOUT value`() {
        assertNotNull(RoutingType.FANOUT)
    }

    @Test
    fun `RoutingType should have exactly 3 values`() {
        assertEquals(3, RoutingType.values().size)
    }

    @Test
    fun `RoutingType should support valueOf`() {
        assertEquals(RoutingType.DIRECT, RoutingType.valueOf("DIRECT"))
        assertEquals(RoutingType.TOPIC, RoutingType.valueOf("TOPIC"))
        assertEquals(RoutingType.FANOUT, RoutingType.valueOf("FANOUT"))
    }

    // ========== ROUTING CONFIG CONSTRUCTION TESTS ==========

    @Test
    fun `RoutingConfig should use DIRECT as default type`() {
        val config = RoutingConfig()
        assertEquals(RoutingType.DIRECT, config.routingType)
    }

    @Test
    fun `RoutingConfig should have null routingKey by default`() {
        val config = RoutingConfig()
        assertNull(config.routingKey)
    }

    @Test
    fun `RoutingConfig should support explicit routing type`() {
        val config = RoutingConfig(routingType = RoutingType.TOPIC)
        assertEquals(RoutingType.TOPIC, config.routingType)
    }

    @Test
    fun `RoutingConfig should support routing key`() {
        val config = RoutingConfig(routingKey = "order.created")
        assertEquals("order.created", config.routingKey)
    }

    @Test
    fun `RoutingConfig should support both type and key`() {
        val config = RoutingConfig(
            routingType = RoutingType.TOPIC,
            routingKey = "user.#"
        )
        assertEquals(RoutingType.TOPIC, config.routingType)
        assertEquals("user.#", config.routingKey)
    }

    // ========== EQUALITY TESTS ==========

    @Test
    fun `RoutingConfigs with same values should be equal`() {
        val config1 = RoutingConfig(RoutingType.DIRECT, "key")
        val config2 = RoutingConfig(RoutingType.DIRECT, "key")
        assertEquals(config1, config2)
    }

    @Test
    fun `RoutingConfigs with different types should not be equal`() {
        val config1 = RoutingConfig(RoutingType.DIRECT, "key")
        val config2 = RoutingConfig(RoutingType.TOPIC, "key")
        assertNotEquals(config1, config2)
    }

    @Test
    fun `RoutingConfigs with different keys should not be equal`() {
        val config1 = RoutingConfig(routingKey = "key1")
        val config2 = RoutingConfig(routingKey = "key2")
        assertNotEquals(config1, config2)
    }

    // ========== HASH CODE TESTS ==========

    @Test
    fun `RoutingConfigs with same values should have same hashCode`() {
        val config1 = RoutingConfig(RoutingType.TOPIC, "key")
        val config2 = RoutingConfig(RoutingType.TOPIC, "key")
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `RoutingConfig should work in HashSet`() {
        val config1 = RoutingConfig(RoutingType.DIRECT, "key1")
        val config2 = RoutingConfig(RoutingType.TOPIC, "key2")
        val config3 = RoutingConfig(RoutingType.DIRECT, "key1")
        val set = hashSetOf(config1, config2, config3)
        assertEquals(2, set.size)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `RoutingConfig should support toString`() {
        val config = RoutingConfig(RoutingType.TOPIC, "order.*")
        val string = config.toString()
        assertTrue(string.contains("RoutingConfig"))
        assertTrue(string.contains("TOPIC"))
        assertTrue(string.contains("order.*"))
    }

    @Test
    fun `RoutingConfig should support copy`() {
        val original = RoutingConfig(RoutingType.DIRECT, "key1")
        val copied = original.copy(routingKey = "key2")
        assertEquals(RoutingType.DIRECT, copied.routingType)
        assertEquals("key2", copied.routingKey)
        assertEquals("key1", original.routingKey)
    }

    @Test
    fun `RoutingConfig should support destructuring`() {
        val config = RoutingConfig(RoutingType.TOPIC, "user.#")
        val (type, key) = config
        assertEquals(RoutingType.TOPIC, type)
        assertEquals("user.#", key)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `direct routing to specific queue`() {
        val config = RoutingConfig(
            routingType = RoutingType.DIRECT,
            routingKey = "order-processing-queue"
        )
        assertEquals(RoutingType.DIRECT, config.routingType)
        assertEquals("order-processing-queue", config.routingKey)
    }

    @Test
    fun `topic routing with pattern matching`() {
        val config = RoutingConfig(
            routingType = RoutingType.TOPIC,
            routingKey = "orders.*.created"
        )
        assertEquals(RoutingType.TOPIC, config.routingType)
        assertEquals("orders.*.created", config.routingKey)
    }

    @Test
    fun `fanout routing to all subscribers`() {
        val config = RoutingConfig(routingType = RoutingType.FANOUT)
        assertEquals(RoutingType.FANOUT, config.routingType)
        assertNull(config.routingKey)  // Fanout doesn't need routing key
    }

    @Test
    fun `hierarchical topic routing`() {
        val configs = listOf(
            RoutingConfig(RoutingType.TOPIC, "com.example.orders.created"),
            RoutingConfig(RoutingType.TOPIC, "com.example.orders.updated"),
            RoutingConfig(RoutingType.TOPIC, "com.example.users.registered")
        )
        val orderConfigs = configs.filter {
            it.routingKey?.startsWith("com.example.orders") == true
        }
        assertEquals(2, orderConfigs.size)
    }

    @Test
    fun `wildcard routing patterns`() {
        val config = RoutingConfig(
            routingType = RoutingType.TOPIC,
            routingKey = "user.#"  // Match all user events
        )
        assertEquals("user.#", config.routingKey)
    }

    @Test
    fun `routing config map for different destinations`() {
        val routingMap = mapOf(
            "orders" to RoutingConfig(RoutingType.DIRECT, "order-queue"),
            "notifications" to RoutingConfig(RoutingType.FANOUT),
            "events" to RoutingConfig(RoutingType.TOPIC, "*.created")
        )
        assertEquals(3, routingMap.size)
        assertEquals(RoutingType.DIRECT, routingMap["orders"]?.routingType)
        assertEquals(RoutingType.FANOUT, routingMap["notifications"]?.routingType)
    }
}
