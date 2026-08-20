package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.config.provider.binderfixtures.AlphaConfig
import io.github.darkryh.katalyst.config.provider.binderfixtures.BetaBinding
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [ConfigBinder] — the reflective annotation-driven config binder (change A).
 *
 * Covers: kebab-case key derivation from [ConfigPrefix], Kotlin defaults for absent keys,
 * fail-fast on missing required keys (naming the key), [ConfigKey] absolute overrides,
 * nullable-property null when absent, fail-fast on present-but-malformed values,
 * data-class `init { require(...) }` validation, the [ConfigBinding] escape hatch, and
 * classpath discovery via [ConfigBinder.bindAll].
 */
class ConfigBinderTest {

    private class FakeConfigProvider(private val data: Map<String, Any?> = emptyMap()) : ConfigProvider {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, defaultValue: T?): T? = data[key] as? T ?: defaultValue
        override fun getString(key: String, default: String): String = data[key]?.toString() ?: default
        override fun getInt(key: String, default: Int): Int = (data[key] as? Number)?.toInt() ?: default
        override fun getLong(key: String, default: Long): Long = (data[key] as? Number)?.toLong() ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = (data[key] as? Boolean) ?: default
        override fun getList(key: String, default: List<String>): List<String> = default
        override fun hasKey(key: String): Boolean = data.containsKey(key)
        override fun getAllKeys(): Set<String> = data.keys
    }

    @ConfigPrefix("mail")
    data class MailConfig(
        val webhookSecret: String = "",
        val replaySecret: String = "",
        val maxRetries: Int = 3,
    )

    @ConfigPrefix("db")
    data class DbConfig(
        val url: String,
        @ConfigKey("custom.timeout.ms") val timeoutMs: Long = 1000,
        val poolSize: Int?,
    )

    @ConfigPrefix("v")
    data class ValidatedConfig(val port: Int = 0) {
        init { require(port in 1..65535) { "port must be in 1..65535, was $port" } }
    }

    class DecodedBinding(provider: ConfigProvider) : ConfigBinding {
        val token: String = provider.requiredString("secret.token")
    }

    @Test
    fun `binds prefixed properties to kebab-case keys`() {
        val provider = FakeConfigProvider(
            mapOf(
                "mail.webhook-secret" to "ws",
                "mail.replay-secret" to "rs",
                "mail.max-retries" to 5,
            )
        )
        val config = ConfigBinder.bind(MailConfig::class, provider) as MailConfig
        assertEquals(MailConfig("ws", "rs", 5), config)
    }

    @Test
    fun `uses kotlin defaults when keys are absent`() {
        val config = ConfigBinder.bind(MailConfig::class, FakeConfigProvider()) as MailConfig
        assertEquals(MailConfig(webhookSecret = "", replaySecret = "", maxRetries = 3), config)
    }

    @Test
    fun `throws ConfigException naming the key when a required key is missing`() {
        val ex = assertFailsWith<ConfigException> {
            ConfigBinder.bind(DbConfig::class, FakeConfigProvider(mapOf("custom.timeout.ms" to 42L)))
        }
        assertTrue(ex.message.orEmpty().contains("db.url"), "message should name the missing key: ${ex.message}")
    }

    @Test
    fun `ConfigKey overrides the class prefix`() {
        val provider = FakeConfigProvider(mapOf("db.url" to "jdbc://x", "custom.timeout.ms" to 42L))
        val config = ConfigBinder.bind(DbConfig::class, provider) as DbConfig
        assertEquals("jdbc://x", config.url)
        assertEquals(42L, config.timeoutMs) // proves custom.timeout.ms was used, not db.timeout-ms
    }

    @Test
    fun `nullable property without default is null when key absent`() {
        val config = ConfigBinder.bind(DbConfig::class, FakeConfigProvider(mapOf("db.url" to "u"))) as DbConfig
        assertNull(config.poolSize)
    }

    @Test
    fun `throws ConfigException when a present value is malformed`() {
        val provider = FakeConfigProvider(mapOf("mail.max-retries" to "not-a-number"))
        assertFailsWith<ConfigException> { ConfigBinder.bind(MailConfig::class, provider) }
    }

    @Test
    fun `throws ConfigException naming the key when a present String key resolves to null`() {
        // hasKey() reports "db.url" as present (the map contains the key), but get() resolves
        // to null. Int/Long/Boolean already fail fast on this via requireRaw(); the String path
        // must now do the same instead of silently binding null into a non-nullable parameter.
        val data: Map<String, Any?> = mapOf("db.url" to null, "custom.timeout.ms" to 42L)
        val ex = assertFailsWith<ConfigException> {
            ConfigBinder.bind(DbConfig::class, FakeConfigProvider(data))
        }
        assertTrue(ex.message.orEmpty().contains("db.url"), "message should name the key: ${ex.message}")
    }

    @Test
    fun `still accepts a legitimately blank required String value`() {
        // The null-guard added for the case above must not reject genuine empty/blank strings -
        // only an actual null raw value is a failure.
        val provider = FakeConfigProvider(mapOf("mail.webhook-secret" to "", "mail.replay-secret" to ""))
        val config = ConfigBinder.bind(MailConfig::class, provider) as MailConfig
        assertEquals("", config.webhookSecret)
    }

    @Test
    fun `runs data class init validation and surfaces failure as ConfigException`() {
        val provider = FakeConfigProvider(mapOf("v.port" to 70000))
        val ex = assertFailsWith<ConfigException> { ConfigBinder.bind(ValidatedConfig::class, provider) }
        assertTrue(ex.message.orEmpty().contains("ValidatedConfig"), "should name the type: ${ex.message}")
    }

    @Test
    fun `accepts values that pass init validation`() {
        val config = ConfigBinder.bind(ValidatedConfig::class, FakeConfigProvider(mapOf("v.port" to 8080))) as ValidatedConfig
        assertEquals(8080, config.port)
    }

    @Test
    fun `bindBinding instantiates a ConfigBinding with the provider`() {
        val binding = ConfigBinder.bindBinding(DecodedBinding::class, FakeConfigProvider(mapOf("secret.token" to "abc")))
        assertEquals("abc", (binding as DecodedBinding).token)
    }

    @Test
    fun `bindAll discovers annotated classes and ConfigBinding implementors`() {
        val provider = FakeConfigProvider(
            mapOf("alpha.name" to "x", "alpha.size" to 99, "beta.flag" to true)
        )
        val bound = ConfigBinder.bindAll(
            arrayOf("io.github.darkryh.katalyst.config.provider.binderfixtures"),
            provider,
        )
        val alpha = bound[AlphaConfig::class] as? AlphaConfig
        val beta = bound[BetaBinding::class] as? BetaBinding
        assertEquals(AlphaConfig(name = "x", size = 99), alpha)
        assertEquals(true, beta?.flag)
    }

    @Test
    fun `bindAll(types, provider) produces identical bindings to bindAll(scanPackages, provider)`() {
        val provider = FakeConfigProvider(
            mapOf("alpha.name" to "x", "alpha.size" to 99, "beta.flag" to true)
        )
        val scanPackages = arrayOf("io.github.darkryh.katalyst.config.provider.binderfixtures")

        // Simulates a caller (e.g. DI bootstrap) that already ran discoverConfigTypes and hands
        // the resulting Set straight to bindAll, instead of letting bindAll re-scan internally.
        val preDiscoveredTypes = ConfigBinder.discoverConfigTypes(scanPackages)
        val fromPreDiscoveredTypes = ConfigBinder.bindAll(preDiscoveredTypes, provider)
        val fromScanPackages = ConfigBinder.bindAll(scanPackages, provider)

        assertEquals(fromScanPackages.keys, fromPreDiscoveredTypes.keys)
        assertEquals(fromScanPackages[AlphaConfig::class], fromPreDiscoveredTypes[AlphaConfig::class])
        assertEquals(
            (fromScanPackages[BetaBinding::class] as BetaBinding).flag,
            (fromPreDiscoveredTypes[BetaBinding::class] as BetaBinding).flag,
        )
    }

    @Test
    fun `bindAll(types, provider) binds directly from the given set without its own classpath scan`() {
        // MailConfig lives in this test's own package (not the binderfixtures package scanned by
        // discoverConfigTypes above) and is handed in as a manually-assembled Set - not the
        // result of any scan. This overload has no scanPackages parameter at all, so there is
        // nothing for it to (re-)scan: it can only bind from what was passed in.
        val provider = FakeConfigProvider(mapOf("mail.webhook-secret" to "ws", "mail.replay-secret" to "rs"))
        val bound = ConfigBinder.bindAll(setOf(MailConfig::class), provider)
        val mail = bound[MailConfig::class] as? MailConfig
        assertEquals(MailConfig(webhookSecret = "ws", replaySecret = "rs", maxRetries = 3), mail)
    }
}
