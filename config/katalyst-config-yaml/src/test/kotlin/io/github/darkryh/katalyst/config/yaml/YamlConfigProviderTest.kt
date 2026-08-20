package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.config.provider.booleanOrNull
import io.github.darkryh.katalyst.config.provider.intOrNull
import io.github.darkryh.katalyst.config.provider.longOrNull
import io.github.darkryh.katalyst.config.provider.requiredBoolean
import io.github.darkryh.katalyst.config.provider.requiredInt
import io.github.darkryh.katalyst.config.provider.requiredLong
import io.github.darkryh.katalyst.config.provider.stringOrNull
import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.test.*

/**
 * Comprehensive tests for the real [YamlConfigurationSource].
 *
 * Tests cover:
 * - Component interface implementation
 * - ConfigProvider interface implementation
 * - Dot notation path navigation
 * - Type-specific getters (getString, getInt, getLong, getBoolean, getList)
 * - Type conversion scenarios
 * - hasKey() and getAllKeys() operations
 * - Nested map navigation
 * - Required-key validation, precedence and environment substitution
 * - Edge cases and error scenarios
 *
 * **Testing Approach:**
 * Every test constructs the production [YamlConfigurationSource] and drives it through its
 * [YamlProfileLoader] seam against classpath fixtures in `src/test/resources`
 * (`application-provider*.yaml`, `application-precedence*.yaml`, ...). Nothing here
 * re-implements the class under test, so deleting or breaking production code turns this
 * suite red.
 */
class YamlConfigurationSourceTest {

    private companion object {
        /**
         * Profile variable used only by this suite. The loader's real default is
         * `KATALYST_PROFILE`; overriding it keeps a developer's ambient environment from
         * steering the fixtures.
         */
        const val PROFILE_ENV_VAR = "KATALYST_CONFIG_PROVIDER_TEST_PROFILE"

        /** Rich fixture covering every conversion branch. */
        const val FULL = "application-provider.yaml"

        /** Required keys and nothing else. */
        const val MINIMAL = "application-provider-minimal.yaml"

        /** Required keys plus `app.name`. */
        const val SPARSE = "application-provider-sparse.yaml"
    }

    /**
     * Build the production provider over a classpath fixture.
     *
     * @param env fake environment used for `${VAR}` substitution; empty means "nothing set",
     *            which keeps the fixtures deterministic regardless of the host environment.
     */
    private fun sourceFor(
        baseConfigFile: String,
        profile: String? = null,
        env: Map<String, String> = emptyMap()
    ): YamlConfigurationSource = YamlConfigurationSource(
        profileLoader = YamlProfileLoader(
            profileEnvVar = PROFILE_ENV_VAR,
            baseConfigFile = baseConfigFile,
            environmentReader = { name -> if (name == PROFILE_ENV_VAR) profile else null },
            propertyReader = { null }
        ),
        substitutor = EnvironmentVariableSubstitutor({ name -> env[name] })
    )

    // ========== COMPONENT INTERFACE TESTS ==========

    @Test
    fun `YamlConfigurationSource should implement Component interface`() {
        // Given
        val provider: Any = sourceFor(MINIMAL)

        // Then
        assertTrue(provider is Component)
    }

    @Test
    fun `YamlConfigurationSource should implement ConfigProvider interface`() {
        // Given
        val provider: Any = sourceFor(MINIMAL)

        // Then
        assertTrue(provider is ConfigProvider)
    }

    // ========== DOT NOTATION PATH NAVIGATION TESTS ==========

