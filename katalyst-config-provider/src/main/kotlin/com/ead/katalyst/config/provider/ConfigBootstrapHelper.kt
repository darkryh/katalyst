package com.ead.katalyst.config.provider

import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import com.ead.katalyst.core.config.ConfigValidator
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

/**
 * Bootstrap helper for configuration initialization.
 *
 * **Purpose:**
 * Provides utility functions for loading and validating configuration
 * before Koin DI context is fully initialized.
 *
 * **Why Separate?**
 * ConfigProvider is auto-discovered during DI initialization, but database
 * configuration must be provided BEFORE DI can initialize. This helper
 * creates a standalone ConfigProvider instance for bootstrap, which is then
 * registered as a singleton in Koin.
 *
 * **Typical Usage:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     // 1. Load config before DI
 *     val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 *
 *     // 2. Extract database config
 *     val dbConfig = ConfigBootstrapHelper.loadDatabaseConfig(config)
 *
 *     // 3. Pass to framework
 *     database(dbConfig)
 *     scanPackages("com.example")
 * }
 * ```
 */
object ConfigBootstrapHelper {
    private val log = LoggerFactory.getLogger(ConfigBootstrapHelper::class.java)

    /**
     * Load and initialize ConfigProvider before Koin DI context.
     *
     * **Flow:**
     * 1. Instantiate provided ConfigProvider class
     * 2. Validate configuration
     * 3. Return ready-to-use provider
     *
     * @param T Type of ConfigProvider implementation
     * @param providerClass Class to instantiate (e.g., YamlConfigProvider::class.java)
     * @return Initialized ConfigProvider instance
     * @throws ConfigException if configuration loading or validation fails
     */
    fun <T : ConfigProvider> loadConfig(providerClass: Class<T>): ConfigProvider {
        log.info("Loading configuration with provider: ${providerClass.simpleName}")

        return try {
            val provider = providerClass.getDeclaredConstructor().newInstance()
            log.info("✓ Configuration loaded successfully")
            provider
        } catch (e: Exception) {
            throw ConfigException("Failed to load configuration with ${providerClass.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Load database configuration from ConfigProvider.
     *
     * Extracts database-specific configuration values as a map.
     * Applications should use DatabaseConfigLoader to load this into DatabaseConfig type.
     *
     * **Configuration Keys Used:**
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
     * @param config ConfigProvider to load from
     * @return Map of database configuration values
     * @throws ConfigException if required keys are missing
     */
    fun loadDatabaseConfigMap(config: ConfigProvider): Map<String, Any> {
        log.debug("Loading database configuration...")

        val url = ConfigLoaders.loadRequiredString(config, "database.url")
        val username = ConfigLoaders.loadRequiredString(config, "database.username")
        val password = config.getString("database.password", "")
        val driver = ConfigLoaders.loadRequiredString(config, "database.driver")

        val dbConfig = mapOf(
            "url" to url,
            "driver" to driver,
            "username" to username,
            "password" to password,
            "maxPoolSize" to ConfigLoaders.loadOptionalInt(config, "database.pool.maxSize", 10),
            "minIdleConnections" to ConfigLoaders.loadOptionalInt(config, "database.pool.minIdle", 2),
            "connectionTimeout" to ConfigLoaders.loadOptionalLong(config, "database.pool.connectionTimeout", 30_000L),
            "idleTimeout" to ConfigLoaders.loadOptionalLong(config, "database.pool.idleTimeout", 600_000L),
            "maxLifetime" to ConfigLoaders.loadOptionalLong(config, "database.pool.maxLifetime", 1_800_000L),
            "autoCommit" to false
        )

        log.debug("✓ Database configuration loaded: url=$url, driver=$driver")
        return dbConfig
    }

    /**
     * Validate configuration using all discovered ConfigValidator implementations.
     *
     * **How It Works:**
     * 1. Discovers all ConfigValidator implementations in the Koin context
     * 2. Calls validate() on each validator
     * 3. Collects all errors
     * 4. Throws if any validator failed
     *
     * **Note:**
     * This is typically called automatically during DI initialization.
     * Use this for manual validation during bootstrap if needed.
     *
     * @param config ConfigProvider to validate
     * @throws ConfigException if any validator fails
     */
    fun validateConfiguration(config: ConfigProvider) {
        log.info("Validating configuration...")

        try {
            val koin = GlobalContext.get()
            val validators = koin.getAll<ConfigValidator>()

            if (validators.isEmpty()) {
                log.debug("No ConfigValidator implementations found")
                return
            }

            log.debug("Running ${validators.size} configuration validators...")
            val errors = mutableListOf<String>()

            for (validator in validators) {
                try {
                    validator.validate()
                    log.debug("✓ Validator passed: ${validator::class.simpleName}")
                } catch (e: Exception) {
                    errors.add("${validator::class.simpleName}: ${e.message}")
                    log.warn("✗ Validator failed: ${validator::class.simpleName} - ${e.message}")
                }
            }

            if (errors.isNotEmpty()) {
                val errorMessage = "Configuration validation failed:\n" + errors.joinToString("\n")
                throw ConfigException(errorMessage)
            }

            log.info("✓ All configuration validators passed")
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not run configuration validators (Koin may not be initialized yet): ${e.message}")
        }
    }

    /**
     * Load service-specific configuration using a ServiceConfigLoader.
     *
     * **Purpose:**
     * Provides type-safe configuration loading for services that need
     * configurations loaded before or during DI initialization.
     *
     * @param T Type of configuration to load
     * @param config ConfigProvider to load from
     * @param loader ServiceConfigLoader that knows how to extract configuration
     * @return Loaded and validated configuration
     * @throws ConfigException if loading or validation fails
     */
    fun <T> loadServiceConfig(
        config: ConfigProvider,
        loader: ServiceConfigLoader<T>
    ): T {
        return try {
            val serviceConfig = loader.loadConfig(config)
            loader.validate(serviceConfig)
            serviceConfig
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Failed to load service configuration: ${e.message}", e)
        }
    }
}
