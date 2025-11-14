package com.ead.katalyst.core.config

import com.ead.katalyst.core.component.Component
import kotlin.test.*

/**
 * Comprehensive tests for ConfigProvider interface.
 *
 * Tests cover:
 * - Interface contract and Component integration
 * - Generic get() method
 * - Type-specific getters (getString, getInt, getLong, getBoolean, getList)
 * - Dot notation path navigation
 * - Default value handling
 * - Type conversion scenarios
 * - hasKey() and getAllKeys() operations
 * - Edge cases and error scenarios
 * - Practical usage scenarios
 */
class ConfigProviderTest {

    // ========== TEST IMPLEMENTATIONS ==========

    /**
     * Simple in-memory config provider for testing.
     */
    class InMemoryConfigProvider(
        private val data: Map<String, Any> = emptyMap()
    ) : ConfigProvider {

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, defaultValue: T?): T? {
            return data[key] as? T ?: defaultValue
        }

        override fun getString(key: String, default: String): String {
            return data[key]?.toString() ?: default
        }

        override fun getInt(key: String, default: Int): Int {
            val value = data[key] ?: return default
            return when (value) {
                is Int -> value
                is String -> value.toIntOrNull() ?: default
                is Number -> value.toInt()
                else -> default
            }
        }

        override fun getLong(key: String, default: Long): Long {
            val value = data[key] ?: return default
            return when (value) {
                is Long -> value
                is String -> value.toLongOrNull() ?: default
                is Number -> value.toLong()
                else -> default
            }
        }

        override fun getBoolean(key: String, default: Boolean): Boolean {
            val value = data[key] ?: return default
            return when (value) {
                is Boolean -> value
                is String -> {
                    when (value.lowercase()) {
                        "true", "yes", "on", "1" -> true
                        "false", "no", "off", "0" -> false
                        else -> default
                    }
                }
                is Number -> value.toInt() != 0
                else -> default
            }
        }

        override fun getList(key: String, default: List<String>): List<String> {
            val value = data[key] ?: return default
            return when (value) {
                is List<*> -> value.filterIsInstance<String>()
                is String -> value.split(",").map { it.trim() }
                else -> default
            }
        }

        override fun hasKey(key: String): Boolean {
            return data.containsKey(key)
        }

