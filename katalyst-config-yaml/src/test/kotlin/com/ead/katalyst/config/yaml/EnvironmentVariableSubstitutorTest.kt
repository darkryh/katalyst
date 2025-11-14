package com.ead.katalyst.config.yaml

import kotlin.test.*

/**
 * Comprehensive tests for EnvironmentVariableSubstitutor.
 *
 * Tests cover:
 * - Pattern matching (${VAR:default} syntax)
 * - String substitution with defaults
 * - Recursive map substitution
 * - List substitution
 * - Nested structure substitution
 * - Edge cases (empty defaults, special characters, multiple substitutions)
 * - Practical usage scenarios
 *
 * **Note on Environment Variables:**
 * Tests assume certain env vars don't exist (e.g., KATALYST_TEST_VAR_*).
 * Uses PATH env var for positive test cases as it exists on all systems.
 */
class EnvironmentVariableSubstitutorTest {

    private val substitutor = EnvironmentVariableSubstitutor()

    // ========== STRING SUBSTITUTION TESTS ==========

    @Test
    fun `substitute should use default when environment variable does not exist`() {
        // Given
        val input = "\${KATALYST_NONEXISTENT_VAR:default_value}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("default_value", result)
    }

    @Test
    fun `substitute should use environment variable when it exists`() {
        // Given - PATH exists on all systems
        val envVar = "PATH"
        val envValue = System.getenv(envVar)
        assumeTrue(envValue != null, "PATH environment variable should exist")

        val input = "\${$envVar:default_value}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals(envValue, result)
        assertNotEquals("default_value", result)
    }

    @Test
    fun `substitute should use empty string when no default provided and var does not exist`() {
        // Given
        val input = "\${KATALYST_NONEXISTENT_VAR}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("", result)
    }

    @Test
    fun `substitute should handle empty default value`() {
        // Given
        val input = "\${KATALYST_NONEXISTENT_VAR:}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("", result)
    }

    @Test
    fun `substitute should handle multiple substitutions in one string`() {
        // Given
        val input = "jdbc:postgresql://\${DB_HOST:localhost}:\${DB_PORT:5432}/\${DB_NAME:katalyst}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("jdbc:postgresql://localhost:5432/katalyst", result)
    }

    @Test
    fun `substitute should handle plain string without placeholders`() {
        // Given
        val input = "plain_string_value"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("plain_string_value", result)
    }

    @Test
    fun `substitute should handle string with partial placeholder syntax`() {
        // Given
        val input = "value with \$ symbol but not placeholder"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("value with \$ symbol but not placeholder", result)
    }

    @Test
    fun `substitute should handle nested braces in default value`() {
        // Given - Braces in default are supported
        val input = "\${VAR:default{value}}"

        // When
        val result = substitutor.substitute(input)

        // Then - Should stop at first closing brace
        assertEquals("default{value", result)
    }

    @Test
    fun `substitute should handle colon in default value`() {
        // Given
        val input = "\${DB_URL:jdbc:postgresql://localhost:5432/db}"

        // When
        val result = substitutor.substitute(input)

        // Then - Should use everything after first colon as default
        assertEquals("jdbc:postgresql://localhost:5432/db", result)
    }

    @Test
    fun `substitute should handle URL with placeholders`() {
        // Given
        val input = "http://\${API_HOST:api.example.com}:\${API_PORT:8080}/api/v1"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("http://api.example.com:8080/api/v1", result)
    }

    @Test
    fun `substitute should handle connection string with credentials`() {
        // Given
        val input = "postgresql://\${DB_USER:admin}:\${DB_PASS:secret}@\${DB_HOST:localhost}/db"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("postgresql://admin:secret@localhost/db", result)
    }

    @Test
    fun `substitute should handle empty string`() {
        // Given
        val input = ""

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("", result)
    }

    @Test
    fun `substitute should handle string with only placeholder`() {
        // Given
        val input = "\${VAR:value}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("value", result)
    }

    @Test
    fun `substitute should handle special characters in default value`() {
        // Given
        val input = "\${SECRET:p@ssw0rd!#\$%^&*()}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("p@ssw0rd!#\$%^&*()", result)
    }

    @Test
    fun `substitute should handle whitespace in default value`() {
        // Given
        val input = "\${MESSAGE:Hello World with spaces}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("Hello World with spaces", result)
    }

    @Test
    fun `substitute should handle numbers in default value`() {
        // Given
        val input = "\${PORT:8080}"

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("8080", result)
    }

    // ========== MAP SUBSTITUTION TESTS ==========

