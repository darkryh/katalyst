package com.ead.katalyst.migrations.util

import kotlin.test.*

/**
 * Comprehensive tests for Checksums utility.
 *
 * Tests cover:
 * - Basic hash generation
 * - Hash stability and consistency
 * - Empty and null handling
 * - Multiple statements
 * - Order sensitivity
 * - Thread safety
 * - Whitespace handling
 * - Special characters
 * - Large statements
 */
class ChecksumsTest {

    // ========== BASIC HASH GENERATION TESTS ==========

    @Test
    fun `hashStatements should generate SHA-256 hash for single statement`() {
        val statements = listOf("CREATE TABLE users (id INT PRIMARY KEY)")
        val hash = hashStatements(statements)

        assertEquals(64, hash.length) // SHA-256 produces 64 hex characters
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashStatements should generate different hashes for different statements`() {
        val hash1 = hashStatements(listOf("CREATE TABLE users (id INT)"))
        val hash2 = hashStatements(listOf("CREATE TABLE orders (id INT)"))

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashStatements should generate same hash for identical statements`() {
        val statements = listOf("CREATE TABLE users (id INT PRIMARY KEY)")
        val hash1 = hashStatements(statements)
        val hash2 = hashStatements(statements)

        assertEquals(hash1, hash2)
    }

    // ========== EMPTY AND NULL HANDLING TESTS ==========

    @Test
    fun `hashStatements should handle empty list`() {
        val hash = hashStatements(emptyList())

        assertEquals(64, hash.length)
        assertNotNull(hash)
    }

    @Test
    fun `hashStatements should handle list with empty strings`() {
        val hash = hashStatements(listOf("", ""))

        assertEquals(64, hash.length)
        assertNotNull(hash)
    }

    @Test
    fun `hashStatements should generate different hash for empty list vs empty strings`() {
        val hash1 = hashStatements(emptyList())
        val hash2 = hashStatements(listOf(""))

        assertNotEquals(hash1, hash2)
    }

    // ========== MULTIPLE STATEMENTS TESTS ==========

    @Test
    fun `hashStatements should handle multiple statements`() {
        val statements = listOf(
            "CREATE TABLE users (id INT PRIMARY KEY)",
            "CREATE TABLE orders (id INT PRIMARY KEY)",
            "CREATE INDEX idx_user ON users(id)"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    @Test
    fun `hashStatements should produce consistent hash for multiple statements`() {
        val statements = listOf(
            "CREATE TABLE users (id INT PRIMARY KEY)",
            "CREATE TABLE orders (id INT PRIMARY KEY)"
        )
        val hash1 = hashStatements(statements)
        val hash2 = hashStatements(statements)

        assertEquals(hash1, hash2)
    }

    // ========== ORDER SENSITIVITY TESTS ==========

    @Test
    fun `hashStatements should be sensitive to statement order`() {
        val statements1 = listOf(
            "CREATE TABLE users (id INT)",
            "CREATE TABLE orders (id INT)"
        )
        val statements2 = listOf(
            "CREATE TABLE orders (id INT)",
            "CREATE TABLE users (id INT)"
        )

        val hash1 = hashStatements(statements1)
        val hash2 = hashStatements(statements2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashStatements should treat single statement differently from multiple`() {
        val hash1 = hashStatements(listOf("CREATE TABLE users (id INT)"))
        val hash2 = hashStatements(listOf(
            "CREATE TABLE users",
            "(id INT)"
        ))

        assertNotEquals(hash1, hash2)
    }

    // ========== THREAD SAFETY TESTS ==========

    @Test
    fun `hashStatements should be thread-safe`() {
        val statements = listOf("CREATE TABLE users (id INT PRIMARY KEY)")
        val expectedHash = hashStatements(statements)

        val results = (1..100).map {
            Thread {
                val hash = hashStatements(statements)
                assertEquals(expectedHash, hash)
            }.apply { start() }
        }.onEach { it.join() }

        assertEquals(100, results.size)
    }

    @Test
    fun `hashStatements should produce consistent results under concurrent access`() {
        val statements1 = listOf("CREATE TABLE users (id INT)")
        val statements2 = listOf("CREATE TABLE orders (id INT)")

        val threads = (1..50).flatMap { i ->
            listOf(
                Thread {
                    val hash = hashStatements(if (i % 2 == 0) statements1 else statements2)
                    assertNotNull(hash)
                    assertEquals(64, hash.length)
                },
                Thread {
                    val hash = hashStatements(if (i % 2 == 0) statements2 else statements1)
                    assertNotNull(hash)
                    assertEquals(64, hash.length)
                }
            )
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    // ========== WHITESPACE HANDLING TESTS ==========

    @Test
    fun `hashStatements should be sensitive to whitespace`() {
        val hash1 = hashStatements(listOf("CREATE TABLE users"))
        val hash2 = hashStatements(listOf("CREATE  TABLE  users"))

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashStatements should be sensitive to leading whitespace`() {
        val hash1 = hashStatements(listOf("CREATE TABLE users"))
        val hash2 = hashStatements(listOf("  CREATE TABLE users"))

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashStatements should be sensitive to trailing whitespace`() {
        val hash1 = hashStatements(listOf("CREATE TABLE users"))
        val hash2 = hashStatements(listOf("CREATE TABLE users  "))

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashStatements should be sensitive to newlines`() {
        val hash1 = hashStatements(listOf("CREATE TABLE users"))
        val hash2 = hashStatements(listOf("CREATE TABLE\nusers"))

        assertNotEquals(hash1, hash2)
    }

    // ========== SPECIAL CHARACTERS TESTS ==========

    @Test
    fun `hashStatements should handle SQL with special characters`() {
        val statements = listOf(
            "CREATE TABLE users (name VARCHAR(100), email VARCHAR(255))",
            "INSERT INTO users VALUES ('O''Brien', 'test@example.com')"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    @Test
    fun `hashStatements should handle SQL with Unicode characters`() {
        val statements = listOf(
            "INSERT INTO users VALUES ('José', 'josé@example.com')",
            "INSERT INTO users VALUES ('北京', 'beijing@example.com')"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    @Test
    fun `hashStatements should handle SQL with escape sequences`() {
        val statements = listOf(
            "INSERT INTO logs VALUES ('Error\\nNewline')",
            "INSERT INTO logs VALUES ('Tab\\tSeparated')"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    // ========== LARGE STATEMENTS TESTS ==========

    @Test
    fun `hashStatements should handle very long statements`() {
        val longStatement = "CREATE TABLE users (" +
                (1..1000).joinToString(", ") { "col$it INT" } +
                ")"
        val hash = hashStatements(listOf(longStatement))

        assertEquals(64, hash.length)
    }

    @Test
    fun `hashStatements should handle many statements`() {
        val statements = (1..1000).map { "CREATE TABLE table$it (id INT)" }
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    // ========== HASH STABILITY TESTS ==========

    @Test
    fun `hashStatements should produce stable hashes across multiple calls`() {
        val statements = listOf(
            "CREATE TABLE users (id INT PRIMARY KEY)",
            "CREATE INDEX idx_user ON users(id)"
        )

        val hashes = (1..10).map { hashStatements(statements) }

        assertTrue(hashes.all { it == hashes.first() })
    }

    @Test
    fun `hashStatements should produce different hashes for minor changes`() {
        val base = "CREATE TABLE users (id INT PRIMARY KEY)"
        val modified = "CREATE TABLE users (id INT PRIMARY KEY )"  // Trailing space

        val hash1 = hashStatements(listOf(base))
        val hash2 = hashStatements(listOf(modified))

        assertNotEquals(hash1, hash2)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `migration with CREATE TABLE statements`() {
        val statements = listOf(
            "CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100), email VARCHAR(255))",
            "CREATE TABLE orders (id SERIAL PRIMARY KEY, user_id INT REFERENCES users(id))"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `migration with ALTER TABLE statements`() {
        val statements = listOf(
            "ALTER TABLE users ADD COLUMN created_at TIMESTAMP",
            "ALTER TABLE users ADD COLUMN updated_at TIMESTAMP"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    @Test
    fun `migration with CREATE INDEX statements`() {
        val statements = listOf(
            "CREATE INDEX idx_users_email ON users(email)",
            "CREATE INDEX idx_orders_user_id ON orders(user_id)"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    @Test
    fun `migration with INSERT statements`() {
        val statements = listOf(
            "INSERT INTO users (name, email) VALUES ('Admin', 'admin@example.com')",
            "INSERT INTO users (name, email) VALUES ('User', 'user@example.com')"
        )
        val hash = hashStatements(statements)

        assertEquals(64, hash.length)
    }

    @Test
    fun `complex migration with multiple statement types`() {
        val statements = listOf(
            "CREATE TABLE roles (id SERIAL PRIMARY KEY, name VARCHAR(50))",
            "INSERT INTO roles (name) VALUES ('ADMIN'), ('USER')",
            "CREATE TABLE user_roles (user_id INT, role_id INT)",
            "ALTER TABLE user_roles ADD FOREIGN KEY (user_id) REFERENCES users(id)",
            "CREATE INDEX idx_user_roles ON user_roles(user_id, role_id)"
        )
        val hash1 = hashStatements(statements)
        val hash2 = hashStatements(statements)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
    }

    @Test
    fun `identical migrations should have identical hashes`() {
        val migration1 = listOf(
            "CREATE TABLE products (id INT PRIMARY KEY)",
            "CREATE INDEX idx_products ON products(id)"
        )
        val migration2 = listOf(
            "CREATE TABLE products (id INT PRIMARY KEY)",
            "CREATE INDEX idx_products ON products(id)"
        )

        val hash1 = hashStatements(migration1)
        val hash2 = hashStatements(migration2)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `modified migrations should have different hashes`() {
        val original = listOf(
            "CREATE TABLE products (id INT PRIMARY KEY)",
            "CREATE INDEX idx_products ON products(id)"
        )
        val modified = listOf(
            "CREATE TABLE products (id INT PRIMARY KEY)",
            "CREATE INDEX idx_products_name ON products(name)"  // Different index
        )

        val hash1 = hashStatements(original)
        val hash2 = hashStatements(modified)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `case-sensitive migration hashing`() {
        val uppercase = listOf("CREATE TABLE USERS (ID INT)")
        val lowercase = listOf("create table users (id int)")

        val hash1 = hashStatements(uppercase)
        val hash2 = hashStatements(lowercase)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash consistency for rollback scenarios`() {
        val upStatements = listOf(
            "CREATE TABLE sessions (id VARCHAR(255) PRIMARY KEY)",
            "CREATE INDEX idx_sessions_created ON sessions(created_at)"
        )

        val hash1 = hashStatements(upStatements)
        val hash2 = hashStatements(upStatements)
        val hash3 = hashStatements(upStatements)

        assertEquals(hash1, hash2)
        assertEquals(hash2, hash3)
    }
}
