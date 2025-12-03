package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import java.time.Duration
import kotlin.test.*

/**
 * Comprehensive tests for ConfigLoaders utility object.
 *
 * Tests cover:
 * - loadRequiredString() - required string loading and validation
 * - loadOptionalString() - optional string with defaults
 * - loadRequiredInt() - required integer loading with validation
 * - loadOptionalInt() - optional integer with defaults
 * - loadRequiredLong() - required long loading with validation
 * - loadOptionalLong() - optional long with defaults
 * - loadDuration() - duration loading from milliseconds
 * - loadList() - list loading
 * - loadIntRange() - range validation
 * - loadEnum() - enum parsing
 * - loadBoolean() - boolean loading
 * - validateRequiredKeys() - batch validation
 * - Edge cases and error scenarios
 */
class ConfigLoadersTest {

    // ========== TEST CONFIG PROVIDER ==========

    class TestConfigProvider(private val data: Map<String, Any> = emptyMap()) : ConfigProvider, Component {
        override fun <T> get(key: String, defaultValue: T?): T? {
            @Suppress("UNCHECKED_CAST")
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
                is String -> value.toBooleanStrictOrNull() ?: default
                else -> default
            }
        }

        override fun getList(key: String, default: List<String>): List<String> {
            val value = data[key] ?: return default
            @Suppress("UNCHECKED_CAST")
            return when (value) {
                is List<*> -> value.map { it.toString() }
                else -> default
            }
        }

        override fun hasKey(key: String): Boolean = data.containsKey(key)

