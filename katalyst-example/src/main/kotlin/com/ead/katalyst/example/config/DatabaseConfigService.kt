package com.ead.katalyst.example.config

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * Database configuration service that uses ConfigProvider.
 *
 * Automatically discovered and injected by Katalyst's reflection-based DI.
 *
 * **Usage:**
 * ```kotlin
 * class MyRepository(private val dbConfig: DatabaseConfigService) {
 *     fun connect() {
 *         val config = dbConfig.config()
 *         // use DatabaseConfig
 *     }
 * }
 * ```
 *
 * **Configuration Keys:**
 * - `database.url`: JDBC connection URL
 * - `database.username`: Database user
 * - `database.password`: Database password
 * - `database.driver`: JDBC driver class
 * - `database.pool.*`: Connection pool settings
 */
class DatabaseConfigService(private val config: ConfigProvider) : Service {
    companion object {
        private val log = LoggerFactory.getLogger(DatabaseConfigService::class.java)
    }

    fun config(): DatabaseConfig {
        val url = config.getString("database.url")
        val username = config.getString("database.username")
        val password = config.getString("database.password")
        val driver = config.getString("database.driver")

        return DatabaseConfig(
            url = url,
            driver = driver,
            username = username,
            password = password,
            maxPoolSize = config.getInt("database.pool.maxSize", 10),
            minIdleConnections = config.getInt("database.pool.minIdle", 1),
            connectionTimeout = config.getLong("database.pool.connectionTimeout", 30_000L),
            idleTimeout = config.getLong("database.pool.idleTimeout", 600_000L),
            maxLifetime = config.getLong("database.pool.maxLifetime", 1_800_000L),
            autoCommit = false
        ).also {
            log.debug("Database config loaded: url=$url, driver=$driver, user=$username")
        }
    }
}
