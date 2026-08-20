package io.github.darkryh.katalyst.database

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [sanitizeJdbcUrl].
 *
 * Verifies that credentials embedded in a JDBC URL - whether as userinfo
 * (`user:pass@host`) or as a query/attribute parameter (`password=...`) - never
 * survive into the sanitized string that gets logged.
 */
class JdbcUrlRedactorTest {

    @Test
    fun `should strip password embedded as userinfo`() {
        val url = "jdbc:postgresql://myuser:s3cr3tPassword@localhost:5432/mydb"

        val sanitized = sanitizeJdbcUrl(url)

        assertFalse(sanitized.contains("s3cr3tPassword"))
        assertFalse(sanitized.contains("myuser"))
        assertTrue(sanitized.contains("postgresql"))
    }

    @Test
    fun `should redact password query parameter`() {
        val url = "jdbc:mysql://localhost:3306/mydb?user=root&password=hunter2"

        val sanitized = sanitizeJdbcUrl(url)

        assertFalse(sanitized.contains("hunter2"))
        assertTrue(sanitized.contains("password=***"))
    }

    @Test
    fun `should redact pwd attribute parameter regardless of case`() {
        val url = "jdbc:sqlserver://localhost;databaseName=mydb;PWD=TopSecret123"

        val sanitized = sanitizeJdbcUrl(url)

        assertFalse(sanitized.contains("TopSecret123"))
    }

    @Test
    fun `should leave URLs with no credentials unchanged in substance`() {
        val url = "jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1"

        val sanitized = sanitizeJdbcUrl(url)

        assertTrue(sanitized.contains("jdbc:h2:mem:test_db"))
    }

    @Test
    fun `should not throw on malformed input`() {
        val sanitized = sanitizeJdbcUrl("not a real jdbc url at all")

        // Should return something rather than throwing - never crash the caller.
        assertTrue(sanitized.isNotEmpty())
    }
}
