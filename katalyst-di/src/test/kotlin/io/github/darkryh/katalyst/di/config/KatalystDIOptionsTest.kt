package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.DatabaseConfig
import kotlin.test.*

/**
 * Comprehensive tests for KatalystDIOptions.
 *
 * Tests cover:
 * - Basic construction
 * - Array handling (scanPackages)
 * - Feature list handling
 * - Custom equals/hashCode (for array comparison)
 * - Data class behavior
 * - Practical usage scenarios
 */
class KatalystDIOptionsTest {

    // ========== TEST DATABASE CONFIG ==========

    private fun createTestDatabaseConfig(): DatabaseConfig = DatabaseConfig(
        url = "jdbc:h2:mem:test",
        driver = "org.h2.Driver",
        username = "sa",
        password = ""
    )

    // ========== BASIC CONSTRUCTION TESTS ==========

    @Test
    fun `KatalystDIOptions should require database config`() {
        val dbConfig = createTestDatabaseConfig()
        val options = KatalystDIOptions(databaseConfig = dbConfig)

        assertEquals(dbConfig, options.databaseConfig)
    }

    @Test
    fun `KatalystDIOptions should use empty array for scanPackages by default`() {
        val options = KatalystDIOptions(databaseConfig = createTestDatabaseConfig())

        assertTrue(options.scanPackages.isEmpty())
        assertEquals(0, options.scanPackages.size)
    }

    @Test
    fun `KatalystDIOptions should use empty list for features by default`() {
        val options = KatalystDIOptions(databaseConfig = createTestDatabaseConfig())

        assertTrue(options.features.isEmpty())
        assertEquals(0, options.features.size)
    }

    @Test
    fun `KatalystDIOptions should support custom scanPackages`() {
        val packages = arrayOf("com.example.app", "com.example.domain")
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = packages
        )