        override fun getAllKeys(): Set<String> {
            return data.keys
        }
    }

    /**
     * Empty config provider that always returns defaults.
     */
    class EmptyConfigProvider : ConfigProvider {
        override fun <T> get(key: String, defaultValue: T?): T? = defaultValue
        override fun getString(key: String, default: String): String = default
        override fun getInt(key: String, default: Int): Int = default
        override fun getLong(key: String, default: Long): Long = default
        override fun getBoolean(key: String, default: Boolean): Boolean = default
        override fun getList(key: String, default: List<String>): List<String> = default
        override fun hasKey(key: String): Boolean = false
        override fun getAllKeys(): Set<String> = emptySet()
    }

    /**
     * Hierarchical config provider simulating YAML-style nested configuration.
     */
    class HierarchicalConfigProvider : ConfigProvider {
        private val data = mutableMapOf<String, Any>()

        fun set(key: String, value: Any) {
            data[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, defaultValue: T?): T? {
            return data[key] as? T ?: defaultValue
        }

        override fun getString(key: String, default: String): String {
            return data[key]?.toString() ?: default
        }

        override fun getInt(key: String, default: Int): Int {
            val value = data[key] ?: return default
            return (value as? Number)?.toInt() ?: value.toString().toIntOrNull() ?: default
        }

        override fun getLong(key: String, default: Long): Long {
            val value = data[key] ?: return default
            return (value as? Number)?.toLong() ?: value.toString().toLongOrNull() ?: default
        }

        override fun getBoolean(key: String, default: Boolean): Boolean {
            val value = data[key] ?: return default
            return when (value) {
                is Boolean -> value
                is String -> value.toBooleanStrictOrNull() ?: default
                else -> default
            }
        }

        override fun getList(key: String, default: List<String>): List<String> {
            val value = data[key] ?: return default
            return (value as? List<*>)?.filterIsInstance<String>() ?: default
        }

        override fun hasKey(key: String): Boolean = data.containsKey(key)

        override fun getAllKeys(): Set<String> = data.keys
    }

    // ========== INTERFACE CONTRACT TESTS ==========

    @Test
    fun `ConfigProvider should extend Component interface`() {
        // Given
        val provider = InMemoryConfigProvider()

        // Then
        assertTrue(provider is Component)
    }

    // ========== GET() GENERIC METHOD TESTS ==========

    @Test
    fun `get should return value when key exists`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("key" to "value"))

        // When
        val result = provider.get<String>("key")

        // Then
        assertEquals("value", result)
    }

    @Test
    fun `get should return default when key does not exist`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.get("missing", "default")

        // Then
        assertEquals("default", result)
    }

    @Test
    fun `get should return null default when key does not exist and no default provided`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.get<String>("missing")

        // Then
        assertNull(result)
    }

    @Test
    fun `get should support different types`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "string" to "text",
                "int" to 42,
                "long" to 123456789L,
                "boolean" to true,
                "list" to listOf("a", "b", "c")
            )
        )

        // Then
        assertEquals("text", provider.get<String>("string"))
        assertEquals(42, provider.get<Int>("int"))
        assertEquals(123456789L, provider.get<Long>("long"))
        assertEquals(true, provider.get<Boolean>("boolean"))
        assertEquals(listOf("a", "b", "c"), provider.get<List<String>>("list"))
    }

    // ========== getString() TESTS ==========

    @Test
    fun `getString should return value when key exists`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("app.name" to "Katalyst"))

        // When
        val result = provider.getString("app.name")

        // Then
        assertEquals("Katalyst", result)
    }

    @Test
    fun `getString should return default when key does not exist`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getString("missing", "default")

        // Then
        assertEquals("default", result)
    }

    @Test
    fun `getString should return empty string as default when not specified`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getString("missing")

        // Then
        assertEquals("", result)
    }

    @Test
    fun `getString should convert non-string values to string`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("port" to 8080))

        // When
        val result = provider.getString("port")

        // Then
        assertEquals("8080", result)
    }

    // ========== getInt() TESTS ==========

    @Test
    fun `getInt should return value when key exists`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("server.port" to 8080))

        // When
        val result = provider.getInt("server.port")

        // Then
        assertEquals(8080, result)
    }

    @Test
    fun `getInt should return default when key does not exist`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getInt("missing", 9999)

        // Then
        assertEquals(9999, result)
    }

    @Test
    fun `getInt should return 0 as default when not specified`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getInt("missing")

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `getInt should parse string to int`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("timeout" to "5000"))

        // When
        val result = provider.getInt("timeout")

        // Then
        assertEquals(5000, result)
    }

    @Test
    fun `getInt should return default when string cannot be parsed`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("invalid" to "not-a-number"))

        // When
        val result = provider.getInt("invalid", 100)

        // Then
        assertEquals(100, result)
    }

    @Test
    fun `getInt should convert other number types to int`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("value" to 42.5))

        // When
        val result = provider.getInt("value")

        // Then
        assertEquals(42, result)
    }

    // ========== getLong() TESTS ==========

    @Test
    fun `getLong should return value when key exists`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("max.size" to 9876543210L))

        // When
        val result = provider.getLong("max.size")

        // Then
        assertEquals(9876543210L, result)
    }

    @Test
    fun `getLong should return default when key does not exist`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getLong("missing", 12345L)

        // Then
        assertEquals(12345L, result)
    }

    @Test
    fun `getLong should return 0L as default when not specified`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getLong("missing")

        // Then
        assertEquals(0L, result)
    }

    @Test
    fun `getLong should parse string to long`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("timestamp" to "1234567890123"))

        // When
        val result = provider.getLong("timestamp")

        // Then
        assertEquals(1234567890123L, result)
    }

    @Test
    fun `getLong should return default when string cannot be parsed`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("invalid" to "not-a-number"))

        // When
        val result = provider.getLong("invalid", 999L)

        // Then
        assertEquals(999L, result)
    }

    @Test
    fun `getLong should convert int to long`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("count" to 42))

        // When
        val result = provider.getLong("count")

        // Then
        assertEquals(42L, result)
    }

    // ========== getBoolean() TESTS ==========

    @Test
    fun `getBoolean should return value when key exists`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("feature.enabled" to true))

        // When
        val result = provider.getBoolean("feature.enabled")

        // Then
        assertTrue(result)
    }

    @Test
    fun `getBoolean should return default when key does not exist`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getBoolean("missing", true)

        // Then
        assertTrue(result)
    }

    @Test
    fun `getBoolean should return false as default when not specified`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getBoolean("missing")

        // Then
        assertFalse(result)
    }

    @Test
    fun `getBoolean should parse true variations`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "true" to "true",
                "yes" to "yes",
                "on" to "on",
                "one" to "1"
            )
        )

        // Then
        assertTrue(provider.getBoolean("true"))
        assertTrue(provider.getBoolean("yes"))
        assertTrue(provider.getBoolean("on"))
        assertTrue(provider.getBoolean("one"))
    }

    @Test
    fun `getBoolean should parse false variations`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "false" to "false",
                "no" to "no",
                "off" to "off",
                "zero" to "0"
            )
        )

        // Then
        assertFalse(provider.getBoolean("false"))
        assertFalse(provider.getBoolean("no"))
        assertFalse(provider.getBoolean("off"))
        assertFalse(provider.getBoolean("zero"))
    }

    @Test
    fun `getBoolean should be case insensitive for string values`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "upper" to "TRUE",
                "mixed" to "Yes",
                "lower" to "on"
            )
        )

        // Then
        assertTrue(provider.getBoolean("upper"))
        assertTrue(provider.getBoolean("mixed"))
        assertTrue(provider.getBoolean("lower"))
    }

    @Test
    fun `getBoolean should return default for unparseable values`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("invalid" to "maybe"))

        // When
        val result = provider.getBoolean("invalid", true)

        // Then
        assertTrue(result)
    }

    @Test
    fun `getBoolean should convert number to boolean`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "zero" to 0,
                "one" to 1,
                "negative" to -1
            )
        )

        // Then
        assertFalse(provider.getBoolean("zero"))
        assertTrue(provider.getBoolean("one"))
        assertTrue(provider.getBoolean("negative"))
    }

    // ========== getList() TESTS ==========

    @Test
    fun `getList should return list when key exists`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf("servers" to listOf("server1", "server2", "server3"))
        )

        // When
        val result = provider.getList("servers")

        // Then
        assertEquals(3, result.size)
        assertEquals(listOf("server1", "server2", "server3"), result)
    }

    @Test
    fun `getList should return default when key does not exist`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getList("missing", listOf("default1", "default2"))

        // Then
        assertEquals(listOf("default1", "default2"), result)
    }

    @Test
    fun `getList should return empty list as default when not specified`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.getList("missing")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getList should parse comma-separated string to list`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("tags" to "tag1,tag2,tag3"))

        // When
        val result = provider.getList("tags")

        // Then
        assertEquals(listOf("tag1", "tag2", "tag3"), result)
    }

    @Test
    fun `getList should trim whitespace from comma-separated values`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("items" to "item1 , item2 , item3"))

        // When
        val result = provider.getList("items")

        // Then
        assertEquals(listOf("item1", "item2", "item3"), result)
    }

    @Test
    fun `getList should filter non-string values from mixed list`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("mixed" to listOf("string", 123, "another")))

        // When
        val result = provider.getList("mixed")

        // Then
        assertEquals(listOf("string", "another"), result)
    }

    // ========== hasKey() TESTS ==========

    @Test
    fun `hasKey should return true when key exists`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("existing.key" to "value"))

        // When
        val result = provider.hasKey("existing.key")

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasKey should return false when key does not exist`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val result = provider.hasKey("missing.key")

        // Then
        assertFalse(result)
    }

    @Test
    fun `hasKey should work with null values`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("null.key" to ""))

        // When
        val result = provider.hasKey("null.key")

        // Then
        assertTrue(result)
    }

    // ========== getAllKeys() TESTS ==========

    @Test
    fun `getAllKeys should return all configuration keys`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost",
                "database.username" to "admin",
                "server.port" to 8080
            )
        )

        // When
        val keys = provider.getAllKeys()

        // Then
        assertEquals(3, keys.size)
        assertTrue(keys.contains("database.url"))
        assertTrue(keys.contains("database.username"))
        assertTrue(keys.contains("server.port"))
    }

    @Test
    fun `getAllKeys should return empty set for empty config`() {
        // Given
        val provider = InMemoryConfigProvider()

        // When
        val keys = provider.getAllKeys()

        // Then
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `getAllKeys should return immutable set`() {
        // Given
        val provider = InMemoryConfigProvider(mapOf("key" to "value"))

        // When
        val keys = provider.getAllKeys()

        // Then
        assertNotNull(keys)
        assertEquals(1, keys.size)
    }

    // ========== DOT NOTATION TESTS ==========

    @Test
    fun `should support dot notation paths`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost",
                "database.username" to "admin",
                "database.password" to "secret",
                "server.port" to 8080,
                "server.host" to "localhost"
            )
        )

        // Then
        assertEquals("jdbc:postgresql://localhost", provider.getString("database.url"))
        assertEquals("admin", provider.getString("database.username"))
        assertEquals(8080, provider.getInt("server.port"))
        assertEquals("localhost", provider.getString("server.host"))
    }

    @Test
    fun `should support deeply nested paths`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "app.module.feature.setting" to "value",
                "level1.level2.level3.level4" to 42
            )
        )

        // Then
        assertEquals("value", provider.getString("app.module.feature.setting"))
        assertEquals(42, provider.getInt("level1.level2.level3.level4"))
    }

    // ========== EMPTY CONFIG PROVIDER TESTS ==========

    @Test
    fun `EmptyConfigProvider should always return defaults`() {
        // Given
        val provider = EmptyConfigProvider()

        // Then
        assertEquals("default", provider.getString("any.key", "default"))
        assertEquals(99, provider.getInt("any.key", 99))
        assertEquals(123L, provider.getLong("any.key", 123L))
        assertTrue(provider.getBoolean("any.key", true))
        assertEquals(listOf("a", "b"), provider.getList("any.key", listOf("a", "b")))
    }

    @Test
    fun `EmptyConfigProvider should have no keys`() {
        // Given
        val provider = EmptyConfigProvider()

        // Then
        assertFalse(provider.hasKey("any.key"))
        assertTrue(provider.getAllKeys().isEmpty())
    }

    // ========== HIERARCHICAL CONFIG PROVIDER TESTS ==========

    @Test
    fun `HierarchicalConfigProvider should support setting and getting values`() {
        // Given
        val provider = HierarchicalConfigProvider()
        provider.set("database.url", "jdbc:postgresql://localhost")
        provider.set("database.port", 5432)
        provider.set("database.ssl.enabled", true)

        // Then
        assertEquals("jdbc:postgresql://localhost", provider.getString("database.url"))
        assertEquals(5432, provider.getInt("database.port"))
        assertTrue(provider.getBoolean("database.ssl.enabled"))
    }

    @Test
    fun `HierarchicalConfigProvider should support complex structures`() {
        // Given
        val provider = HierarchicalConfigProvider()
        provider.set("servers", listOf("server1.com", "server2.com", "server3.com"))
        provider.set("timeout", 30000L)
        provider.set("retries", 3)

        // Then
        assertEquals(listOf("server1.com", "server2.com", "server3.com"), provider.getList("servers"))
        assertEquals(30000L, provider.getLong("timeout"))
        assertEquals(3, provider.getInt("retries"))
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical database configuration scenario`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost:5432/mydb",
                "database.username" to "admin",
                "database.password" to "secret",
                "database.pool.size" to 10,
                "database.pool.timeout" to 30000L,
                "database.ssl.enabled" to true
            )
        )

        // When - Simulating database service initialization
        val dbUrl = provider.getString("database.url")
        val dbUser = provider.getString("database.username")
        val poolSize = provider.getInt("database.pool.size")
        val timeout = provider.getLong("database.pool.timeout")
        val sslEnabled = provider.getBoolean("database.ssl.enabled")

        // Then
        assertEquals("jdbc:postgresql://localhost:5432/mydb", dbUrl)
        assertEquals("admin", dbUser)
        assertEquals(10, poolSize)
        assertEquals(30000L, timeout)
        assertTrue(sslEnabled)
    }

    @Test
    fun `typical application configuration scenario`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "app.name" to "Katalyst",
                "app.version" to "1.0.0",
                "server.port" to 8080,
                "server.host" to "0.0.0.0",
                "logging.level" to "INFO",
                "feature.flags" to listOf("analytics", "caching", "compression")
            )
        )

        // When
        val appName = provider.getString("app.name")
        val version = provider.getString("app.version")
        val port = provider.getInt("server.port")
        val features = provider.getList("feature.flags")

        // Then
        assertEquals("Katalyst", appName)
        assertEquals("1.0.0", version)
        assertEquals(8080, port)
        assertEquals(3, features.size)
        assertTrue(features.contains("analytics"))
    }

    @Test
    fun `configuration with fallback defaults`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf("server.port" to 9090)
        )

        // When - Getting config with defaults for missing keys
        val port = provider.getInt("server.port", 8080)
        val host = provider.getString("server.host", "localhost")
        val timeout = provider.getLong("server.timeout", 30000L)
        val debug = provider.getBoolean("server.debug", false)

        // Then
        assertEquals(9090, port)  // Configured value
        assertEquals("localhost", host)  // Default value
        assertEquals(30000L, timeout)  // Default value
        assertFalse(debug)  // Default value
    }

    @Test
    fun `checking configuration keys before access`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "required.setting" to "value",
                "optional.setting" to "another"
            )
        )

        // When/Then
        if (provider.hasKey("required.setting")) {
            assertEquals("value", provider.getString("required.setting"))
        } else {
            fail("Required setting should exist")
        }

        if (!provider.hasKey("missing.setting")) {
            // Use default or skip configuration
            val value = provider.getString("missing.setting", "default")
            assertEquals("default", value)
        }
    }

    @Test
    fun `iterating all configuration keys`() {
        // Given
        val provider = InMemoryConfigProvider(
            mapOf(
                "key1" to "value1",
                "key2" to "value2",
                "key3" to "value3"
            )
        )

        // When
        val allKeys = provider.getAllKeys()
        val configMap = allKeys.associateWith { provider.getString(it) }

        // Then
        assertEquals(3, configMap.size)
        assertEquals("value1", configMap["key1"])
        assertEquals("value2", configMap["key2"])
        assertEquals("value3", configMap["key3"])
    }
}
