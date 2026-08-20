package io.github.darkryh.katalyst.database

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based (dependency-free generative) tests for [sanitizeJdbcUrl].
 *
 * The example-based suite alongside this one passed for years while the redactor leaked, because
 * every fixture password was alphanumeric and the broken pattern only failed on `/`, `@` and
 * space. Hand-picked cases cannot find that; generated ones find it on the first run.
 *
 * The universal invariant is simply: **whatever the password, it must not survive into the string
 * that gets logged.** Everything below is a restatement of that for a different shape of input.
 */
class JdbcUrlRedactorPropertyTest {

    /**
     * Characters PostgreSQL and MySQL accept in a password. `/`, `@`, `:` and space are the
     * interesting ones — each defeated the previous implementation.
     */
    private val passwordChars =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/@:!#\$%^&*()-_=+.,<>[]{} "

    /**
     * Minimum generated length.
     *
     * Containment is only a sound leak check when the secret cannot occur in the surrounding URL by
     * chance. A one-character password like `d` "appears" in `db.internal` and `3` in `5432`, which
     * says nothing about redaction. Eight characters drawn from this alphabet makes an accidental
     * match vanishingly unlikely, and the short degenerate shapes are covered separately below by
     * asserting the exact redacted output instead.
     */
    private val minGeneratedLength = 8

    private fun randomPassword(random: Random, minLength: Int = minGeneratedLength, maxLength: Int = 24): String =
        (1..random.nextInt(minLength, maxLength + 1))
            .map { passwordChars[random.nextInt(passwordChars.length)] }
            .joinToString("")

    @Test
    fun `a userinfo password never survives redaction, whatever characters it contains`() {
        val random = Random(20260818)
        repeat(1_000) {
            val password = randomPassword(random)
            val url = "jdbc:postgresql://appuser:$password@db.internal:5432/orders"

            val sanitized = sanitizeJdbcUrl(url)

            assertEquals(
                "jdbc:postgresql://db.internal:5432/orders",
                sanitized,
                "userinfo must be removed exactly, for password='$password'",
            )
            assertFalse(
                sanitized.contains(password),
                "password leaked for url='$url' -> sanitized='$sanitized'",
            )
        }
    }

    @Test
    fun `a password containing the characters that broke the old pattern is still redacted`() {
        // The regression cases, named explicitly so a failure reads as a diagnosis rather than a
        // random seed. Each of these was logged verbatim before the fix. Asserted as an exact
        // result rather than by containment: `//` and `@` are too short for containment to mean
        // anything against a URL that legitimately contains both.
        val expected = "jdbc:postgresql://db.internal:5432/orders"
        listOf(
            "pa/ss",          // '/' — terminated the old character class
            "p@ss",           // '@' — terminated the old character class
            "pass word",      // ' ' — terminated the old character class
            "a/b@c d",        // all three at once
            "//",             // degenerate
            "@",              // degenerate
            ":",              // degenerate — the userinfo separator itself
        ).forEach { password ->
            val url = "jdbc:postgresql://appuser:$password@db.internal:5432/orders"

            assertEquals(
                expected,
                sanitizeJdbcUrl(url),
                "password '$password' was not redacted to the expected form",
            )
        }
    }

    @Test
    fun `a password in a query parameter never survives, whatever it contains`() {
        val random = Random(11071988)
        repeat(1_000) {
            // '&' and ';' terminate a parameter value, so exclude them from this shape.
            val password = randomPassword(random).filter { it != '&' && it != ';' }
            if (password.length < minGeneratedLength) return@repeat
            val url = "jdbc:mysql://db.internal:3306/orders?user=root&password=$password"

            val sanitized = sanitizeJdbcUrl(url)

            assertEquals(
                "jdbc:mysql://db.internal:3306/orders?user=root&password=***",
                sanitized,
                "query password must be replaced exactly, for password='$password'",
            )
            assertFalse(
                sanitized.contains(password),
                "query password leaked for url='$url' -> sanitized='$sanitized'",
            )
        }
    }

    @Test
    fun `redaction is idempotent`() {
        val random = Random(4242)
        repeat(1_000) {
            val url = "jdbc:postgresql://u:${randomPassword(random)}@host:5432/db"
            val once = sanitizeJdbcUrl(url)

            assertTrue(
                sanitizeJdbcUrl(once) == once,
                "re-redacting changed the result: '$once' -> '${sanitizeJdbcUrl(once)}'",
            )
        }
    }

    @Test
    fun `a URL carrying no credentials is left intact`() {
        // Over-redaction has a cost too: an operator needs to recognise the target database.
        listOf(
            "jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1",
            "jdbc:postgresql://db.internal:5432/orders",
            "jdbc:mysql://localhost:3306/shop?useSSL=false",
        ).forEach { url ->
            assertTrue(
                sanitizeJdbcUrl(url) == url,
                "a credential-free URL must not be altered: '$url' -> '${sanitizeJdbcUrl(url)}'",
            )
        }
    }

    @Test
    fun `an at-sign inside a query value is not mistaken for userinfo`() {
        // The bound that keeps the greedy scan honest: an email-shaped parameter must not cause
        // the host to be swallowed.
        val url = "jdbc:postgresql://db.internal:5432/orders?user=ops@example.com"

        val sanitized = sanitizeJdbcUrl(url)

        assertTrue(sanitized.contains("db.internal"), "host was swallowed -> '$sanitized'")
        assertTrue(sanitized.contains("orders"), "database was swallowed -> '$sanitized'")
    }

    @Test
    fun `never throws and never returns empty, for any input`() {
        val random = Random(99)
        repeat(1_000) {
            val garbage = randomPassword(random, minLength = 0, maxLength = 60)

            val sanitized = sanitizeJdbcUrl(garbage)

            assertTrue(sanitized.isNotEmpty() || garbage.isEmpty(), "empty result for '$garbage'")
        }
        // Shapes with no scheme, no authority, or nothing but separators.
        listOf("", "//", "jdbc:", "not a url", "@@@", "jdbc:postgresql://", "://@")
            .forEach { sanitizeJdbcUrl(it) }
    }
}