        assertEquals(2, options.scanPackages.size)
        assertTrue(options.scanPackages.contains("com.example.app"))
        assertTrue(options.scanPackages.contains("com.example.domain"))
    }

    @Test
    fun `KatalystDIOptions should support single scan package`() {
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = arrayOf("com.example.app")
        )

        assertEquals(1, options.scanPackages.size)
        assertEquals("com.example.app", options.scanPackages[0])
    }

    // ========== ARRAY HANDLING TESTS ==========

    @Test
    fun `KatalystDIOptions should handle empty scanPackages array`() {
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = emptyArray()
        )

        assertTrue(options.scanPackages.isEmpty())
    }

    @Test
    fun `KatalystDIOptions should preserve scanPackages order`() {
        val packages = arrayOf("com.example.app", "com.example.domain", "com.example.service")
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = packages
        )

        assertEquals("com.example.app", options.scanPackages[0])
        assertEquals("com.example.domain", options.scanPackages[1])
        assertEquals("com.example.service", options.scanPackages[2])
    }

    @Test
    fun `KatalystDIOptions should handle many scan packages`() {
        val packages = (1..100).map { "com.example.package$it" }.toTypedArray()
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = packages
        )

        assertEquals(100, options.scanPackages.size)
        assertEquals("com.example.package1", options.scanPackages[0])
        assertEquals("com.example.package100", options.scanPackages[99])
    }

    // ========== EQUALITY TESTS ==========

    @Test
    fun `KatalystDIOptions should be equal with same database config`() {
        val dbConfig = createTestDatabaseConfig()
        val options1 = KatalystDIOptions(databaseConfig = dbConfig)
        val options2 = KatalystDIOptions(databaseConfig = dbConfig)

        assertEquals(options1, options2)
    }

    @Test
    fun `KatalystDIOptions should be equal with same scanPackages`() {
        val dbConfig = createTestDatabaseConfig()
        val packages = arrayOf("com.example.app")

        val options1 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = packages
        )
        val options2 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = arrayOf("com.example.app")
        )

        assertEquals(options1, options2)
    }

    @Test
    fun `KatalystDIOptions should not be equal with different scanPackages`() {
        val dbConfig = createTestDatabaseConfig()

        val options1 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = arrayOf("com.example.app")
        )
        val options2 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = arrayOf("com.example.domain")
        )

        assertNotEquals(options1, options2)
    }

    @Test
    fun `KatalystDIOptions should not be equal with different database config`() {
        val dbConfig1 = createTestDatabaseConfig()
        val dbConfig2 = DatabaseConfig(
            url = "jdbc:postgresql://localhost/test",
            driver = "org.postgresql.Driver",
            username = "user",
            password = "pass"
        )

        val options1 = KatalystDIOptions(databaseConfig = dbConfig1)
        val options2 = KatalystDIOptions(databaseConfig = dbConfig2)

        assertNotEquals(options1, options2)
    }

    @Test
    fun `KatalystDIOptions should handle reflexive equality`() {
        val options = KatalystDIOptions(databaseConfig = createTestDatabaseConfig())

        assertEquals(options, options)
    }

    @Test
    fun `KatalystDIOptions should handle empty arrays in equality`() {
        val dbConfig = createTestDatabaseConfig()
        val options1 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = emptyArray()
        )
        val options2 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = emptyArray()
        )

        assertEquals(options1, options2)
    }

    // ========== HASHCODE TESTS ==========

    @Test
    fun `KatalystDIOptions should have consistent hashCode`() {
        val dbConfig = createTestDatabaseConfig()
        val packages = arrayOf("com.example.app")

        val options1 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = packages
        )
        val options2 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = arrayOf("com.example.app")
        )

        assertEquals(options1.hashCode(), options2.hashCode())
    }

    @Test
    fun `KatalystDIOptions should have different hashCode for different configs`() {
        val dbConfig1 = createTestDatabaseConfig()
        val dbConfig2 = DatabaseConfig(
            url = "jdbc:postgresql://localhost/test",
            driver = "org.postgresql.Driver",
            username = "user",
            password = "pass"
        )

        val options1 = KatalystDIOptions(databaseConfig = dbConfig1)
        val options2 = KatalystDIOptions(databaseConfig = dbConfig2)

        assertNotEquals(options1.hashCode(), options2.hashCode())
    }

    @Test
    fun `KatalystDIOptions should include scanPackages in hashCode`() {
        val dbConfig = createTestDatabaseConfig()

        val options1 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = arrayOf("com.example.app")
        )
        val options2 = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = arrayOf("com.example.domain")
        )

        // Different packages should likely produce different hash codes
        // (not guaranteed, but very likely)
        assertNotEquals(options1.hashCode(), options2.hashCode())
    }

    // ========== DATA CLASS BEHAVIOR TESTS ==========

    @Test
    fun `KatalystDIOptions should support copy`() {
        val original = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = arrayOf("com.example.app")
        )

        val newDbConfig = DatabaseConfig(
            url = "jdbc:postgresql://localhost/new",
            driver = "org.postgresql.Driver",
            username = "new",
            password = "new"
        )

        val copied = original.copy(databaseConfig = newDbConfig)

        assertEquals(newDbConfig, copied.databaseConfig)
        assertTrue(copied.scanPackages.contentEquals(arrayOf("com.example.app")))
    }

    @Test
    fun `KatalystDIOptions should support copy with scanPackages change`() {
        val original = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = arrayOf("com.example.app")
        )

        val newPackages = arrayOf("com.example.domain", "com.example.service")
        val copied = original.copy(scanPackages = newPackages)

        assertTrue(copied.scanPackages.contentEquals(newPackages))
        assertEquals(2, copied.scanPackages.size)
    }

    @Test
    fun `KatalystDIOptions should preserve original on copy`() {
        val dbConfig = createTestDatabaseConfig()
        val original = KatalystDIOptions(
            databaseConfig = dbConfig,
            scanPackages = arrayOf("com.example.app")
        )

        val copied = original.copy(scanPackages = arrayOf("com.example.domain"))

        assertEquals("com.example.app", original.scanPackages[0])
        assertEquals("com.example.domain", copied.scanPackages[0])
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `typical application configuration`() {
        val options = KatalystDIOptions(
            databaseConfig = DatabaseConfig(
                url = "jdbc:postgresql://localhost:5432/myapp",
                driver = "org.postgresql.Driver",
                username = "app_user",
                password = "secure_password"
            ),
            scanPackages = arrayOf(
                "com.example.app.services",
                "com.example.app.repositories",
                "com.example.app.controllers"
            )
        )

        assertEquals(3, options.scanPackages.size)
        assertNotNull(options.databaseConfig)
        assertTrue(options.features.isEmpty())
    }

    @Test
    fun `minimal configuration for testing`() {
        val options = KatalystDIOptions(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:mem:test",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )

        assertTrue(options.scanPackages.isEmpty())
        assertTrue(options.features.isEmpty())
    }

    @Test
    fun `configuration with single module package`() {
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = arrayOf("com.example.users")
        )

        assertEquals(1, options.scanPackages.size)
        assertEquals("com.example.users", options.scanPackages[0])
    }

    @Test
    fun `configuration for monolith application`() {
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = arrayOf(
                "com.example.users",
                "com.example.products",
                "com.example.orders",
                "com.example.payments",
                "com.example.shipping"
            )
        )

        assertEquals(5, options.scanPackages.size)
    }

    @Test
    fun `configuration for microservice`() {
        val options = KatalystDIOptions(
            databaseConfig = DatabaseConfig(
                url = "jdbc:postgresql://db:5432/user_service",
                driver = "org.postgresql.Driver",
                username = "user_svc",
                password = "password"
            ),
            scanPackages = arrayOf("com.example.userservice")
        )

        assertEquals(1, options.scanPackages.size)
        assertTrue(options.scanPackages[0].contains("userservice"))
    }

    @Test
    fun `production database configuration`() {
        val options = KatalystDIOptions(
            databaseConfig = DatabaseConfig(
                url = "jdbc:postgresql://prod-db.example.com:5432/production",
                driver = "org.postgresql.Driver",
                username = "prod_user",
                password = "prod_secure_password"
            ),
            scanPackages = arrayOf("com.example.app")
        )

        assertTrue(options.databaseConfig.url.contains("prod-db"))
        assertTrue(options.databaseConfig.username.contains("prod"))
    }

    @Test
    fun `development H2 configuration`() {
        val options = KatalystDIOptions(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:~/dev_database",
                driver = "org.h2.Driver",
                username = "dev",
                password = "dev"
            ),
            scanPackages = arrayOf("com.example.dev")
        )

        assertTrue(options.databaseConfig.url.contains("h2"))
        assertEquals("dev", options.databaseConfig.username)
    }

    @Test
    fun `multi-module application configuration`() {
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = arrayOf(
                "com.example.core",
                "com.example.api",
                "com.example.domain.users",
                "com.example.domain.products",
                "com.example.infrastructure"
            )
        )

        assertEquals(5, options.scanPackages.size)
        assertTrue(options.scanPackages.any { it.contains("core") })
        assertTrue(options.scanPackages.any { it.contains("api") })
        assertTrue(options.scanPackages.any { it.contains("infrastructure") })
    }

    @Test
    fun `nested package configuration`() {
        val options = KatalystDIOptions(
            databaseConfig = createTestDatabaseConfig(),
            scanPackages = arrayOf(
                "com.example.app.services.user",
                "com.example.app.services.order",
                "com.example.app.repositories.user",
                "com.example.app.repositories.order"
            )
        )

        assertEquals(4, options.scanPackages.size)
        assertTrue(options.scanPackages.all { it.startsWith("com.example.app") })
    }
}
