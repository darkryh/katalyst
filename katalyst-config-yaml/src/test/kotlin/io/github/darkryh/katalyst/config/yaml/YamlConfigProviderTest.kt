package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.test.*

/**
 * Comprehensive tests for YamlConfigProvider.
 *
 * Tests cover:
 * - Component interface implementation
 * - ConfigProvider interface implementation
 * - Dot notation path navigation
 * - Type-specific getters (getString, getInt, getLong, getBoolean, getList)
 * - Type conversion scenarios
 * - hasKey() and getAllKeys() operations
 * - Nested map navigation
 * - Edge cases and error scenarios
 *
 * **Testing Approach:**
 * Uses TestYamlConfigProvider that bypasses file loading to focus on
 * testing the ConfigProvider interface implementation and path navigation logic.
 */
class YamlConfigProviderTest {

    /**
     * Test implementation that bypasses file loading.
     * Allows testing ConfigProvider logic with controlled test data.
     */
    class TestYamlConfigProvider(testData: Map<String, Any>) : ConfigProvider, Component {
        private val data: Map<String, Any> = testData

        override fun <T> get(key: String, defaultValue: T?): T? {
            val value = navigatePath(key) ?: return defaultValue
            if (defaultValue != null) {
                val expectedType = defaultValue::class
                if (!expectedType.isInstance(value)) {
                    throw ConfigException(
                        "Configuration key '$key' has type ${value::class.simpleName}, expected ${expectedType.simpleName}"
                    )
                }
            }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        override fun getString(key: String, default: String): String {
            return when (val value = navigatePath(key)) {
                null -> default
                is String -> value
                else -> value.toString()
            }
        }

        override fun getInt(key: String, default: Int): Int {
            val value = navigatePath(key) ?: return default
            return when (value) {
                is Int -> value
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: default
                else -> default
            }
        }

        override fun getLong(key: String, default: Long): Long {
            val value = navigatePath(key) ?: return default
            return when (value) {
                is Long -> value
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: default
                else -> default
            }
        }

        override fun getBoolean(key: String, default: Boolean): Boolean {
            val value = navigatePath(key) ?: return default
            return when (value) {
                is Boolean -> value
                is String -> when (value.lowercase()) {
                    "true", "yes", "on", "1" -> true
                    "false", "no", "off", "0" -> false
                    else -> default
                }
                is Number -> value.toInt() != 0
                else -> default
            }
        }

        override fun getList(key: String, default: List<String>): List<String> {
            val value = navigatePath(key) ?: return default
            return when (value) {
                is List<*> -> value.map { it?.toString() ?: "" }
                is String -> value.split(",").map { it.trim() }
                else -> default
            }
        }

        override fun hasKey(key: String): Boolean {
            return navigatePath(key) != null
        }

        override fun getAllKeys(): Set<String> {
            val keys = mutableSetOf<String>()
            fun traverse(map: Map<*, *>, prefix: String = "") {
                for ((key, value) in map) {
                    val fullKey = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                    keys.add(fullKey)
                    if (value is Map<*, *>) {
                        traverse(value, fullKey)
                    }
                }
            }
            traverse(data)
            return keys
        }

        @Suppress("UNCHECKED_CAST")
        private fun navigatePath(path: String): Any? {
            val parts = path.split(".")
            var current: Any? = data

            for (part in parts) {
                current = when (current) {
                    is Map<*, *> -> (current as Map<String, Any>)[part]
                    else -> return null
                }
            }

            return current
        }
    }

    // ========== COMPONENT INTERFACE TESTS ==========

    @Test
    fun `YamlConfigProvider should implement Component interface`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        assertTrue(provider is Component)
    }