    @Test
    fun `substitute map should handle simple map`() {
        // Given
        val input = mapOf(
            "host" to "\${DB_HOST:localhost}",
            "port" to "\${DB_PORT:5432}",
            "database" to "\${DB_NAME:katalyst}"
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("localhost", result["host"])
        assertEquals("5432", result["port"])
        assertEquals("katalyst", result["database"])
    }

    @Test
    fun `substitute map should handle nested maps`() {
        // Given
        val input = mapOf(
            "database" to mapOf(
                "url" to "\${DB_URL:jdbc:postgresql://localhost:5432/db}",
                "username" to "\${DB_USER:postgres}",
                "password" to "\${DB_PASS:}"
            ),
            "server" to mapOf(
                "host" to "\${SERVER_HOST:0.0.0.0}",
                "port" to "\${SERVER_PORT:8080}"
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val dbConfig = result["database"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost:5432/db", dbConfig["url"])
        assertEquals("postgres", dbConfig["username"])
        assertEquals("", dbConfig["password"])

        @Suppress("UNCHECKED_CAST")
        val serverConfig = result["server"] as Map<String, Any>
        assertEquals("0.0.0.0", serverConfig["host"])
        assertEquals("8080", serverConfig["port"])
    }

    @Test
    fun `substitute map should handle non-string values`() {
        // Given
        val input = mapOf(
            "string" to "\${VAR:value}",
            "int" to 42,
            "boolean" to true,
            "list" to listOf(1, 2, 3)
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("value", result["string"])
        assertEquals(42, result["int"])
        assertEquals(true, result["boolean"])
        assertEquals(listOf(1, 2, 3), result["list"])
    }

    @Test
    fun `substitute map should handle empty map`() {
        // Given
        val input = emptyMap<String, Any>()

        // When
        val result = substitutor.substitute(input)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `substitute map should preserve non-substitutable values`() {
        // Given
        val input = mapOf(
            "plain_string" to "no_placeholders",
            "number" to 123,
            "flag" to false
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("no_placeholders", result["plain_string"])
        assertEquals(123, result["number"])
        assertEquals(false, result["flag"])
    }

    // ========== LIST SUBSTITUTION TESTS ==========

    @Test
    fun `substitute map should handle lists of strings`() {
        // Given
        val input = mapOf(
            "servers" to listOf(
                "\${SERVER1:server1.com}",
                "\${SERVER2:server2.com}",
                "\${SERVER3:server3.com}"
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val servers = result["servers"] as List<String>
        assertEquals(3, servers.size)
        assertEquals("server1.com", servers[0])
        assertEquals("server2.com", servers[1])
        assertEquals("server3.com", servers[2])
    }

    @Test
    fun `substitute map should handle lists containing maps`() {
        // Given
        val input = mapOf(
            "endpoints" to listOf(
                mapOf(
                    "url" to "\${API1_URL:http://api1.com}",
                    "key" to "\${API1_KEY:key1}"
                ),
                mapOf(
                    "url" to "\${API2_URL:http://api2.com}",
                    "key" to "\${API2_KEY:key2}"
                )
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val endpoints = result["endpoints"] as List<Map<String, Any>>
        assertEquals(2, endpoints.size)
        assertEquals("http://api1.com", endpoints[0]["url"])
        assertEquals("key1", endpoints[0]["key"])
        assertEquals("http://api2.com", endpoints[1]["url"])
        assertEquals("key2", endpoints[1]["key"])
    }

    @Test
    fun `substitute map should handle mixed lists`() {
        // Given
        val input = mapOf(
            "mixed" to listOf(
                "\${VAR:string_value}",
                42,
                true,
                mapOf("key" to "\${NESTED:nested_value}")
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val mixed = result["mixed"] as List<Any>
        assertEquals(4, mixed.size)
        assertEquals("string_value", mixed[0])
        assertEquals(42, mixed[1])
        assertEquals(true, mixed[2])

        @Suppress("UNCHECKED_CAST")
        val nestedMap = mixed[3] as Map<String, Any>
        assertEquals("nested_value", nestedMap["key"])
    }

    @Test
    fun `substitute map should handle empty lists`() {
        // Given
        val input = mapOf("empty" to emptyList<String>())

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val empty = result["empty"] as List<Any>
        assertTrue(empty.isEmpty())
    }

    // ========== DEEPLY NESTED STRUCTURE TESTS ==========

    @Test
    fun `substitute should handle deeply nested configuration`() {
        // Given
        val input = mapOf(
            "app" to mapOf(
                "name" to "\${APP_NAME:Katalyst}",
                "modules" to mapOf(
                    "database" to mapOf(
                        "config" to mapOf(
                            "url" to "\${DB_URL:jdbc:postgresql://localhost/db}",
                            "pool" to mapOf(
                                "size" to "\${POOL_SIZE:10}",
                                "timeout" to "\${POOL_TIMEOUT:30000}"
                            )
                        )
                    ),
                    "cache" to mapOf(
                        "provider" to "\${CACHE_PROVIDER:redis}",
                        "ttl" to "\${CACHE_TTL:3600}"
                    )
                )
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val app = result["app"] as Map<String, Any>
        assertEquals("Katalyst", app["name"])

        @Suppress("UNCHECKED_CAST")
        val modules = app["modules"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val database = modules["database"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val dbConfig = database["config"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost/db", dbConfig["url"])

        @Suppress("UNCHECKED_CAST")
        val pool = dbConfig["pool"] as Map<String, Any>
        assertEquals("10", pool["size"])
        assertEquals("30000", pool["timeout"])

        @Suppress("UNCHECKED_CAST")
        val cache = modules["cache"] as Map<String, Any>
        assertEquals("redis", cache["provider"])
        assertEquals("3600", cache["ttl"])
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical database configuration substitution`() {
        // Given - Typical database config from YAML
        val input = mapOf(
            "database" to mapOf(
                "url" to "\${DATABASE_URL:jdbc:postgresql://localhost:5432/katalyst_db}",
                "username" to "\${DATABASE_USER:postgres}",
                "password" to "\${DATABASE_PASSWORD:}",
                "pool" to mapOf(
                    "maxSize" to "\${DB_POOL_MAX_SIZE:20}",
                    "minIdle" to "\${DB_POOL_MIN_IDLE:5}"
                ),
                "ssl" to mapOf(
                    "enabled" to "\${DB_SSL_ENABLED:false}",
                    "cert" to "\${DB_SSL_CERT:}"
                )
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val db = result["database"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost:5432/katalyst_db", db["url"])
        assertEquals("postgres", db["username"])
        assertEquals("", db["password"])  // Empty default for secrets

        @Suppress("UNCHECKED_CAST")
        val pool = db["pool"] as Map<String, Any>
        assertEquals("20", pool["maxSize"])
        assertEquals("5", pool["minIdle"])

        @Suppress("UNCHECKED_CAST")
        val ssl = db["ssl"] as Map<String, Any>
        assertEquals("false", ssl["enabled"])
        assertEquals("", ssl["cert"])
    }

    @Test
    fun `typical multi-service configuration substitution`() {
        // Given - Configuration for multiple services
        val input = mapOf(
            "services" to listOf(
                mapOf(
                    "name" to "auth-service",
                    "url" to "\${AUTH_SERVICE_URL:http://localhost:8081}",
                    "timeout" to "\${AUTH_TIMEOUT:5000}"
                ),
                mapOf(
                    "name" to "payment-service",
                    "url" to "\${PAYMENT_SERVICE_URL:http://localhost:8082}",
                    "timeout" to "\${PAYMENT_TIMEOUT:10000}"
                ),
                mapOf(
                    "name" to "notification-service",
                    "url" to "\${NOTIFICATION_SERVICE_URL:http://localhost:8083}",
                    "timeout" to "\${NOTIFICATION_TIMEOUT:3000}"
                )
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val services = result["services"] as List<Map<String, Any>>
        assertEquals(3, services.size)

        assertEquals("auth-service", services[0]["name"])
        assertEquals("http://localhost:8081", services[0]["url"])
        assertEquals("5000", services[0]["timeout"])

        assertEquals("payment-service", services[1]["name"])
        assertEquals("http://localhost:8082", services[1]["url"])
        assertEquals("10000", services[1]["timeout"])
    }

    @Test
    fun `typical JWT configuration with secret substitution`() {
        // Given
        val input = mapOf(
            "jwt" to mapOf(
                "secret" to "\${JWT_SECRET:change-me-in-production}",
                "issuer" to "\${JWT_ISSUER:katalyst}",
                "audience" to "\${JWT_AUDIENCE:katalyst-api}",
                "expiresIn" to "\${JWT_EXPIRES_IN:3600}",
                "algorithm" to "\${JWT_ALGORITHM:HS256}"
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        @Suppress("UNCHECKED_CAST")
        val jwt = result["jwt"] as Map<String, Any>
        assertEquals("change-me-in-production", jwt["secret"])
        assertEquals("katalyst", jwt["issuer"])
        assertEquals("katalyst-api", jwt["audience"])
        assertEquals("3600", jwt["expiresIn"])
        assertEquals("HS256", jwt["algorithm"])
    }

    @Test
    fun `environment-specific configuration substitution`() {
        // Given - Different values based on environment
        val input = mapOf(
            "environment" to "\${ENV:development}",
            "logging" to mapOf(
                "level" to "\${LOG_LEVEL:INFO}",
                "file" to "\${LOG_FILE:/var/log/katalyst.log}"
            ),
            "features" to mapOf(
                "analytics" to "\${FEATURE_ANALYTICS:false}",
                "debug" to "\${FEATURE_DEBUG:true}"
            )
        )

        // When
        val result = substitutor.substitute(input)

        // Then
        assertEquals("development", result["environment"])

        @Suppress("UNCHECKED_CAST")
        val logging = result["logging"] as Map<String, Any>
        assertEquals("INFO", logging["level"])
        assertEquals("/var/log/katalyst.log", logging["file"])

        @Suppress("UNCHECKED_CAST")
        val features = result["features"] as Map<String, Any>
        assertEquals("false", features["analytics"])
        assertEquals("true", features["debug"])
    }
}