        override fun getAllKeys(): Set<String> = data.keys
    }

    // ========== loadRequiredString() TESTS ==========

    @Test
    fun `loadRequiredString should return value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("key" to "value"))

        // When
        val result = ConfigLoaders.loadRequiredString(provider, "key")

        // Then
        assertEquals("value", result)
    }

    @Test
    fun `loadRequiredString should throw when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.loadRequiredString(provider, "missing")
        }
        assertTrue(exception.message?.contains("Required configuration key") == true)
        assertTrue(exception.message?.contains("missing") == true)
    }

    @Test
    fun `loadRequiredString should throw when value is blank`() {
        // Given
        val provider = TestConfigProvider(mapOf("blank" to "   "))

        // Then
        assertFailsWith<ConfigException> {
            ConfigLoaders.loadRequiredString(provider, "blank")
        }
    }

    @Test
    fun `loadRequiredString should throw when value is empty`() {
        // Given
        val provider = TestConfigProvider(mapOf("empty" to ""))

        // Then
        assertFailsWith<ConfigException> {
            ConfigLoaders.loadRequiredString(provider, "empty")
        }
    }

    // ========== loadOptionalString() TESTS ==========

    @Test
    fun `loadOptionalString should return value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("key" to "value"))

        // When
        val result = ConfigLoaders.loadOptionalString(provider, "key")

        // Then
        assertEquals("value", result)
    }

    @Test
    fun `loadOptionalString should return default when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadOptionalString(provider, "missing", "default")

        // Then
        assertEquals("default", result)
    }

    @Test
    fun `loadOptionalString should return empty string as default when not specified`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadOptionalString(provider, "missing")

        // Then
        assertEquals("", result)
    }

    // ========== loadRequiredInt() TESTS ==========

    @Test
    fun `loadRequiredInt should return value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("port" to "8080"))

        // When
        val result = ConfigLoaders.loadRequiredInt(provider, "port")

        // Then
        assertEquals(8080, result)
    }

    @Test
    fun `loadRequiredInt should throw when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.loadRequiredInt(provider, "missing")
        }
        assertTrue(exception.message?.contains("must be a valid integer") == true)
    }

    @Test
    fun `loadRequiredInt should throw when value is not a valid integer`() {
        // Given
        val provider = TestConfigProvider(mapOf("invalid" to "not-a-number"))

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.loadRequiredInt(provider, "invalid")
        }
        assertTrue(exception.message?.contains("must be a valid integer") == true)
        assertTrue(exception.message?.contains("not-a-number") == true)
    }

    @Test
    fun `loadRequiredInt should parse negative integers`() {
        // Given
        val provider = TestConfigProvider(mapOf("negative" to "-100"))

        // When
        val result = ConfigLoaders.loadRequiredInt(provider, "negative")

        // Then
        assertEquals(-100, result)
    }

    @Test
    fun `loadRequiredInt should parse zero`() {
        // Given
        val provider = TestConfigProvider(mapOf("zero" to "0"))

        // When
        val result = ConfigLoaders.loadRequiredInt(provider, "zero")

        // Then
        assertEquals(0, result)
    }

    // ========== loadOptionalInt() TESTS ==========

    @Test
    fun `loadOptionalInt should return value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("port" to 8080))

        // When
        val result = ConfigLoaders.loadOptionalInt(provider, "port")

        // Then
        assertEquals(8080, result)
    }

    @Test
    fun `loadOptionalInt should return default when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadOptionalInt(provider, "missing", 9999)

        // Then
        assertEquals(9999, result)
    }

    @Test
    fun `loadOptionalInt should return 0 as default when not specified`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadOptionalInt(provider, "missing")

        // Then
        assertEquals(0, result)
    }

    // ========== loadRequiredLong() TESTS ==========

    @Test
    fun `loadRequiredLong should return value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("timestamp" to "1234567890123"))

        // When
        val result = ConfigLoaders.loadRequiredLong(provider, "timestamp")

        // Then
        assertEquals(1234567890123L, result)
    }

    @Test
    fun `loadRequiredLong should throw when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.loadRequiredLong(provider, "missing")
        }
        assertTrue(exception.message?.contains("must be a valid long") == true)
    }

    @Test
    fun `loadRequiredLong should throw when value is not a valid long`() {
        // Given
        val provider = TestConfigProvider(mapOf("invalid" to "not-a-number"))

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.loadRequiredLong(provider, "invalid")
        }
        assertTrue(exception.message?.contains("must be a valid long") == true)
    }

    // ========== loadOptionalLong() TESTS ==========

    @Test
    fun `loadOptionalLong should return value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("timestamp" to 1234567890123L))

        // When
        val result = ConfigLoaders.loadOptionalLong(provider, "timestamp")

        // Then
        assertEquals(1234567890123L, result)
    }

    @Test
    fun `loadOptionalLong should return default when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadOptionalLong(provider, "missing", 999L)

        // Then
        assertEquals(999L, result)
    }

    @Test
    fun `loadOptionalLong should return 0L as default when not specified`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadOptionalLong(provider, "missing")

        // Then
        assertEquals(0L, result)
    }

    // ========== loadDuration() TESTS ==========

    @Test
    fun `loadDuration should return duration from milliseconds`() {
        // Given
        val provider = TestConfigProvider(mapOf("timeout" to 5000L))

        // When
        val result = ConfigLoaders.loadDuration(provider, "timeout", Duration.ofSeconds(1))

        // Then
        assertEquals(Duration.ofMillis(5000), result)
        assertEquals(5, result.toSeconds())
    }

    @Test
    fun `loadDuration should return default when key is missing`() {
        // Given
        val provider = TestConfigProvider()
        val default = Duration.ofSeconds(30)

        // When
        val result = ConfigLoaders.loadDuration(provider, "missing", default)

        // Then
        assertEquals(default, result)
    }

    @Test
    fun `loadDuration should return default when value is zero`() {
        // Given
        val provider = TestConfigProvider(mapOf("timeout" to 0L))
        val default = Duration.ofSeconds(30)

        // When
        val result = ConfigLoaders.loadDuration(provider, "timeout", default)

        // Then
        assertEquals(default, result)
    }

    @Test
    fun `loadDuration should return default when value is negative`() {
        // Given
        val provider = TestConfigProvider(mapOf("timeout" to -1L))
        val default = Duration.ofSeconds(30)

        // When
        val result = ConfigLoaders.loadDuration(provider, "timeout", default)

        // Then
        assertEquals(default, result)
    }

    @Test
    fun `loadDuration should handle large durations`() {
        // Given
        val provider = TestConfigProvider(mapOf("timeout" to 86400000L))  // 24 hours

        // When
        val result = ConfigLoaders.loadDuration(provider, "timeout", Duration.ofSeconds(1))

        // Then
        assertEquals(Duration.ofDays(1), result)
    }

    // ========== loadList() TESTS ==========

    @Test
    fun `loadList should return list when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("servers" to listOf("server1", "server2", "server3")))

        // When
        val result = ConfigLoaders.loadList(provider, "servers")

        // Then
        assertEquals(listOf("server1", "server2", "server3"), result)
    }

    @Test
    fun `loadList should return default when key is missing`() {
        // Given
        val provider = TestConfigProvider()
        val default = listOf("default1", "default2")

        // When
        val result = ConfigLoaders.loadList(provider, "missing", default)

        // Then
        assertEquals(default, result)
    }

    @Test
    fun `loadList should return empty list as default when not specified`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadList(provider, "missing")

        // Then
        assertTrue(result.isEmpty())
    }

    // ========== loadIntRange() TESTS ==========

    @Test
    fun `loadIntRange should return valid range`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "min" to 10,
                "max" to 100
            )
        )

        // When
        val (min, max) = ConfigLoaders.loadIntRange(provider, "min", "max")

        // Then
        assertEquals(10, min)
        assertEquals(100, max)
    }

    @Test
    fun `loadIntRange should use defaults when keys are missing`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val (min, max) = ConfigLoaders.loadIntRange(provider, "min", "max", 5, 50)

        // Then
        assertEquals(5, min)
        assertEquals(50, max)
    }

    @Test
    fun `loadIntRange should throw when min greater than max`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "min" to 100,
                "max" to 10
            )
        )

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.loadIntRange(provider, "min", "max")
        }
        assertTrue(exception.message?.contains("range invalid") == true)
        assertTrue(exception.message?.contains("100") == true)
        assertTrue(exception.message?.contains("10") == true)
    }

    @Test
    fun `loadIntRange should allow equal min and max`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "min" to 50,
                "max" to 50
            )
        )

        // When
        val (min, max) = ConfigLoaders.loadIntRange(provider, "min", "max")

        // Then
        assertEquals(50, min)
        assertEquals(50, max)
    }

    @Test
    fun `loadIntRange should use Int MAX_VALUE as default max`() {
        // Given
        val provider = TestConfigProvider(mapOf("min" to 0))

        // When
        val (_, max) = ConfigLoaders.loadIntRange(provider, "min", "max")

        // Then
        assertEquals(Int.MAX_VALUE, max)
    }

    // ========== loadEnum() TESTS ==========

    enum class TestEnvironment {
        DEVELOPMENT, STAGING, PRODUCTION
    }

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    @Test
    fun `loadEnum should return enum value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("env" to "production"))

        // When
        val result = ConfigLoaders.loadEnum(provider, "env", TestEnvironment.DEVELOPMENT)

        // Then
        assertEquals(TestEnvironment.PRODUCTION, result)
    }

    @Test
    fun `loadEnum should be case insensitive (uppercase)`() {
        // Given
        val provider = TestConfigProvider(mapOf("env" to "PRODUCTION"))

        // When
        val result = ConfigLoaders.loadEnum(provider, "env", TestEnvironment.DEVELOPMENT)

        // Then
        assertEquals(TestEnvironment.PRODUCTION, result)
    }

    @Test
    fun `loadEnum should be case insensitive (lowercase)`() {
        // Given
        val provider = TestConfigProvider(mapOf("log.level" to "debug"))

        // When
        val result = ConfigLoaders.loadEnum(provider, "log.level", LogLevel.INFO)

        // Then
        assertEquals(LogLevel.DEBUG, result)
    }

    @Test
    fun `loadEnum should return default when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadEnum(provider, "missing", TestEnvironment.DEVELOPMENT)

        // Then
        assertEquals(TestEnvironment.DEVELOPMENT, result)
    }

    @Test
    fun `loadEnum should return default when value is blank`() {
        // Given
        val provider = TestConfigProvider(mapOf("env" to ""))

        // When
        val result = ConfigLoaders.loadEnum(provider, "env", TestEnvironment.DEVELOPMENT)

        // Then
        assertEquals(TestEnvironment.DEVELOPMENT, result)
    }

    @Test
    fun `loadEnum should throw when value is invalid`() {
        // Given
        val provider = TestConfigProvider(mapOf("env" to "invalid"))

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.loadEnum(provider, "env", TestEnvironment.DEVELOPMENT)
        }
        assertTrue(exception.message?.contains("must be one of") == true)
        assertTrue(exception.message?.contains("invalid") == true)
    }

    // ========== loadBoolean() TESTS ==========

    @Test
    fun `loadBoolean should return boolean value when key exists`() {
        // Given
        val provider = TestConfigProvider(mapOf("enabled" to true))

        // When
        val result = ConfigLoaders.loadBoolean(provider, "enabled")

        // Then
        assertTrue(result)
    }

    @Test
    fun `loadBoolean should return default when key is missing`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadBoolean(provider, "missing", true)

        // Then
        assertTrue(result)
    }

    @Test
    fun `loadBoolean should return false as default when not specified`() {
        // Given
        val provider = TestConfigProvider()

        // When
        val result = ConfigLoaders.loadBoolean(provider, "missing")

        // Then
        assertFalse(result)
    }

    // ========== validateRequiredKeys() TESTS ==========

    @Test
    fun `validateRequiredKeys should pass when all keys exist`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "key1" to "value1",
                "key2" to "value2",
                "key3" to "value3"
            )
        )

        // When/Then - Should not throw
        ConfigLoaders.validateRequiredKeys(provider, "key1", "key2", "key3")
    }

    @Test
    fun `validateRequiredKeys should throw when one key is missing`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "key1" to "value1",
                "key2" to "value2"
            )
        )

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.validateRequiredKeys(provider, "key1", "key2", "key3")
        }
        assertTrue(exception.message?.contains("Missing required configuration keys") == true)
        assertTrue(exception.message?.contains("key3") == true)
    }

    @Test
    fun `validateRequiredKeys should throw when multiple keys are missing`() {
        // Given
        val provider = TestConfigProvider(mapOf("key1" to "value1"))

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigLoaders.validateRequiredKeys(provider, "key1", "key2", "key3")
        }
        assertTrue(exception.message?.contains("key2") == true)
        assertTrue(exception.message?.contains("key3") == true)
    }

    @Test
    fun `validateRequiredKeys should pass with no required keys`() {
        // Given
        val provider = TestConfigProvider()

        // When/Then - Should not throw
        ConfigLoaders.validateRequiredKeys(provider)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical database configuration loading`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost:5432/db",
                "database.username" to "admin",
                "database.password" to "secret",
                "database.pool.maxSize" to "20",
                "database.pool.minIdle" to "5",
                "database.timeout" to "30000"
            )
        )

        // When
        val url = ConfigLoaders.loadRequiredString(provider, "database.url")
        val username = ConfigLoaders.loadRequiredString(provider, "database.username")
        val password = ConfigLoaders.loadOptionalString(provider, "database.password")
        val maxSize = ConfigLoaders.loadRequiredInt(provider, "database.pool.maxSize")
        val minIdle = ConfigLoaders.loadOptionalInt(provider, "database.pool.minIdle", 5)
        val timeout = ConfigLoaders.loadDuration(provider, "database.timeout", Duration.ofSeconds(30))

        // Then
        assertEquals("jdbc:postgresql://localhost:5432/db", url)
        assertEquals("admin", username)
        assertEquals("secret", password)
        assertEquals(20, maxSize)
        assertEquals(5, minIdle)
        assertEquals(Duration.ofSeconds(30), timeout)
    }

    @Test
    fun `typical JWT configuration loading`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "jwt.secret" to "my-secret-key-minimum-32-chars",
                "jwt.issuer" to "katalyst",
                "jwt.expiresIn" to "3600000",
                "jwt.algorithm" to "HS256"
            )
        )

        // When
        val secret = ConfigLoaders.loadRequiredString(provider, "jwt.secret")
        val issuer = ConfigLoaders.loadRequiredString(provider, "jwt.issuer")
        val expiresIn = ConfigLoaders.loadDuration(provider, "jwt.expiresIn", Duration.ofHours(1))
        val algorithm = ConfigLoaders.loadOptionalString(provider, "jwt.algorithm", "HS256")

        // Then
        assertEquals("my-secret-key-minimum-32-chars", secret)
        assertEquals("katalyst", issuer)
        assertEquals(Duration.ofHours(1), expiresIn)
        assertEquals("HS256", algorithm)
    }

    @Test
    fun `typical server configuration loading`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "server.host" to "0.0.0.0",
                "server.port" to "8080",
                "server.ssl.enabled" to true,
                "server.ssl.port" to "8443",
                "server.environment" to "production"
            )
        )

        // When
        val host = ConfigLoaders.loadOptionalString(provider, "server.host", "localhost")
        val port = ConfigLoaders.loadOptionalInt(provider, "server.port", 8080)
        val sslEnabled = ConfigLoaders.loadBoolean(provider, "server.ssl.enabled", false)
        val sslPort = ConfigLoaders.loadOptionalInt(provider, "server.ssl.port", 8443)
        val environment = ConfigLoaders.loadEnum(provider, "server.environment", TestEnvironment.DEVELOPMENT)

        // Then
        assertEquals("0.0.0.0", host)
        assertEquals(8080, port)
        assertTrue(sslEnabled)
        assertEquals(8443, sslPort)
        assertEquals(TestEnvironment.PRODUCTION, environment)
    }

    @Test
    fun `configuration with validation`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "app.name" to "Katalyst",
                "app.version" to "1.0.0",
                "database.url" to "jdbc:postgresql://localhost/db",
                "server.port" to "8080"
            )
        )

        // When/Then - Validate all required keys exist
        ConfigLoaders.validateRequiredKeys(
            provider,
            "app.name",
            "app.version",
            "database.url",
            "server.port"
        )

        // Load configuration
        val name = ConfigLoaders.loadRequiredString(provider, "app.name")
        val version = ConfigLoaders.loadRequiredString(provider, "app.version")
        val dbUrl = ConfigLoaders.loadRequiredString(provider, "database.url")
        val port = ConfigLoaders.loadRequiredInt(provider, "server.port")

        assertEquals("Katalyst", name)
        assertEquals("1.0.0", version)
        assertEquals("jdbc:postgresql://localhost/db", dbUrl)
        assertEquals(8080, port)
    }
}
