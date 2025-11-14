package com.ead.katalyst.config

import kotlin.test.*

/**
 * Comprehensive tests for DatabaseConfig.
 *
 * Tests cover:
 * - Valid configuration creation
 * - Required field validation (url, driver)
 * - Pool size validation
 * - Timeout validation
 * - Default values
 * - Edge cases (empty strings, boundary values)
 */
class DatabaseConfigTest {

    // ========== VALID CONFIGURATION TESTS ==========

    @Test
    fun `should create valid config with all required fields`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals("jdbc:h2:mem:test", config.url)
        assertEquals("org.h2.Driver", config.driver)
        assertEquals("sa", config.username)
        assertEquals("", config.password)
    }

    @Test
    fun `should create valid config with all fields specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:postgresql://localhost:5432/katalyst",
            driver = "org.postgresql.Driver",
            username = "postgres",
            password = "secret123",
            maxPoolSize = 20,
            minIdleConnections = 5,
            connectionTimeout = 60000L,
            idleTimeout = 300000L,
            maxLifetime = 900000L,
            autoCommit = true,
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
        )

        // Then
        assertEquals("jdbc:postgresql://localhost:5432/katalyst", config.url)
        assertEquals("org.postgresql.Driver", config.driver)
        assertEquals("postgres", config.username)
        assertEquals("secret123", config.password)
        assertEquals(20, config.maxPoolSize)
        assertEquals(5, config.minIdleConnections)
        assertEquals(60000L, config.connectionTimeout)
        assertEquals(300000L, config.idleTimeout)
        assertEquals(900000L, config.maxLifetime)
        assertTrue(config.autoCommit)
        assertEquals("TRANSACTION_SERIALIZABLE", config.transactionIsolation)
    }

    // ========== DEFAULT VALUES TESTS ==========

    @Test
    fun `should use default maxPoolSize when not specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals(10, config.maxPoolSize)
    }

    @Test
    fun `should use default minIdleConnections when not specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals(2, config.minIdleConnections)
    }

    @Test
    fun `should use default connectionTimeout when not specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals(30000L, config.connectionTimeout)
    }

    @Test
    fun `should use default idleTimeout when not specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals(600000L, config.idleTimeout)
    }

    @Test
    fun `should use default maxLifetime when not specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals(1800000L, config.maxLifetime)
    }

    @Test
    fun `should use default autoCommit when not specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertFalse(config.autoCommit)
    }

    @Test
    fun `should use default transactionIsolation when not specified`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals("TRANSACTION_REPEATABLE_READ", config.transactionIsolation)
    }

    // ========== URL VALIDATION TESTS ==========

    @Test
    fun `should throw IllegalArgumentException when url is blank`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        }

        assertEquals("Database URL cannot be blank", exception.message)
    }

    @Test
    fun `should throw IllegalArgumentException when url is whitespace`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "   ",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        }

        assertEquals("Database URL cannot be blank", exception.message)
    }

    @Test
    fun `should accept various valid JDBC URL formats`() {
        // PostgreSQL
        var config = DatabaseConfig(
            url = "jdbc:postgresql://localhost:5432/mydb",
            driver = "org.postgresql.Driver",
            username = "user",
            password = "pass"
        )
        assertEquals("jdbc:postgresql://localhost:5432/mydb", config.url)

        // MySQL
        config = DatabaseConfig(
            url = "jdbc:mysql://localhost:3306/mydb?useSSL=false",
            driver = "com.mysql.cj.jdbc.Driver",
            username = "user",
            password = "pass"
        )
        assertEquals("jdbc:mysql://localhost:3306/mydb?useSSL=false", config.url)

        // H2 mem
        config = DatabaseConfig(
            url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )
        assertEquals("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", config.url)

        // H2 file
        config = DatabaseConfig(
            url = "jdbc:h2:file:/data/test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )
        assertEquals("jdbc:h2:file:/data/test", config.url)
    }

    // ========== DRIVER VALIDATION TESTS ==========

    @Test
    fun `should throw IllegalArgumentException when driver is blank`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "",
                username = "sa",
                password = ""
            )
        }

        assertEquals("Database driver cannot be blank", exception.message)
    }

    @Test
    fun `should throw IllegalArgumentException when driver is whitespace`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "   ",
                username = "sa",
                password = ""
            )
        }

        assertEquals("Database driver cannot be blank", exception.message)
    }

    @Test
    fun `should accept various valid driver class names`() {
        // H2
        var config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )
        assertEquals("org.h2.Driver", config.driver)

        // PostgreSQL
        config = DatabaseConfig(
            url = "jdbc:postgresql://localhost/db",
            driver = "org.postgresql.Driver",
            username = "user",
            password = "pass"
        )
        assertEquals("org.postgresql.Driver", config.driver)

        // MySQL
        config = DatabaseConfig(
            url = "jdbc:mysql://localhost/db",
            driver = "com.mysql.cj.jdbc.Driver",
            username = "user",
            password = "pass"
        )
        assertEquals("com.mysql.cj.jdbc.Driver", config.driver)
    }

    // ========== POOL SIZE VALIDATION TESTS ==========

    @Test
    fun `should throw IllegalArgumentException when maxPoolSize is zero`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                maxPoolSize = 0
            )
        }

        assertEquals("Max pool size must be greater than 0", exception.message)
    }

    @Test
    fun `should throw IllegalArgumentException when maxPoolSize is negative`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                maxPoolSize = -5
            )
        }

        assertEquals("Max pool size must be greater than 0", exception.message)
    }

    @Test
    fun `should accept maxPoolSize of 1`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            maxPoolSize = 1
        )

        // Then
        assertEquals(1, config.maxPoolSize)
    }

    @Test
    fun `should accept large maxPoolSize values`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            maxPoolSize = 100
        )

        // Then
        assertEquals(100, config.maxPoolSize)
    }

    // ========== MIN IDLE CONNECTIONS VALIDATION TESTS ==========

    @Test
    fun `should throw IllegalArgumentException when minIdleConnections is negative`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                minIdleConnections = -1
            )
        }

        assertEquals("Min idle connections cannot be negative", exception.message)
    }

    @Test
    fun `should accept minIdleConnections of 0`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            minIdleConnections = 0
        )

        // Then
        assertEquals(0, config.minIdleConnections)
    }

    @Test
    fun `should accept minIdleConnections equal to maxPoolSize`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            maxPoolSize = 10,
            minIdleConnections = 10
        )

        // Then
        assertEquals(10, config.minIdleConnections)
        assertEquals(10, config.maxPoolSize)
    }

    @Test
    fun `should accept minIdleConnections greater than maxPoolSize`() {
        // When - HikariCP will handle this internally
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            maxPoolSize = 5,
            minIdleConnections = 10
        )

        // Then
        assertEquals(5, config.maxPoolSize)
        assertEquals(10, config.minIdleConnections)
    }

    // ========== CONNECTION TIMEOUT VALIDATION TESTS ==========

    @Test
    fun `should throw IllegalArgumentException when connectionTimeout is zero`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                connectionTimeout = 0L
            )
        }

        assertEquals("Connection timeout must be greater than 0", exception.message)
    }

    @Test
    fun `should throw IllegalArgumentException when connectionTimeout is negative`() {
        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                connectionTimeout = -1000L
            )
        }

        assertEquals("Connection timeout must be greater than 0", exception.message)
    }

    @Test
    fun `should accept connectionTimeout of 1 millisecond`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            connectionTimeout = 1L
        )

        // Then
        assertEquals(1L, config.connectionTimeout)
    }

    @Test
    fun `should accept very large connectionTimeout values`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "",
            connectionTimeout = 600000L  // 10 minutes
        )

        // Then
        assertEquals(600000L, config.connectionTimeout)
    }

    // ========== CREDENTIALS TESTS ==========

    @Test
    fun `should accept empty username`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "",
            password = "pass"
        )

        // Then
        assertEquals("", config.username)
    }

    @Test
    fun `should accept empty password`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // Then
        assertEquals("", config.password)
    }

    @Test
    fun `should accept special characters in credentials`() {
        // When
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "user@domain.com",
            password = "P@ssw0rd!#$%"
        )

        // Then
        assertEquals("user@domain.com", config.username)
        assertEquals("P@ssw0rd!#$%", config.password)
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `should support data class copy with modifications`() {
        // Given
        val original = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = ""
        )

        // When
        val copy = original.copy(maxPoolSize = 20, minIdleConnections = 5)

        // Then
        assertEquals("jdbc:h2:mem:test", copy.url)
        assertEquals("org.h2.Driver", copy.driver)
        assertEquals(20, copy.maxPoolSize)
        assertEquals(5, copy.minIdleConnections)
        assertEquals(10, original.maxPoolSize)  // Original unchanged
    }

    @Test
    fun `should support equality comparison`() {
        // Given
        val config1 = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "pass"
        )

        val config2 = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "pass"
        )

        val config3 = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "different"
        )

        // Then
        assertEquals(config1, config2)
        assertNotEquals(config1, config3)
    }

    @Test
    fun `should generate consistent hashCode for equal objects`() {
        // Given
        val config1 = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "pass"
        )

        val config2 = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "pass"
        )

        // Then
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `should generate readable toString representation`() {
        // Given
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test",
            driver = "org.h2.Driver",
            username = "sa",
            password = "secret"
        )

        // When
        val string = config.toString()

        // Then
        assertTrue(string.contains("jdbc:h2:mem:test"))
        assertTrue(string.contains("org.h2.Driver"))
        assertTrue(string.contains("sa"))
        // Password may be included in toString (not a security issue in tests)
    }
}
