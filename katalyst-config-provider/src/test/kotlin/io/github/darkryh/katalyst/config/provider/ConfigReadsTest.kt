package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.test.*

/**
 * Tests for the nullable-first configuration read API in ConfigReads.kt.
 *
 * Covers the surviving extensions only:
 * - requiredString / requiredInt / requiredLong / requiredBoolean
 * - stringOrNull / intOrNull / longOrNull / booleanOrNull
 *
 * Key semantics under test:
 * - requiredX throws (naming the key) when missing / blank (string) / malformed.
 * - xOrNull returns null when the key is ABSENT.
 * - xOrNull THROWS when the key is PRESENT but malformed (no silent fallback).
 */
class ConfigReadsTest {

    class FakeConfigProvider(private val data: Map<String, Any> = emptyMap()) : ConfigProvider, Component {
        override fun <T> get(key: String, defaultValue: T?): T? {
            @Suppress("UNCHECKED_CAST")
            return data[key] as? T ?: defaultValue
        }

        override fun getString(key: String, default: String): String = data[key]?.toString() ?: default

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
            return when (value) {
                is List<*> -> value.map { it.toString() }
                else -> default
            }
        }

        override fun hasKey(key: String): Boolean = data.containsKey(key)

        override fun getAllKeys(): Set<String> = data.keys
    }

    // ---------- requiredString ----------

    @Test
    fun `requiredString returns value when present`() {
        val provider = FakeConfigProvider(mapOf("key" to "value"))
        assertEquals("value", provider.requiredString("key"))
    }

    @Test
    fun `requiredString throws naming key when missing`() {
        val provider = FakeConfigProvider()
        val ex = assertFailsWith<ConfigException> { provider.requiredString("db.url") }
        assertTrue(ex.message!!.contains("db.url"))
    }

    @Test
    fun `requiredString throws when blank`() {
        val provider = FakeConfigProvider(mapOf("key" to "   "))
        assertFailsWith<ConfigException> { provider.requiredString("key") }
    }

    // ---------- stringOrNull ----------

    @Test
    fun `stringOrNull returns value when present`() {
        val provider = FakeConfigProvider(mapOf("key" to "value"))
        assertEquals("value", provider.stringOrNull("key"))
    }

    @Test
    fun `stringOrNull returns null when absent`() {
        assertNull(FakeConfigProvider().stringOrNull("missing"))
    }

    // ---------- requiredInt ----------

    @Test
    fun `requiredInt parses value`() {
        val provider = FakeConfigProvider(mapOf("port" to "8080"))
        assertEquals(8080, provider.requiredInt("port"))
    }

    @Test
    fun `requiredInt throws when missing`() {
        val ex = assertFailsWith<ConfigException> { FakeConfigProvider().requiredInt("port") }
        assertTrue(ex.message!!.contains("port"))
    }

    @Test
    fun `requiredInt throws when malformed`() {
        val provider = FakeConfigProvider(mapOf("port" to "abc"))
        assertFailsWith<ConfigException> { provider.requiredInt("port") }
    }

    // ---------- intOrNull ----------

    @Test
    fun `intOrNull returns null when absent`() {
        assertNull(FakeConfigProvider().intOrNull("port"))
    }

    @Test
    fun `intOrNull parses present value`() {
        assertEquals(42, FakeConfigProvider(mapOf("port" to 42)).intOrNull("port"))
    }

    @Test
    fun `intOrNull throws when present but malformed`() {
        val provider = FakeConfigProvider(mapOf("port" to "eighty"))
        assertFailsWith<ConfigException> { provider.intOrNull("port") }
    }

    // ---------- requiredLong ----------

    @Test
    fun `requiredLong parses value`() {
        assertEquals(1234L, FakeConfigProvider(mapOf("ts" to "1234")).requiredLong("ts"))
    }

    @Test
    fun `requiredLong throws when missing`() {
        assertFailsWith<ConfigException> { FakeConfigProvider().requiredLong("ts") }
    }

    @Test
    fun `requiredLong throws when malformed`() {
        assertFailsWith<ConfigException> { FakeConfigProvider(mapOf("ts" to "soon")).requiredLong("ts") }
    }

    // ---------- longOrNull ----------

    @Test
    fun `longOrNull returns null when absent`() {
        assertNull(FakeConfigProvider().longOrNull("ts"))
    }

    @Test
    fun `longOrNull throws when present but malformed`() {
        val provider = FakeConfigProvider(mapOf("ts" to "soon"))
        assertFailsWith<ConfigException> { provider.longOrNull("ts") }
    }

    // ---------- requiredBoolean ----------

    @Test
    fun `requiredBoolean accepts multiple formats`() {
        assertTrue(FakeConfigProvider(mapOf("f" to "yes")).requiredBoolean("f"))
        assertFalse(FakeConfigProvider(mapOf("f" to "off")).requiredBoolean("f"))
        assertTrue(FakeConfigProvider(mapOf("f" to true)).requiredBoolean("f"))
        assertTrue(FakeConfigProvider(mapOf("f" to "enabled")).requiredBoolean("f"))
    }

    @Test
    fun `requiredBoolean throws when missing`() {
        assertFailsWith<ConfigException> { FakeConfigProvider().requiredBoolean("f") }
    }

    @Test
    fun `requiredBoolean throws when malformed`() {
        assertFailsWith<ConfigException> { FakeConfigProvider(mapOf("f" to "maybe")).requiredBoolean("f") }
    }

    // ---------- booleanOrNull ----------

    @Test
    fun `booleanOrNull returns null when absent`() {
        assertNull(FakeConfigProvider().booleanOrNull("f"))
    }

    @Test
    fun `booleanOrNull parses present value`() {
        assertEquals(true, FakeConfigProvider(mapOf("f" to "on")).booleanOrNull("f"))
        assertEquals(false, FakeConfigProvider(mapOf("f" to "0")).booleanOrNull("f"))
    }

    @Test
    fun `booleanOrNull throws when present but malformed`() {
        val provider = FakeConfigProvider(mapOf("f" to "maybe"))
        assertFailsWith<ConfigException> { provider.booleanOrNull("f") }
    }
}
