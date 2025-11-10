package com.ead.katalyst.example.config

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * Bootstrap helper that initializes configuration before Koin DI context.
 *
 * **Problem:** The `ConfigProvider` is an auto-discovered component that's registered
 * during `initializeDI()`. However, `database()` must be called BEFORE `initializeDI()`.
 *
 * **Solution:** Initialize a standalone ConfigProvider instance to load database
 * config, then let the DI system discover and register the same provider.
 *
 * **Usage:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(ConfigBootstrap.loadDatabaseConfig())
 *     scanPackages("com.ead.katalyst.example")
 * }
 * ```
 */
object ConfigBootstrap {
    private val log = LoggerFactory.getLogger(ConfigBootstrap::class.java)

    /**
     * Load and initialize ConfigProvider before Koin DI context.
     * Creates a standalone instance to bootstrap the application.
     *
     * @return DatabaseConfig extracted from configuration
     */
    fun loadDatabaseConfig(): DatabaseConfig {
        log.info("Loading configuration for bootstrap...")
        val configProvider = YamlConfigProvider()

        val url = configProvider.getString("database.url")
        val username = configProvider.getString("database.username")
        val password = configProvider.getString("database.password")
        val driver = configProvider.getString("database.driver")

        return DatabaseConfig(
            url = url,
            driver = driver,
            username = username,
            password = password,
            maxPoolSize = configProvider.getInt("database.pool.maxSize", 10),
            minIdleConnections = configProvider.getInt("database.pool.minIdle", 1),
            connectionTimeout = configProvider.getLong("database.pool.connectionTimeout", 30_000L),
            idleTimeout = configProvider.getLong("database.pool.idleTimeout", 600_000L),
            maxLifetime = configProvider.getLong("database.pool.maxLifetime", 1_800_000L),
            autoCommit = false
        ).also {
            log.info("✓ Bootstrap configuration loaded successfully")
        }
    }

    /**
     * Get configuration provider instance for loading other configs.
     * Use this if you need to access configuration values during bootstrap.
     *
     * @return ConfigProvider instance
     */
    fun getConfigProvider(): ConfigProvider {
        return YamlConfigProvider()
    }
}
