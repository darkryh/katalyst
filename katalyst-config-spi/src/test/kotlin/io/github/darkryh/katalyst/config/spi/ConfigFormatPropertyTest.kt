package io.github.darkryh.katalyst.config.spi

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based tests for [ConfigFormat] extension resolution.
 *
 * Instead of a handful of fixed examples, these generate many randomized inputs and assert the
 * mapping invariants hold for all of them (case-insensitivity, exhaustive "unknown -> null"). This
 * is dependency-free generative testing; a future migration to kotest-property is noted in
 * TESTING_STRATEGY.md (Phase 5).
 */
class ConfigFormatPropertyTest {

    private val yamlExts = listOf("yaml", "yml")
    private val hoconExts = listOf("conf", "hocon")
    private val knownExts = yamlExts + hoconExts

    @Test
    fun `known extensions map regardless of case`() {
        repeat(500) {
            for (ext in knownExts) {
                val scrambled = ext.randomCase()
                val expected = if (ext in yamlExts) ConfigFormat.YAML else ConfigFormat.HOCON
                assertEquals(expected, ConfigFormat.fromExtension(scrambled), "failed for '$scrambled'")
            }
        }
    }

    @Test
    fun `arbitrary unknown extensions always resolve to null`() {
        repeat(2_000) {
            val candidate = randomToken()
            if (candidate.lowercase() in knownExts) return@repeat // skip accidental hits
            assertNull(ConfigFormat.fromExtension(candidate), "expected null for '$candidate'")
        }
    }

    @Test
    fun `fromExtension is total - it never throws for any string`() {
        repeat(2_000) {
            val s = randomToken(allowEmpty = true)
            // Must return a value or null, never blow up.
            val result = runCatching { ConfigFormat.fromExtension(s) }
            assertTrue(result.isSuccess, "threw for input '$s'")
        }
    }

    private fun String.randomCase(): String =
        map { if (Random.nextBoolean()) it.uppercaseChar() else it.lowercaseChar() }.joinToString("")

    private fun randomToken(allowEmpty: Boolean = false): String {
        val len = Random.nextInt(if (allowEmpty) 0 else 1, 8)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-"
        return (0 until len).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