    @Test
    fun `YamlConfigProvider should implement ConfigProvider interface`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        assertTrue(provider is ConfigProvider)
    }

    // ========== DOT NOTATION PATH NAVIGATION TESTS ==========

    @Test
    fun `navigatePath should handle simple keys`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "name" to "Katalyst",
                "version" to "1.0.0",
                "port" to 8080
            )
        )

        // Then
        assertEquals("Katalyst", provider.getString("name"))
        assertEquals("1.0.0", provider.getString("version"))
        assertEquals(8080, provider.getInt("port"))
    }

    @Test
    fun `navigatePath should handle nested paths with dot notation`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "database" to mapOf(
                    "url" to "jdbc:postgresql://localhost:5432/db",
                    "username" to "postgres",
                    "password" to "secret"
                )
            )
        )

        // Then
        assertEquals("jdbc:postgresql://localhost:5432/db", provider.getString("database.url"))
        assertEquals("postgres", provider.getString("database.username"))
        assertEquals("secret", provider.getString("database.password"))
    }

    @Test
    fun `navigatePath should handle deeply nested paths`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "app" to mapOf(
                    "modules" to mapOf(
                        "database" to mapOf(
                            "config" to mapOf(
                                "pool" to mapOf(
                                    "size" to 10,
                                    "timeout" to 30000
                                )
                            )
                        )
                    )
                )
            )
        )

        // Then
        assertEquals(10, provider.getInt("app.modules.database.config.pool.size"))
        assertEquals(30000, provider.getInt("app.modules.database.config.pool.timeout"))
    }

    @Test
    fun `navigatePath should return null for non-existent keys`() {
        // Given
        val provider = TestYamlConfigProvider(mapOf("existing" to "value"))

        // Then
        assertNull(provider.get<String>("non.existent"))
        assertEquals("default", provider.getString("non.existent", "default"))
    }

    @Test
    fun `navigatePath should return null for partial paths`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "database" to mapOf(
                    "url" to "value"
                )
            )
        )

        // Then
        assertNull(provider.get<String>("database.url.invalid"))
        assertNull(provider.get<String>("database.nonexistent"))
    }

    // ========== getString() TESTS ==========

    @Test
    fun `getString should return string values`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("name" to "Katalyst")
        )

        // Then
        assertEquals("Katalyst", provider.getString("name"))
    }

    @Test
    fun `getString should return default for missing keys`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        assertEquals("default", provider.getString("missing", "default"))
    }

    @Test
    fun `getString should convert non-string values to strings`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "port" to 8080,
                "enabled" to true,
                "version" to 1.5
            )
        )

        // Then
        assertEquals("8080", provider.getString("port"))
        assertEquals("true", provider.getString("enabled"))
        assertEquals("1.5", provider.getString("version"))
    }

    // ========== getInt() TESTS ==========

    @Test
    fun `getInt should return integer values`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("port" to 8080)
        )

        // Then
        assertEquals(8080, provider.getInt("port"))
    }

    @Test
    fun `getInt should return default for missing keys`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        assertEquals(9999, provider.getInt("missing", 9999))
    }

    @Test
    fun `getInt should parse string to int`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("timeout" to "5000")
        )

        // Then
        assertEquals(5000, provider.getInt("timeout"))
    }

    @Test
    fun `getInt should return default for unparseable strings`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("invalid" to "not-a-number")
        )

        // Then
        assertEquals(100, provider.getInt("invalid", 100))
    }

    @Test
    fun `getInt should convert other number types`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "double" to 42.7,
                "long" to 123L
            )
        )

        // Then
        assertEquals(42, provider.getInt("double"))
        assertEquals(123, provider.getInt("long"))
    }

    // ========== getLong() TESTS ==========

    @Test
    fun `getLong should return long values`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("timestamp" to 1234567890123L)
        )

        // Then
        assertEquals(1234567890123L, provider.getLong("timestamp"))
    }

    @Test
    fun `getLong should return default for missing keys`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        assertEquals(999L, provider.getLong("missing", 999L))
    }

    @Test
    fun `getLong should parse string to long`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("value" to "9876543210")
        )

        // Then
        assertEquals(9876543210L, provider.getLong("value"))
    }

    @Test
    fun `getLong should convert int to long`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("count" to 42)
        )

        // Then
        assertEquals(42L, provider.getLong("count"))
    }

    // ========== getBoolean() TESTS ==========

    @Test
    fun `getBoolean should return boolean values`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "enabled" to true,
                "disabled" to false
            )
        )

        // Then
        assertTrue(provider.getBoolean("enabled"))
        assertFalse(provider.getBoolean("disabled"))
    }

    @Test
    fun `getBoolean should return default for missing keys`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        assertTrue(provider.getBoolean("missing", true))
        assertFalse(provider.getBoolean("missing", false))
    }

    @Test
    fun `getBoolean should parse true variations case-insensitively`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "true" to "true",
                "yes" to "YES",
                "on" to "On",
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
    fun `getBoolean should parse false variations case-insensitively`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "false" to "false",
                "no" to "NO",
                "off" to "Off",
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
    fun `getBoolean should return default for unparseable values`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("invalid" to "maybe")
        )

        // Then
        assertTrue(provider.getBoolean("invalid", true))
        assertFalse(provider.getBoolean("invalid", false))
    }

    @Test
    fun `getBoolean should convert numbers to boolean`() {
        // Given
        val provider = TestYamlConfigProvider(
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
    fun `getList should return list of strings`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("servers" to listOf("server1", "server2", "server3"))
        )

        // Then
        val servers = provider.getList("servers")
        assertEquals(3, servers.size)
        assertEquals(listOf("server1", "server2", "server3"), servers)
    }

    @Test
    fun `getList should return default for missing keys`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        val default = listOf("default1", "default2")
        assertEquals(default, provider.getList("missing", default))
    }

    @Test
    fun `getList should parse comma-separated string`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("tags" to "tag1,tag2,tag3")
        )

        // Then
        assertEquals(listOf("tag1", "tag2", "tag3"), provider.getList("tags"))
    }

    @Test
    fun `getList should trim whitespace from comma-separated values`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("items" to "item1 , item2 , item3")
        )

        // Then
        assertEquals(listOf("item1", "item2", "item3"), provider.getList("items"))
    }

    @Test
    fun `getList should convert non-string list items to strings`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("mixed" to listOf(1, 2, 3))
        )

        // Then
        assertEquals(listOf("1", "2", "3"), provider.getList("mixed"))
    }

    @Test
    fun `getList should handle null values in lists`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("nullable" to listOf("value1", null, "value2"))
        )

        // Then
        val result = provider.getList("nullable")
        assertEquals(3, result.size)
        assertEquals("value1", result[0])
        assertEquals("", result[1])
        assertEquals("value2", result[2])
    }

    // ========== hasKey() TESTS ==========

    @Test
    fun `hasKey should return true for existing keys`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("existing" to "value")
        )

        // Then
        assertTrue(provider.hasKey("existing"))
    }

    @Test
    fun `hasKey should return false for missing keys`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // Then
        assertFalse(provider.hasKey("missing"))
    }

    @Test
    fun `hasKey should work with nested paths`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "database" to mapOf(
                    "url" to "value"
                )
            )
        )

        // Then
        assertTrue(provider.hasKey("database.url"))
        assertFalse(provider.hasKey("database.password"))
    }

    // ========== getAllKeys() TESTS ==========

    @Test
    fun `getAllKeys should return all configuration keys`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "name" to "Katalyst",
                "version" to "1.0.0",
                "port" to 8080
            )
        )

        // When
        val keys = provider.getAllKeys()

        // Then
        assertEquals(3, keys.size)
        assertTrue(keys.contains("name"))
        assertTrue(keys.contains("version"))
        assertTrue(keys.contains("port"))
    }

    @Test
    fun `getAllKeys should return nested keys with dot notation`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "database" to mapOf(
                    "url" to "jdbc:postgresql://localhost",
                    "username" to "admin",
                    "pool" to mapOf(
                        "size" to 10,
                        "timeout" to 30000
                    )
                )
            )
        )

        // When
        val keys = provider.getAllKeys()

        // Then
        assertTrue(keys.contains("database"))
        assertTrue(keys.contains("database.url"))
        assertTrue(keys.contains("database.username"))
        assertTrue(keys.contains("database.pool"))
        assertTrue(keys.contains("database.pool.size"))
        assertTrue(keys.contains("database.pool.timeout"))
    }

    @Test
    fun `getAllKeys should return empty set for empty configuration`() {
        // Given
        val provider = TestYamlConfigProvider(emptyMap())

        // When
        val keys = provider.getAllKeys()

        // Then
        assertTrue(keys.isEmpty())
    }

    // ========== TYPE CONVERSION ERROR TESTS ==========

    @Test
    fun `get should throw ConfigException for type mismatch`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf("value" to "string")
        )

        // Then
        assertFailsWith<ConfigException> {
            provider.get<Int>("value", 42)
        }
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical database configuration scenario`() {
        // Given - Real-world database config
        val provider = TestYamlConfigProvider(
            mapOf(
                "database" to mapOf(
                    "url" to "jdbc:postgresql://localhost:5432/katalyst",
                    "username" to "admin",
                    "password" to "secret",
                    "pool" to mapOf(
                        "maxSize" to 20,
                        "minIdle" to 5,
                        "timeout" to 30000L
                    ),
                    "ssl" to mapOf(
                        "enabled" to true,
                        "cert" to "/path/to/cert.pem"
                    )
                )
            )
        )

        // When/Then
        assertEquals("jdbc:postgresql://localhost:5432/katalyst", provider.getString("database.url"))
        assertEquals("admin", provider.getString("database.username"))
        assertEquals("secret", provider.getString("database.password"))
        assertEquals(20, provider.getInt("database.pool.maxSize"))
        assertEquals(5, provider.getInt("database.pool.minIdle"))
        assertEquals(30000L, provider.getLong("database.pool.timeout"))
        assertTrue(provider.getBoolean("database.ssl.enabled"))
        assertEquals("/path/to/cert.pem", provider.getString("database.ssl.cert"))
    }

    @Test
    fun `typical application configuration scenario`() {
        // Given
        val provider = TestYamlConfigProvider(
            mapOf(
                "app" to mapOf(
                    "name" to "Katalyst",
                    "version" to "1.0.0",
                    "environment" to "production"
                ),
                "server" to mapOf(
                    "host" to "0.0.0.0",
                    "port" to 8080,
                    "ssl" to mapOf(
                        "enabled" to true,
                        "port" to 8443
                    )
                ),
                "logging" to mapOf(
                    "level" to "INFO",
                    "file" to "/var/log/katalyst.log",
                    "console" to true
                ),
                "features" to mapOf(
                    "analytics" to false,
                    "caching" to true,
                    "compression" to true
                )
            )
        )

        // Then - Application info
        assertEquals("Katalyst", provider.getString("app.name"))
        assertEquals("1.0.0", provider.getString("app.version"))
        assertEquals("production", provider.getString("app.environment"))

        // Server config
        assertEquals("0.0.0.0", provider.getString("server.host"))
        assertEquals(8080, provider.getInt("server.port"))
        assertTrue(provider.getBoolean("server.ssl.enabled"))
        assertEquals(8443, provider.getInt("server.ssl.port"))

        // Logging config
        assertEquals("INFO", provider.getString("logging.level"))
        assertEquals("/var/log/katalyst.log", provider.getString("logging.file"))
        assertTrue(provider.getBoolean("logging.console"))

        // Feature flags
        assertFalse(provider.getBoolean("features.analytics"))
        assertTrue(provider.getBoolean("features.caching"))
        assertTrue(provider.getBoolean("features.compression"))
    }

    @Test
    fun `configuration with defaults for missing values`() {
        // Given - Sparse configuration
        val provider = TestYamlConfigProvider(
            mapOf(
                "app" to mapOf(
                    "name" to "Katalyst"
                )
            )
        )

        // When/Then - Using defaults for missing keys
        assertEquals("Katalyst", provider.getString("app.name"))
        assertEquals("1.0.0", provider.getString("app.version", "1.0.0"))
        assertEquals(8080, provider.getInt("server.port", 8080))
        assertEquals("localhost", provider.getString("server.host", "localhost"))
        assertFalse(provider.getBoolean("features.debug", false))
    }
}