    @Test
    fun `navigatePath should handle simple keys`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals("Katalyst", provider.getString("name"))
        assertEquals("1.0.0", provider.getString("version"))
        assertEquals(8080, provider.getInt("port"))
    }

    @Test
    fun `navigatePath should handle nested paths with dot notation`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals("jdbc:postgresql://localhost:5432/katalyst", provider.getString("database.url"))
        assertEquals("postgres", provider.getString("database.username"))
        assertEquals("secret", provider.getString("database.password"))
    }

    @Test
    fun `navigatePath should handle deeply nested paths`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(10, provider.getInt("app.modules.database.config.pool.size"))
        assertEquals(30000, provider.getInt("app.modules.database.config.pool.timeout"))
    }

    @Test
    fun `navigatePath should return null for non-existent keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertNull(provider.get<String>("non.existent"))
        assertEquals("default", provider.getString("non.existent", "default"))
    }

    @Test
    fun `navigatePath should return null for partial paths`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertNull(provider.get<String>("database.url.invalid"))
        assertNull(provider.get<String>("database.nonexistent"))
    }

    // ========== getString() TESTS ==========

    @Test
    fun `getString should return string values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals("Katalyst", provider.getString("name"))
    }

    @Test
    fun `getString should return default for missing keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertEquals("default", provider.getString("missing", "default"))
    }

    @Test
    fun `getString should convert non-string values to strings`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals("8080", provider.getString("conversions.port"))
        assertEquals("true", provider.getString("conversions.enabled"))
        assertEquals("1.5", provider.getString("conversions.version"))
    }

    @Test
    fun `stringOrNull should return nested YAML value or null`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals("secret", provider.stringOrNull("database.password"))
        assertNull(provider.stringOrNull("database.missing"))
    }

    // ========== getInt() TESTS ==========

    @Test
    fun `getInt should return integer values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(8080, provider.getInt("port"))
    }

    @Test
    fun `getInt should return default for missing keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertEquals(9999, provider.getInt("missing", 9999))
    }

    @Test
    fun `getInt should parse string to int`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(5000, provider.getInt("numbers.stringInt"))
    }

    @Test
    fun `getInt should return default for unparseable strings`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(100, provider.getInt("numbers.notANumber", 100))
    }

    @Test
    fun `intOrNull should return null for missing YAML values`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertNull(provider.intOrNull("missing"))
    }

    @Test
    fun `intOrNull should fail fast for malformed YAML values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFailsWith<ConfigException> {
            provider.intOrNull("numbers.portWithUnit")
        }
    }

    @Test
    fun `requiredInt should throw for malformed yaml values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        val exception = assertFailsWith<ConfigException> {
            provider.requiredInt("malformed.port")
        }
        assertTrue(exception.message?.contains("must be a valid integer") == true)
        assertTrue(exception.message?.contains("eighty") == true)
    }

    @Test
    fun `intOrNull should fail fast for malformed yaml values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFailsWith<ConfigException> { provider.intOrNull("malformed.port") }
    }

    @Test
    fun `getInt should convert other number types`() {
        // Given
        val provider = sourceFor(FULL)

        // Then - a YAML double is truncated...
        assertEquals(42, provider.getInt("numbers.double"))
        // ...and a YAML long wider than Int is silently narrowed by Number.toInt().
        assertEquals(9876543210L.toInt(), provider.getInt("numbers.long"))
    }

    // ========== getLong() TESTS ==========

    @Test
    fun `getLong should return long values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(1234567890123L, provider.getLong("numbers.timestamp"))
    }

    @Test
    fun `getLong should return default for missing keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertEquals(999L, provider.getLong("missing", 999L))
    }

    @Test
    fun `getLong should parse string to long`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(9876543210L, provider.getLong("numbers.stringLong"))
    }

    @Test
    fun `getLong should convert int to long`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(42L, provider.getLong("numbers.count"))
    }

    @Test
    fun `requiredLong should throw for malformed yaml values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        val exception = assertFailsWith<ConfigException> {
            provider.requiredLong("timeouts.shutdown")
        }
        assertTrue(exception.message?.contains("must be a valid long") == true)
        assertTrue(exception.message?.contains("30s") == true)
    }

    @Test
    fun `longOrNull should fail fast for malformed yaml values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFailsWith<ConfigException> { provider.longOrNull("timeouts.shutdown") }
    }

    @Test
    fun `longOrNull should return null for missing YAML values`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertNull(provider.longOrNull("missing"))
    }

    @Test
    fun `longOrNull should fail fast for malformed YAML values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFailsWith<ConfigException> {
            provider.longOrNull("timeouts.shutdown")
        }
    }

    // ========== getBoolean() TESTS ==========

    @Test
    fun `getBoolean should return boolean values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertTrue(provider.getBoolean("features.caching"))
        assertFalse(provider.getBoolean("features.analytics"))
    }

    @Test
    fun `getBoolean should return default for missing keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertTrue(provider.getBoolean("missing", true))
        assertFalse(provider.getBoolean("missing", false))
    }

    @Test
    fun `getBoolean should parse true variations case-insensitively`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertTrue(provider.getBoolean("booleans.trueWord"))
        assertTrue(provider.getBoolean("booleans.yesWord"))
        assertTrue(provider.getBoolean("booleans.onWord"))
        assertTrue(provider.getBoolean("booleans.oneDigit"))
    }

    @Test
    fun `getBoolean should parse false variations case-insensitively`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFalse(provider.getBoolean("booleans.falseWord"))
        assertFalse(provider.getBoolean("booleans.noWord"))
        assertFalse(provider.getBoolean("booleans.offWord"))
        assertFalse(provider.getBoolean("booleans.zeroDigit"))
    }

    @Test
    fun `getBoolean should return default for unparseable values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertTrue(provider.getBoolean("booleans.maybe", true))
        assertFalse(provider.getBoolean("booleans.maybe", false))
    }

    @Test
    fun `requiredBoolean should throw for malformed yaml values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        val exception = assertFailsWith<ConfigException> {
            provider.requiredBoolean("malformed.analytics")
        }
        assertTrue(exception.message?.contains("must be a valid boolean") == true)
        assertTrue(exception.message?.contains("sometimes") == true)
    }

    @Test
    fun `booleanOrNull should return null for missing YAML values`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertNull(provider.booleanOrNull("features.analytics"))
    }

    @Test
    fun `booleanOrNull should fail fast for malformed YAML values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFailsWith<ConfigException> {
            provider.booleanOrNull("malformed.analytics")
        }
    }

    @Test
    fun `getBoolean should convert numbers to boolean`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFalse(provider.getBoolean("numbers.zero"))
        assertTrue(provider.getBoolean("numbers.one"))
        assertTrue(provider.getBoolean("numbers.negative"))
    }

    // ========== getList() TESTS ==========

    @Test
    fun `getList should return list of strings`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        val servers = provider.getList("servers")
        assertEquals(3, servers.size)
        assertEquals(listOf("server1", "server2", "server3"), servers)
    }

    @Test
    fun `getList should return default for missing keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        val default = listOf("default1", "default2")
        assertEquals(default, provider.getList("missing", default))
    }

    @Test
    fun `getList should parse comma-separated string`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(listOf("tag1", "tag2", "tag3"), provider.getList("tags"))
    }

    @Test
    fun `getList should trim whitespace from comma-separated values`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(listOf("item1", "item2", "item3"), provider.getList("items"))
    }

    @Test
    fun `getList should convert non-string list items to strings`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertEquals(listOf("1", "2", "3"), provider.getList("mixed"))
    }

    @Test
    fun `getList should handle null values in lists`() {
        // Given
        val provider = sourceFor(FULL)

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
        val provider = sourceFor(FULL)

        // Then
        assertTrue(provider.hasKey("name"))
    }

    @Test
    fun `hasKey should return false for missing keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // Then
        assertFalse(provider.hasKey("missing"))
    }

    @Test
    fun `hasKey should work with nested paths`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertTrue(provider.hasKey("database.url"))
        assertFalse(provider.hasKey("database.absent"))
    }

    // ========== getAllKeys() TESTS ==========

    @Test
    fun `getAllKeys should return all configuration keys`() {
        // Given
        val provider = sourceFor(MINIMAL)

        // When
        val keys = provider.getAllKeys()

        // Then
        assertEquals(
            setOf(
                "ktor",
                "ktor.deployment",
                "ktor.deployment.host",
                "ktor.deployment.port",
                "ktor.deployment.shutdownGracePeriod",
                "ktor.deployment.shutdownTimeout",
                "database",
                "database.url",
                "database.username",
                "database.driver"
            ),
            keys
        )
    }

    @Test
    fun `getAllKeys should return nested keys with dot notation`() {
        // Given
        val provider = sourceFor(FULL)

        // When
        val keys = provider.getAllKeys()

        // Then
        assertTrue(keys.contains("database"))
        assertTrue(keys.contains("database.url"))
        assertTrue(keys.contains("database.username"))
        assertTrue(keys.contains("database.pool"))
        assertTrue(keys.contains("database.pool.maxSize"))
        assertTrue(keys.contains("database.pool.timeout"))
    }

    @Test
    fun `empty configuration is rejected instead of yielding an empty key set`() {
        // Given - a file that parses to nothing at all
        val exception = assertFailsWith<ConfigException> {
            sourceFor("application-provider-empty.yaml")
        }

        // Then - the source fails fast rather than handing back an empty configuration
        assertTrue(exception.message?.contains("Missing required configuration keys") == true)
        assertTrue(exception.message?.contains("database.url") == true)
    }

    // ========== TYPE CONVERSION ERROR TESTS ==========

    @Test
    fun `get should throw ConfigException for type mismatch`() {
        // Given
        val provider = sourceFor(FULL)

        // Then
        assertFailsWith<ConfigException> {
            provider.get<Int>("name", 42)
        }
    }

    // ========== REQUIRED KEY VALIDATION ==========

    @Test
    fun `construction fails fast when a required key is missing`() {
        // Given - fixture deliberately omits database.url and the ktor deployment block
        val exception = assertFailsWith<ConfigException> {
            sourceFor("application-missing-required.yaml")
        }

        // Then
        assertTrue(exception.message?.contains("ktor.deployment.host") == true)
        assertTrue(exception.message?.contains("database.url") == true)
    }

    // ========== PRECEDENCE / SUBSTITUTION ==========

    @Test
    fun `precedence chain runs base file then profile file then placeholder default then typed read`() {
        // Given - base declares server.port=8080 and server.timeoutMs=1000;
        // the profile overrides server.port with "${KATALYST_TEST_PORT:9443}" and no env is set
        val provider = sourceFor("application-precedence.yaml", profile = "precedenceprofile")

        // Then - one assertion drives the whole chain: the profile wins over the base, the
        // placeholder falls back to its inline default, and the typed getter parses the string.
        assertEquals(9443, provider.getInt("server.port"))

        // And the surrounding layers behave accordingly
        assertEquals(1000, provider.getInt("server.timeoutMs")) // base-only key survives the merge
        assertEquals("profile-user", provider.getString("database.username")) // profile wins
        assertEquals("jdbc:h2:mem:precedence-base", provider.getString("database.url")) // base fallback
    }

    @Test
    fun `environment variable wins over the inline placeholder default`() {
        // Given
        val provider = sourceFor(
            "application-precedence.yaml",
            profile = "precedenceprofile",
            env = mapOf("KATALYST_TEST_PORT" to "7000", "KATALYST_TEST_DB_USER" to "env-user")
        )

        // Then
        assertEquals(7000, provider.getInt("server.port"))
        assertEquals("env-user", provider.getString("database.username"))
    }

    @Test
    fun `environment variables never override a literal file value`() {
        // Given - the KDoc used to claim env vars are a precedence layer above the files.
        // They are not: they only fill ${} placeholders the YAML author wrote.
        val provider = sourceFor(
            "application-precedence.yaml",
            env = mapOf(
                "DATABASE_URL" to "jdbc:postgresql://takeover:5432/db",
                "KATALYST_TEST_PORT" to "7000"
            )
        )

        // Then - database.url is a literal in the file, so the environment is ignored entirely
        assertEquals("jdbc:h2:mem:precedence-base", provider.getString("database.url"))
        assertEquals(8080, provider.getInt("server.port"))
    }

    @Test
    fun `a resolved value containing a literal placeholder is not substituted twice`() {
        // Given - KATALYST_TEST_LITERAL_PLACEHOLDER is exported by the module's test task as
        // the literal text `abc${d}ef`. Uses the default (System.getenv) substitutor on purpose:
        // the defect only appears when the same environment feeds both substitution passes.
        val provider = YamlConfigurationSource(
            profileLoader = YamlProfileLoader(
                profileEnvVar = PROFILE_ENV_VAR,
                baseConfigFile = "application-literal-placeholder.yaml",
                environmentReader = { null },
                propertyReader = { null }
            )
        )

        // Then - the password must survive verbatim. A second substitution pass would see the
        // `${d}` it just produced, find no variable `d`, and silently yield "abcef".
        assertEquals("abc\${d}ef", provider.getString("database.password"))
    }

    // ========== YAML 1.1 SCALAR COERCION TRAPS (pinned, not endorsed) ==========

    @Test
    fun `unquoted NO is read back as the boolean false not the string NO`() {
        // Given - the fixture says `region: NO`
        val provider = sourceFor(FULL)

        // Then - YAML 1.1 resolves it to a Boolean before Katalyst ever sees it
        assertEquals("false", provider.getString("coercion.region"))
        assertFalse(provider.getBoolean("coercion.region", default = true))
        // ...and a caller who asks for a String gets a hard type error rather than "NO"
        assertFailsWith<ConfigException> { provider.get("coercion.region", "NO") }
    }

    @Test
    fun `a numeric YAML key is reported by getAllKeys but is unreachable through navigatePath`() {
        // Given - the fixture says `2024: rate-for-2024` (an unquoted, therefore Int, key)
        val provider = sourceFor(FULL)

        // Then - getAllKeys() stringifies it, so the key looks present...
        assertTrue(provider.getAllKeys().contains("coercion.2024"))

        // ...but navigatePath() indexes the map with a String, so the value silently vanishes.
        assertFalse(provider.hasKey("coercion.2024"))
        assertEquals("MISSING", provider.getString("coercion.2024", "MISSING"))
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical database configuration scenario`() {
        // Given - Real-world database config
        val provider = sourceFor(FULL)

        // When/Then
        assertEquals("jdbc:postgresql://localhost:5432/katalyst", provider.getString("database.url"))
        assertEquals("postgres", provider.getString("database.username"))
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
        val provider = sourceFor(FULL)

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
        // Given - Sparse configuration (required keys plus app.name only)
        val provider = sourceFor(SPARSE)

        // When/Then - Using defaults for missing keys
        assertEquals("Katalyst", provider.getString("app.name"))
        assertEquals("1.0.0", provider.getString("app.version", "1.0.0"))
        assertEquals(8080, provider.getInt("server.port", 8080))
        assertEquals("localhost", provider.getString("server.host", "localhost"))
        assertFalse(provider.getBoolean("features.debug", false))
    }
}
