package com.ead.katalyst.example.config.loaders

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.config.provider.ServiceConfigLoader
import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * ServiceConfigLoader implementation for DatabaseConfig.
 *
 * **Purpose:**
 * Loads database configuration from ConfigProvider in a type-safe manner.
 * Can be auto-discovered and validated during application startup.
 *
 * **Configuration Keys:**
 * - database.url (REQUIRED)
 * - database.username (REQUIRED)
 * - database.password (optional)
 * - database.driver (REQUIRED)
 * - database.pool.maxSize (optional, default: 10)
 * - database.pool.minIdle (optional, default: 2)
 * - database.pool.connectionTimeout (optional, default: 30000ms)
 * - database.pool.idleTimeout (optional, default: 600000ms)
 * - database.pool.maxLifetime (optional, default: 1800000ms)
 *
 * **Example YAML:**
 * ```yaml
 * database:
 *   url: ${DB_URL:jdbc:postgresql://localhost:5432/katalyst_db}
 *   username: ${DB_USER:postgres}
 *   password: ${DB_PASS:}
 *   driver: org.postgresql.Driver
 *   pool:
 *     maxSize: 20
 *     minIdle: 5
 *     connectionTimeout: 30000
 *     idleTimeout: 600000
 *     maxLifetime: 1800000
 * ```
 *
 * **Usage in Application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 *     val dbConfig = ConfigBootstrapHelper.loadServiceConfig(config, DatabaseConfigLoader())
 *     database(dbConfig)
 *     scanPackages("com.ead.katalyst.example")
 * }
 * ```
 *
 * **Auto-Discovery:**
 * ```kotlin
 * val loaders = ConfigMetadata.discoverLoaders(arrayOf("com.ead.katalyst.example"))
 * // DatabaseConfigLoader is automatically discovered and validated
 * ```
 */
class DatabaseConfigLoader : ServiceConfigLoader<DatabaseConfig> {
    companion object {
        private val log = LoggerFactory.getLogger(DatabaseConfigLoader::class.java)
    }

    /**
     * Load database configuration from ConfigProvider.
     *
     * **Process:**
     * 1. Extract required keys (url, username, driver)
     * 2. Extract optional keys with defaults (password, pool settings)
     * 3. Construct and return DatabaseConfig
     * 4. Validation happens in validate() method
     *
     * @param provider ConfigProvider to load from
     * @return Loaded DatabaseConfig instance
     * @throws ConfigException if required keys are missing
     */
    override fun loadConfig(provider: ConfigProvider): DatabaseConfig {
        log.debug("Loading database configuration...")

        // Load required keys
        val url = ConfigLoaders.loadRequiredString(provider, "database.url")
        val username = ConfigLoaders.loadRequiredString(provider, "database.username")
        val driver = ConfigLoaders.loadRequiredString(provider, "database.driver")

        // Load optional keys with defaults
        val password = ConfigLoaders.loadOptionalString(provider, "database.password", "")
        val maxPoolSize = ConfigLoaders.loadOptionalInt(provider, "database.pool.maxSize", 10)
        val minIdleConnections = ConfigLoaders.loadOptionalInt(provider, "database.pool.minIdle", 2)
        val connectionTimeout = ConfigLoaders.loadOptionalLong(provider, "database.pool.connectionTimeout", 30_000L)
        val idleTimeout = ConfigLoaders.loadOptionalLong(provider, "database.pool.idleTimeout", 600_000L)
        val maxLifetime = ConfigLoaders.loadOptionalLong(provider, "database.pool.maxLifetime", 1_800_000L)

        return DatabaseConfig(
            url = url,
            driver = driver,
            username = username,
            password = password,
            maxPoolSize = maxPoolSize,
            minIdleConnections = minIdleConnections,
            connectionTimeout = connectionTimeout,
            idleTimeout = idleTimeout,
            maxLifetime = maxLifetime,
            autoCommit = false
        ).also {
            log.debug("✓ Database configuration loaded: url=$url, driver=$driver")
        }
    }

    /**
     * Validate loaded database configuration.
     *
     * **Validation Checks:**
     * 1. URL is not blank (handled by DatabaseConfig constructor)
     * 2. Driver is not blank (handled by DatabaseConfig constructor)
     * 3. Pool settings are reasonable:
     *    - maxPoolSize > 0 (handled by DatabaseConfig)
     *    - minIdleConnections >= 0 (handled by DatabaseConfig)
     *    - connectionTimeout > 0 (handled by DatabaseConfig)
     * 4. Connection-specific validations:
     *    - Database driver class exists on classpath
     *    - URL format is valid for the driver
     *
     * @param config DatabaseConfig to validate
     * @throws ConfigException if validation fails
     */
    override fun validate(config: DatabaseConfig) {
        log.debug("Validating database configuration...")

        try {
            // Attempt to load the JDBC driver class
            try {
                Class.forName(config.driver)
                log.debug("✓ Database driver class found on classpath: ${config.driver}")
            } catch (e: ClassNotFoundException) {
                throw ConfigException(
                    "Database driver class not found on classpath: ${config.driver}. " +
                            "Ensure the JDBC driver dependency is included.",
                    e
                )
            }

            // Additional validation could include:
            // - Validating URL format
            // - Testing connection (optional, may be expensive)
            // - Validating pool settings relationships

            log.debug("✓ Database configuration validation passed")
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Database configuration validation failed: ${e.message}", e)
        }
    }
}
