package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.core.config.ConfigException
import kotlin.test.*

/**
 * Comprehensive tests for YamlParser.
 *
 * Tests cover:
 * - Valid YAML parsing
 * - Invalid YAML handling
 * - Environment variable substitution integration
 * - Different YAML structures (maps, lists, scalars)
 * - Root validation
 * - Edge cases (empty, null, malformed)
 * - Practical usage scenarios
 */
class YamlParserTest {

    private fun parseWithoutSystemEnv(content: String): Map<String, Any> =
        YamlParser.parse(content) { _ -> null }

    // ========== VALID YAML PARSING TESTS ==========

    @Test
    fun `parse should handle simple flat YAML`() {
        // Given
        val yaml = """
            name: Katalyst
            version: 1.0.0
            port: 8080
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        assertEquals("Katalyst", result["name"])
        assertEquals("1.0.0", result["version"])
        assertEquals(8080, result["port"])
    }

    @Test
    fun `parse should handle nested YAML maps`() {
        // Given
        val yaml = """
            database:
              url: jdbc:postgresql://localhost:5432/db
              username: postgres
              password: secret
              pool:
                size: 10
                timeout: 30000
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val database = result["database"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost:5432/db", database["url"])
        assertEquals("postgres", database["username"])
        assertEquals("secret", database["password"])

        @Suppress("UNCHECKED_CAST")
        val pool = database["pool"] as Map<String, Any>
        assertEquals(10, pool["size"])
        assertEquals(30000, pool["timeout"])
    }

    @Test
    fun `parse should handle YAML lists`() {
        // Given
        val yaml = """
            servers:
              - server1.com
              - server2.com
              - server3.com
            ports:
              - 8080
              - 8081
              - 8082
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val servers = result["servers"] as List<String>
        assertEquals(3, servers.size)
        assertEquals("server1.com", servers[0])
        assertEquals("server2.com", servers[1])
        assertEquals("server3.com", servers[2])

        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Int>
        assertEquals(3, ports.size)
        assertEquals(8080, ports[0])
        assertEquals(8081, ports[1])
        assertEquals(8082, ports[2])
    }

    @Test
    fun `parse should handle YAML with maps in lists`() {
        // Given
        val yaml = """
            endpoints:
              - name: auth
                url: http://localhost:8081
                timeout: 5000
              - name: payment
                url: http://localhost:8082
                timeout: 10000
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val endpoints = result["endpoints"] as List<Map<String, Any>>
        assertEquals(2, endpoints.size)

        assertEquals("auth", endpoints[0]["name"])
        assertEquals("http://localhost:8081", endpoints[0]["url"])
        assertEquals(5000, endpoints[0]["timeout"])

        assertEquals("payment", endpoints[1]["name"])
        assertEquals("http://localhost:8082", endpoints[1]["url"])
        assertEquals(10000, endpoints[1]["timeout"])
    }

    @Test
    fun `parse should handle boolean values`() {
        // Given
        val yaml = """
            feature:
              enabled: true
              debug: false
              verbose: yes
              quiet: no
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val feature = result["feature"] as Map<String, Any>
        assertEquals(true, feature["enabled"])
        assertEquals(false, feature["debug"])
        assertEquals(true, feature["verbose"])
        assertEquals(false, feature["quiet"])
    }

    @Test
    fun `parse should handle different number types`() {
        // Given
        val yaml = """
            numbers:
              int: 42
              long: 9876543210
              float: 3.14
              negative: -100
              zero: 0
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val numbers = result["numbers"] as Map<String, Any>
        assertEquals(42, numbers["int"])
        assertEquals(9876543210, numbers["long"])
        assertEquals(3.14, numbers["float"])
        assertEquals(-100, numbers["negative"])
        assertEquals(0, numbers["zero"])
    }

    @Test
    fun `parse should handle quoted strings`() {
        // Given
        val yaml = """
            messages:
              single: 'single quoted'
              double: "double quoted"
              plain: plain string
              special: "string with: colons and - dashes"
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val messages = result["messages"] as Map<String, Any>
        assertEquals("single quoted", messages["single"])
        assertEquals("double quoted", messages["double"])
        assertEquals("plain string", messages["plain"])
        assertEquals("string with: colons and - dashes", messages["special"])
    }

    @Test
    fun `parse should handle multiline strings`() {
        // Given
        val yaml = """
            description: |
              This is a multiline
              string in YAML
              with multiple lines
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        val description = result["description"] as String
        assertTrue(description.contains("multiline"))
        assertTrue(description.contains("multiple lines"))
    }

    @Test
    fun `parse should handle empty string values`() {
        // Given
        val yaml = """
            empty1: ""
            empty2: ''
            empty3:
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        assertEquals("", result["empty1"])
        assertEquals("", result["empty2"])
        assertNull(result["empty3"])
    }

    // ========== ENVIRONMENT VARIABLE SUBSTITUTION TESTS ==========

    @Test
    fun `parse should substitute environment variables in values`() {
        // Given
        val yaml = """
            database:
              url: ${'$'}{DB_URL:jdbc:postgresql://localhost:5432/db}
              username: ${'$'}{DB_USER:postgres}
              password: ${'$'}{DB_PASS:}
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val database = result["database"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost:5432/db", database["url"])
        assertEquals("postgres", database["username"])
        assertEquals("", database["password"])
    }

    @Test
    fun `parse should substitute environment variables in nested structures`() {
        // Given
        val yaml = """
            app:
              name: ${'$'}{APP_NAME:Katalyst}
              server:
                host: ${'$'}{SERVER_HOST:0.0.0.0}
                port: ${'$'}{SERVER_PORT:8080}
              database:
                url: ${'$'}{DB_URL:jdbc:postgresql://localhost/db}
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val app = result["app"] as Map<String, Any>
        assertEquals("Katalyst", app["name"])

        @Suppress("UNCHECKED_CAST")
        val server = app["server"] as Map<String, Any>
        assertEquals("0.0.0.0", server["host"])
        assertEquals("8080", server["port"])

        @Suppress("UNCHECKED_CAST")
        val database = app["database"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost/db", database["url"])
    }

    @Test
    fun `parse should substitute environment variables in lists`() {
        // Given
        val yaml = """
            servers:
              - ${'$'}{SERVER1:server1.com}
              - ${'$'}{SERVER2:server2.com}
              - ${'$'}{SERVER3:server3.com}
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val servers = result["servers"] as List<String>
        assertEquals(3, servers.size)
        assertEquals("server1.com", servers[0])
        assertEquals("server2.com", servers[1])
        assertEquals("server3.com", servers[2])
    }

    @Test
    fun `parse should handle mixed substitution and plain values`() {
        // Given
        val yaml = """
            config:
              plain: plain_value
              substituted: ${'$'}{VAR:default_value}
              number: 42
              flag: true
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val config = result["config"] as Map<String, Any>
        assertEquals("plain_value", config["plain"])
        assertEquals("default_value", config["substituted"])
        assertEquals(42, config["number"])
        assertEquals(true, config["flag"])
    }

    @Test
    fun `parse should normalize commented environment values`() {
        val yaml = """
            app:
              version: ${'$'}{APP_VERSION:0.0.0}
            database:
              url: ${'$'}{DATABASE_URL:jdbc:postgresql://localhost:5432/default_db}
            ai:
              model: ${'$'}{AI_MODEL:gpt-default}
        """.trimIndent()

        val env = mapOf(
            "APP_VERSION" to "1.1.12 # semantic version",
            "DATABASE_URL" to "jdbc:postgresql://localhost:5432/sampledb # local db",
            "AI_MODEL" to "\"gpt-5\" # provider hint"
        )

        val result = YamlParser.parse(yaml) { key -> env[key] }

        @Suppress("UNCHECKED_CAST")
        val app = result["app"] as Map<String, Any>
        assertEquals("1.1.12", app["version"])

        @Suppress("UNCHECKED_CAST")
        val database = result["database"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost:5432/sampledb", database["url"])

        @Suppress("UNCHECKED_CAST")
        val ai = result["ai"] as Map<String, Any>
        assertEquals("gpt-5", ai["model"])
    }

    @Test
    fun `parse should keep hash for non commented values`() {
        val yaml = """
            token:
              value: ${'$'}{TOKEN_VALUE:default}
        """.trimIndent()

        val result = YamlParser.parse(yaml) { key ->
            if (key == "TOKEN_VALUE") "abc#def" else null
        }

        @Suppress("UNCHECKED_CAST")
        val token = result["token"] as Map<String, Any>
        assertEquals("abc#def", token["value"])
    }

    // ========== EMPTY AND NULL HANDLING TESTS ==========

    @Test
    fun `parse should return empty map for empty string`() {
        // Given
        val yaml = ""

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse should return empty map for whitespace only`() {
        // Given
        val yaml = "   \n   \n   "

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse should return empty map for YAML with only comments`() {
        // Given
        val yaml = """
            # This is a comment
            # Another comment
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse should handle YAML with comments`() {
        // Given
        val yaml = """
            # Application configuration
            app:
              # Application name
              name: Katalyst
              # Server port
              port: 8080
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val app = result["app"] as Map<String, Any>
        assertEquals("Katalyst", app["name"])
        assertEquals(8080, app["port"])
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    fun `parse should throw ConfigException for invalid YAML syntax`() {
        // Given - Invalid YAML (unclosed quote)
        val yaml = """
            invalid: "unclosed quote
        """.trimIndent()

        // Then
        assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
    }

    @Test
    fun `parse should throw ConfigException when root is array`() {
        // Given - YAML root is an array, not a map
        val yaml = """
            - item1
            - item2
            - item3
        """.trimIndent()

        // Then
        val exception = assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
        assertTrue(exception.message?.contains("root must be a map") == true)
    }

    @Test
    fun `parse should throw ConfigException when root is scalar`() {
        // Given - YAML root is a scalar value
        val yaml = "just a string"

        // Then
        val exception = assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
        assertTrue(exception.message?.contains("root must be a map") == true)
    }

    @Test
    fun `parse should throw ConfigException for malformed YAML`() {
        // Given - Malformed YAML (invalid indentation)
        val yaml = """
            valid: value
              invalid_indentation: value
        """.trimIndent()

        // Then
        assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
    }

    // ========== ADVERSARIAL / HOSTILE DOCUMENT TESTS ==========

    @Test
    fun `parse should reject duplicate keys instead of silently dropping one`() {
        // Given - the same key twice. SnakeYAML's own default (allowDuplicateKeys = true) keeps
        // the LAST value and discards the first without a word, so a config file that shipped
        // two `url:` lines would connect to whichever one happened to come second.
        val yaml = """
            database:
              url: jdbc:postgresql://primary:5432/db
              url: jdbc:postgresql://replica:5432/db
        """.trimIndent()

        // Then - Katalyst pins allowDuplicateKeys = false, so this fails loudly
        val exception = assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
        assertTrue(exception.message?.contains("duplicate key") == true, exception.message)
        assertTrue(exception.message?.contains("url") == true, exception.message)
    }

    @Test
    fun `parse should reject duplicate keys at the document root`() {
        // Given
        val yaml = """
            profile: dev
            profile: prod
        """.trimIndent()

        // Then
        assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
    }

    @Test
    fun `parse should reject an alias bomb instead of expanding it`() {
        // Given - the classic "billion laughs": each level multiplies the previous one by ten.
        // Fully expanded this is 10^6 leaf nodes; the alias cap must stop it at compose time.
        val yaml = """
            a: &a [1, 1, 1, 1, 1, 1, 1, 1, 1, 1]
            b: &b [*a, *a, *a, *a, *a, *a, *a, *a, *a, *a]
            c: &c [*b, *b, *b, *b, *b, *b, *b, *b, *b, *b]
            d: &d [*c, *c, *c, *c, *c, *c, *c, *c, *c, *c]
            e: &e [*d, *d, *d, *d, *d, *d, *d, *d, *d, *d]
            f: &f [*e, *e, *e, *e, *e, *e, *e, *e, *e, *e]
            g: [*f, *f, *f, *f, *f, *f, *f, *f, *f, *f]
        """.trimIndent()

        // Then - raises rather than hanging or exhausting the heap
        val exception = assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
        assertTrue(exception.message?.contains("aliases") == true, exception.message)
    }

    @Test
    fun `parse should allow alias reuse below the pinned limit`() {
        // Given - 40 collection aliases, comfortably under the pinned cap of 50.
        // This is the other half of the pin: the cap must not be so tight that ordinary
        // anchor reuse in a real application.yaml stops working.
        val yaml = """
            a: &a [1, 1, 1, 1, 1, 1, 1, 1, 1, 1]
            b: &b [*a, *a, *a, *a, *a, *a, *a, *a, *a, *a]
            c: &c [*b, *b, *b, *b, *b, *b, *b, *b, *b, *b]
            d: [*c, *c, *c, *c, *c, *c, *c, *c, *c, *c]
        """.trimIndent()

        // Then
        val result = parseWithoutSystemEnv(yaml)
        assertEquals(4, result.size)
    }

    @Test
    fun `parse should reject a document nested past the pinned depth limit`() {
        // Given - 200 levels of nesting, four times the pinned nestingDepthLimit of 50
        val yaml = buildString {
            repeat(200) { append("[") }
            append("1")
            repeat(200) { append("]") }
        }.let { "deep: $it" }

        // Then
        assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
    }

    @Test
    fun `parse should throw ConfigException for tab indentation`() {
        // Given - YAML forbids tabs as indentation; an editor that "helpfully" converts spaces
        // must not produce a half-parsed configuration
        val yaml = "database:\n\turl: jdbc:h2:mem:test\n"

        // Then
        val exception = assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
        assertTrue(exception.message?.contains("tab", ignoreCase = true) == true, exception.message)
    }

    @Test
    fun `parse should throw ConfigException for a truncated document`() {
        // Given - a file cut off mid-write (unterminated flow sequence)
        val yaml = "servers: [alpha, beta,"

        // Then
        assertFailsWith<ConfigException> {
            parseWithoutSystemEnv(yaml)
        }
    }

    // ========== YAML 1.1 SCALAR COERCION (pinned, not endorsed) ==========

    @Test
    fun `parse resolves unquoted NO and ON as booleans not strings`() {
        // Given - a region code and a switch that look like text to a human
        val yaml = """
            region: NO
            mode: ON
            quoted: "NO"
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then - YAML 1.1 turns the bare tokens into booleans; only quoting preserves the text
        assertEquals(false, result["region"])
        assertEquals(true, result["mode"])
        assertEquals("NO", result["quoted"])
    }

    @Test
    fun `parse produces non-String map keys for unquoted boolean and numeric keys`() {
        // Given
        val yaml = """
            no: disabled
            2024: annual-rate
            "yes": kept-as-text
        """.trimIndent()

        // When
        @Suppress("UNCHECKED_CAST")
        val result = parseWithoutSystemEnv(yaml) as Map<Any?, Any?>

        // Then - the keys are Boolean/Int, not String. YamlConfigurationSource.navigatePath()
        // indexes the map with a String, so these values are unreachable through the dotted API.
        assertTrue(result.containsKey(false), "expected a Boolean key, got ${result.keys}")
        assertTrue(result.containsKey(2024), "expected an Int key, got ${result.keys}")
        assertFalse(result.containsKey("no"))
        assertFalse(result.containsKey("2024"))
        // A quoted key stays text
        assertEquals("kept-as-text", result["yes"])
    }

    // ========== COMPLEX STRUCTURE TESTS ==========

    @Test
    fun `parse should handle deeply nested configuration`() {
        // Given
        val yaml = """
            app:
              name: Katalyst
              modules:
                database:
                  config:
                    url: jdbc:postgresql://localhost/db
                    pool:
                      size: 10
                      timeout: 30000
                cache:
                  provider: redis
                  ttl: 3600
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

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
        assertEquals(10, pool["size"])
        assertEquals(30000, pool["timeout"])
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical application configuration YAML`() {
        // Given - Real-world application.yaml
        val yaml = """
            app:
              name: ${'$'}{APP_NAME:Katalyst}
              version: 1.0.0
              environment: ${'$'}{ENV:development}

            server:
              host: ${'$'}{SERVER_HOST:0.0.0.0}
              port: ${'$'}{SERVER_PORT:8080}

            database:
              url: ${'$'}{DATABASE_URL:jdbc:postgresql://localhost:5432/katalyst}
              username: ${'$'}{DATABASE_USER:postgres}
              password: ${'$'}{DATABASE_PASSWORD:}
              pool:
                maxSize: ${'$'}{DB_POOL_MAX_SIZE:20}
                minIdle: ${'$'}{DB_POOL_MIN_IDLE:5}

            logging:
              level: ${'$'}{LOG_LEVEL:INFO}
              file: ${'$'}{LOG_FILE:/var/log/katalyst.log}

            features:
              analytics: ${'$'}{FEATURE_ANALYTICS:false}
              caching: ${'$'}{FEATURE_CACHING:true}
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val app = result["app"] as Map<String, Any>
        assertEquals("Katalyst", app["name"])
        assertEquals("1.0.0", app["version"])
        assertEquals("development", app["environment"])

        @Suppress("UNCHECKED_CAST")
        val server = result["server"] as Map<String, Any>
        assertEquals("0.0.0.0", server["host"])
        assertEquals("8080", server["port"])

        @Suppress("UNCHECKED_CAST")
        val database = result["database"] as Map<String, Any>
        assertEquals("jdbc:postgresql://localhost:5432/katalyst", database["url"])
        assertEquals("postgres", database["username"])

        @Suppress("UNCHECKED_CAST")
        val pool = database["pool"] as Map<String, Any>
        assertEquals("20", pool["maxSize"])
        assertEquals("5", pool["minIdle"])

        @Suppress("UNCHECKED_CAST")
        val logging = result["logging"] as Map<String, Any>
        assertEquals("INFO", logging["level"])

        @Suppress("UNCHECKED_CAST")
        val features = result["features"] as Map<String, Any>
        assertEquals("false", features["analytics"])
        assertEquals("true", features["caching"])
    }

    @Test
    fun `multi-service configuration with profiles`() {
        // Given
        val yaml = """
            services:
              - name: auth-service
                url: ${'$'}{AUTH_SERVICE_URL:http://localhost:8081}
                timeout: 5000
                retries: 3
              - name: payment-service
                url: ${'$'}{PAYMENT_SERVICE_URL:http://localhost:8082}
                timeout: 10000
                retries: 5
              - name: notification-service
                url: ${'$'}{NOTIFICATION_SERVICE_URL:http://localhost:8083}
                timeout: 3000
                retries: 2
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val services = result["services"] as List<Map<String, Any>>
        assertEquals(3, services.size)

        assertEquals("auth-service", services[0]["name"])
        assertEquals("http://localhost:8081", services[0]["url"])
        assertEquals(5000, services[0]["timeout"])
        assertEquals(3, services[0]["retries"])

        assertEquals("payment-service", services[1]["name"])
        assertEquals("http://localhost:8082", services[1]["url"])
        assertEquals(10000, services[1]["timeout"])
        assertEquals(5, services[1]["retries"])

        assertEquals("notification-service", services[2]["name"])
        assertEquals("http://localhost:8083", services[2]["url"])
        assertEquals(3000, services[2]["timeout"])
        assertEquals(2, services[2]["retries"])
    }

    @Test
    fun `JWT and security configuration`() {
        // Given
        val yaml = """
            jwt:
              secret: ${'$'}{JWT_SECRET:change-me-in-production}
              issuer: ${'$'}{JWT_ISSUER:katalyst}
              audience: ${'$'}{JWT_AUDIENCE:katalyst-api}
              expiresIn: ${'$'}{JWT_EXPIRES_IN:3600}
              algorithm: HS256

            security:
              cors:
                enabled: true
                origins:
                  - http://localhost:3000
                  - https://app.example.com
                methods:
                  - GET
                  - POST
                  - PUT
                  - DELETE
              rateLimit:
                enabled: ${'$'}{RATE_LIMIT_ENABLED:true}
                requests: ${'$'}{RATE_LIMIT_REQUESTS:100}
                window: ${'$'}{RATE_LIMIT_WINDOW:60}
        """.trimIndent()

        // When
        val result = parseWithoutSystemEnv(yaml)

        // Then
        @Suppress("UNCHECKED_CAST")
        val jwt = result["jwt"] as Map<String, Any>
        assertEquals("change-me-in-production", jwt["secret"])
        assertEquals("katalyst", jwt["issuer"])
        assertEquals("katalyst-api", jwt["audience"])
        assertEquals("3600", jwt["expiresIn"])
        assertEquals("HS256", jwt["algorithm"])

        @Suppress("UNCHECKED_CAST")
        val security = result["security"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val cors = security["cors"] as Map<String, Any>
        assertEquals(true, cors["enabled"])

        @Suppress("UNCHECKED_CAST")
        val origins = cors["origins"] as List<String>
        assertEquals(2, origins.size)
        assertEquals("http://localhost:3000", origins[0])
        assertEquals("https://app.example.com", origins[1])

        @Suppress("UNCHECKED_CAST")
        val rateLimit = security["rateLimit"] as Map<String, Any>
        assertEquals("true", rateLimit["enabled"])
        assertEquals("100", rateLimit["requests"])
        assertEquals("60", rateLimit["window"])
    }
}
