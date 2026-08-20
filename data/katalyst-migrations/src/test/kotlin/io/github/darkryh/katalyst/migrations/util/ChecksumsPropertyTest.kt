package io.github.darkryh.katalyst.migrations.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Property-based (dependency-free generative) tests for [hashStatements].
 *
 * Migration checksums must be deterministic, sensitive to any change, and order-dependent — these
 * are the invariants migration integrity relies on. Generates many random statement lists and
 * asserts the properties hold universally.
 */
class ChecksumsPropertyTest {

    @Test
    fun `hashing is deterministic and well-formed sha-256`() {
        repeat(1_000) {
            val statements = randomStatements()
            val a = hashStatements(statements)
            val b = hashStatements(statements.toList()) // independent copy
            assertEquals(a, b, "hash not deterministic for $statements")
            assertEquals(64, a.length, "expected 64 hex chars (SHA-256)")
            assertTrue(a.all { it in "0123456789abcdef" }, "non-hex char in '$a'")
        }
    }

    @Test
    fun `any change to a statement changes the checksum`() {
        repeat(1_000) {
            val statements = randomStatements(minSize = 1)
            val original = hashStatements(statements)

            val idx = Random.nextInt(statements.size)
            val mutated = statements.toMutableList().apply { this[idx] = this[idx] + "X" }
            assertNotEquals(original, hashStatements(mutated), "checksum unchanged after mutating $statements")
        }
    }

    @Test
    fun `statement order is significant`() {
        repeat(1_000) {
            val statements = randomStatements(minSize = 2)
            val reversed = statements.reversed()
            if (statements == reversed) return@repeat // palindromic ordering: skip
            assertNotEquals(
                hashStatements(statements),
                hashStatements(reversed),
                "reordering did not change checksum for $statements"
            )
        }
    }

    private fun randomStatements(minSize: Int = 0): List<String> {
        val size = Random.nextInt(minSize.coerceAtLeast(0), 8)
        return (0 until size).map { randomToken() }
    }

    private fun randomToken(): String {
        val len = Random.nextInt(1, 12)
        val alphabet = "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_(),;"
        return (0 until len).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
