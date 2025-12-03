package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.test.*

/**
 * Comprehensive tests for ServiceConfigLoader interface.
 *
 * Tests cover:
 * - Interface contract
 * - loadConfig() implementation
 * - validate() optional override
 * - Custom loader implementations
 * - Type-safe configuration loading
 * - Validation patterns
 * - Error handling
 * - Practical usage scenarios
 */
class ServiceConfigLoaderTest {

    // ========== TEST DATA CLASSES ==========

    data class DatabaseConfig(
        val url: String,
        val username: String,
        val password: String,
        val maxPoolSize: Int,
        val timeout: Long
    )

    data class JwtConfig(
        val secret: String,
        val issuer: String,
        val expiresIn: Long,
        val algorithm: String
    )

    data class ServerConfig(
        val host: String,
        val port: Int,
        val ssl: Boolean
    )

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
                else -> default
            }
        }

        override fun getLong(key: String, default: Long): Long {
            val value = data[key] ?: return default
            return when (value) {
                is Long -> value
                is String -> value.toLongOrNull() ?: default
                else -> default
            }
        }

        override fun getBoolean(key: String, default: Boolean): Boolean {
            val value = data[key] ?: return default
            return when (value) {
                is Boolean -> value
                else -> default
            }
        }

        override fun getList(key: String, default: List<String>): List<String> {
            @Suppress("UNCHECKED_CAST")
            return data[key] as? List<String> ?: default
        }

        override fun hasKey(key: String): Boolean = data.containsKey(key)

        override fun getAllKeys(): Set<String> = data.keys
    }

    // ========== TEST LOADER IMPLEMENTATIONS ==========

    class DatabaseConfigLoader : ServiceConfigLoader<DatabaseConfig> {
        override fun loadConfig(provider: ConfigProvider): DatabaseConfig {
            return DatabaseConfig(
                url = provider.getString("database.url"),
                username = provider.getString("database.username"),
                password = provider.getString("database.password"),
                maxPoolSize = provider.getInt("database.pool.maxSize", 10),
                timeout = provider.getLong("database.timeout", 30000L)
            )
        }

        override fun validate(config: DatabaseConfig) {
            if (config.url.isBlank()) {
                throw ConfigException("database.url cannot be blank")
            }
            if (config.maxPoolSize <= 0) {
                throw ConfigException("database.pool.maxSize must be positive")
            }
        }
    }

    class JwtConfigLoader : ServiceConfigLoader<JwtConfig> {
        override fun loadConfig(provider: ConfigProvider): JwtConfig {
            return JwtConfig(
                secret = provider.getString("jwt.secret"),
                issuer = provider.getString("jwt.issuer"),
                expiresIn = provider.getLong("jwt.expiresIn", 3600000L),
                algorithm = provider.getString("jwt.algorithm", "HS256")
            )
        }

        override fun validate(config: JwtConfig) {
            if (config.secret.length < 32) {
                throw ConfigException("JWT secret must be at least 32 characters")
            }
            if (config.issuer.isBlank()) {
                throw ConfigException("JWT issuer cannot be blank")
            }
            if (config.expiresIn <= 0) {
                throw ConfigException("JWT expiresIn must be positive")
            }
        }
    }

    class ServerConfigLoader : ServiceConfigLoader<ServerConfig> {
        override fun loadConfig(provider: ConfigProvider): ServerConfig {
            return ServerConfig(
                host = provider.getString("server.host", "0.0.0.0"),
                port = provider.getInt("server.port", 8080),
                ssl = provider.getBoolean("server.ssl", false)
            )
        }

        override fun validate(config: ServerConfig) {
            if (config.port < 1 || config.port > 65535) {
                throw ConfigException("Server port must be between 1 and 65535")
            }
        }
    }

    class NoValidationLoader : ServiceConfigLoader<String> {
        override fun loadConfig(provider: ConfigProvider): String {
            return provider.getString("value", "default")
        }
        // No validate() override - uses default empty implementation
    }

    // ========== INTERFACE CONTRACT TESTS ==========

    @Test
    fun `ServiceConfigLoader should have loadConfig method`() {
        // Given
        val loader = DatabaseConfigLoader()
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost/db",
                "database.username" to "admin",
                "database.password" to "secret"
            )
        )

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertNotNull(config)
        assertTrue(config is DatabaseConfig)
    }

    @Test
    fun `ServiceConfigLoader should have optional validate method`() {
        // Given
        val loader = NoValidationLoader()
        val config = "test"

        // When/Then - Should not throw (default implementation does nothing)
        loader.validate(config)
    }

    // ========== DatabaseConfigLoader TESTS ==========

    @Test
    fun `DatabaseConfigLoader should load valid configuration`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost:5432/katalyst",
                "database.username" to "admin",
                "database.password" to "secret",
                "database.pool.maxSize" to 20,
                "database.timeout" to 45000L
            )
        )
        val loader = DatabaseConfigLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertEquals("jdbc:postgresql://localhost:5432/katalyst", config.url)
        assertEquals("admin", config.username)
        assertEquals("secret", config.password)
        assertEquals(20, config.maxPoolSize)
        assertEquals(45000L, config.timeout)
    }

    @Test
    fun `DatabaseConfigLoader should use defaults for optional values`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost/db",
                "database.username" to "admin",
                "database.password" to "secret"
            )
        )
        val loader = DatabaseConfigLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertEquals(10, config.maxPoolSize)  // Default value
        assertEquals(30000L, config.timeout)  // Default value
    }

    @Test
    fun `DatabaseConfigLoader validation should pass for valid config`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:postgresql://localhost/db",
            username = "admin",
            password = "secret",
            maxPoolSize = 20,
            timeout = 30000L
        )
        val loader = DatabaseConfigLoader()

        // When/Then - Should not throw
        loader.validate(config)
    }

    @Test
    fun `DatabaseConfigLoader validation should fail for blank URL`() {
        // Given
        val config = DatabaseConfig(
            url = "",
            username = "admin",
            password = "secret",
            maxPoolSize = 20,
            timeout = 30000L
        )
        val loader = DatabaseConfigLoader()

        // Then
        val exception = assertFailsWith<ConfigException> {
            loader.validate(config)
        }
        assertTrue(exception.message?.contains("url cannot be blank") == true)
    }

    @Test
    fun `DatabaseConfigLoader validation should fail for non-positive pool size`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:postgresql://localhost/db",
            username = "admin",
            password = "secret",
            maxPoolSize = 0,
            timeout = 30000L
        )
        val loader = DatabaseConfigLoader()

        // Then
        val exception = assertFailsWith<ConfigException> {
            loader.validate(config)
        }
        assertTrue(exception.message?.contains("database.pool.maxSize must be positive") == true)
    }

    // ========== JwtConfigLoader TESTS ==========

    @Test
    fun `JwtConfigLoader should load valid configuration`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "jwt.secret" to "this-is-a-very-long-secret-key-32-chars-min",
                "jwt.issuer" to "katalyst",
                "jwt.expiresIn" to 7200000L,
                "jwt.algorithm" to "HS512"
            )
        )
        val loader = JwtConfigLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertEquals("this-is-a-very-long-secret-key-32-chars-min", config.secret)
        assertEquals("katalyst", config.issuer)
        assertEquals(7200000L, config.expiresIn)
        assertEquals("HS512", config.algorithm)
    }

    @Test
    fun `JwtConfigLoader should use defaults for optional values`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "jwt.secret" to "this-is-a-very-long-secret-key-32-chars-min",
                "jwt.issuer" to "katalyst"
            )
        )
        val loader = JwtConfigLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertEquals(3600000L, config.expiresIn)  // Default 1 hour
        assertEquals("HS256", config.algorithm)   // Default algorithm
    }

    @Test
    fun `JwtConfigLoader validation should pass for valid config`() {
        // Given
        val config = JwtConfig(
            secret = "this-is-a-very-long-secret-key-32-chars-min",
            issuer = "katalyst",
            expiresIn = 3600000L,
            algorithm = "HS256"
        )
        val loader = JwtConfigLoader()

        // When/Then - Should not throw
        loader.validate(config)
    }

    @Test
    fun `JwtConfigLoader validation should fail for short secret`() {
        // Given
        val config = JwtConfig(
            secret = "too-short",
            issuer = "katalyst",
            expiresIn = 3600000L,
            algorithm = "HS256"
        )
        val loader = JwtConfigLoader()

        // Then
        val exception = assertFailsWith<ConfigException> {
            loader.validate(config)
        }
        assertTrue(exception.message?.contains("at least 32 characters") == true)
    }

    @Test
    fun `JwtConfigLoader validation should fail for blank issuer`() {
        // Given
        val config = JwtConfig(
            secret = "this-is-a-very-long-secret-key-32-chars-min",
            issuer = "",
            expiresIn = 3600000L,
            algorithm = "HS256"
        )
        val loader = JwtConfigLoader()

        // Then
        val exception = assertFailsWith<ConfigException> {
            loader.validate(config)
        }
        assertTrue(exception.message?.contains("issuer cannot be blank") == true)
    }

    @Test
    fun `JwtConfigLoader validation should fail for non-positive expiresIn`() {
        // Given
        val config = JwtConfig(
            secret = "this-is-a-very-long-secret-key-32-chars-min",
            issuer = "katalyst",
            expiresIn = 0L,
            algorithm = "HS256"
        )
        val loader = JwtConfigLoader()

        // Then
        val exception = assertFailsWith<ConfigException> {
            loader.validate(config)
        }
        assertTrue(exception.message?.contains("expiresIn must be positive") == true)
    }

    // ========== ServerConfigLoader TESTS ==========

    @Test
    fun `ServerConfigLoader should load valid configuration`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "server.host" to "127.0.0.1",
                "server.port" to 9090,
                "server.ssl" to true
            )
        )
        val loader = ServerConfigLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertEquals("127.0.0.1", config.host)
        assertEquals(9090, config.port)
        assertTrue(config.ssl)
    }

    @Test
    fun `ServerConfigLoader should use defaults`() {
        // Given
        val provider = TestConfigProvider()
        val loader = ServerConfigLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertFalse(config.ssl)
    }

    @Test
    fun `ServerConfigLoader validation should pass for valid port`() {
        // Given
        val config = ServerConfig(
            host = "0.0.0.0",
            port = 8080,
            ssl = false
        )
        val loader = ServerConfigLoader()

        // When/Then - Should not throw
        loader.validate(config)
    }

    @Test
    fun `ServerConfigLoader validation should fail for port too low`() {
        // Given
        val config = ServerConfig(
            host = "0.0.0.0",
            port = 0,
            ssl = false
        )
        val loader = ServerConfigLoader()

        // Then
        val exception = assertFailsWith<ConfigException> {
            loader.validate(config)
        }
        assertTrue(exception.message?.contains("between 1 and 65535") == true)
    }

    @Test
    fun `ServerConfigLoader validation should fail for port too high`() {
        // Given
        val config = ServerConfig(
            host = "0.0.0.0",
            port = 70000,
            ssl = false
        )
        val loader = ServerConfigLoader()

        // Then
        val exception = assertFailsWith<ConfigException> {
            loader.validate(config)
        }
        assertTrue(exception.message?.contains("between 1 and 65535") == true)
    }

    @Test
    fun `ServerConfigLoader validation should pass for edge port values`() {
        // Given
        val loader = ServerConfigLoader()
        val minPort = ServerConfig("0.0.0.0", 1, false)
        val maxPort = ServerConfig("0.0.0.0", 65535, false)

        // When/Then - Should not throw
        loader.validate(minPort)
        loader.validate(maxPort)
    }

    // ========== NoValidationLoader TESTS ==========

    @Test
    fun `NoValidationLoader should use default validate implementation`() {
        // Given
        val loader = NoValidationLoader()
        val config = "any value"

        // When/Then - Should not throw (default validate does nothing)
        loader.validate(config)
    }

    @Test
    fun `NoValidationLoader should load config successfully`() {
        // Given
        val provider = TestConfigProvider(mapOf("value" to "test"))
        val loader = NoValidationLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then
        assertEquals("test", config)
    }

    // ========== INTEGRATION TESTS ==========

    @Test
    fun `typical workflow - load and validate`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost/db",
                "database.username" to "admin",
                "database.password" to "secret",
                "database.pool.maxSize" to 15
            )
        )
        val loader = DatabaseConfigLoader()

        // When
        val config = loader.loadConfig(provider)
        loader.validate(config)

        // Then - No exception means success
        assertNotNull(config)
        assertEquals("jdbc:postgresql://localhost/db", config.url)
        assertEquals(15, config.maxPoolSize)
    }

    @Test
    fun `typical workflow - load fails validation`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "",  // Invalid: blank
                "database.username" to "admin",
                "database.password" to "secret"
            )
        )
        val loader = DatabaseConfigLoader()

        // When
        val config = loader.loadConfig(provider)

        // Then - Validation should fail
        assertFailsWith<ConfigException> {
            loader.validate(config)
        }
    }

    @Test
    fun `multiple loaders can coexist`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost/db",
                "database.username" to "admin",
                "database.password" to "secret",
                "jwt.secret" to "this-is-a-very-long-secret-key-32-chars-min",
                "jwt.issuer" to "katalyst",
                "server.host" to "localhost",
                "server.port" to 8080
            )
        )

        val dbLoader = DatabaseConfigLoader()
        val jwtLoader = JwtConfigLoader()
        val serverLoader = ServerConfigLoader()

        // When
        val dbConfig = dbLoader.loadConfig(provider)
        val jwtConfig = jwtLoader.loadConfig(provider)
        val serverConfig = serverLoader.loadConfig(provider)

        // Then
        assertNotNull(dbConfig)
        assertNotNull(jwtConfig)
        assertNotNull(serverConfig)

        dbLoader.validate(dbConfig)
        jwtLoader.validate(jwtConfig)
        serverLoader.validate(serverConfig)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `production configuration with strict validation`() {
        // Given - Production-like configuration
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://prod-db.example.com:5432/katalyst_prod",
                "database.username" to "prod_user",
                "database.password" to "super-secure-password",
                "database.pool.maxSize" to 50,
                "database.timeout" to 60000L
            )
        )
        val loader = DatabaseConfigLoader()

        // When
        val config = loader.loadConfig(provider)
        loader.validate(config)

        // Then
        assertEquals("jdbc:postgresql://prod-db.example.com:5432/katalyst_prod", config.url)
        assertEquals("prod_user", config.username)
        assertEquals(50, config.maxPoolSize)
        assertEquals(60000L, config.timeout)
    }

    @Test
    fun `development configuration with defaults`() {
        // Given - Minimal development configuration
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost/katalyst_dev",
                "database.username" to "dev",
                "database.password" to "dev"
            )
        )
        val loader = DatabaseConfigLoader()

        // When
        val config = loader.loadConfig(provider)
        loader.validate(config)

        // Then - Uses sensible defaults
        assertEquals(10, config.maxPoolSize)   // Default
        assertEquals(30000L, config.timeout)   // Default
    }
}
