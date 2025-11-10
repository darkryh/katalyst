package com.ead.katalyst.example.config

import com.ead.katalyst.config.provider.ConfigBootstrapHelper
import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.config.provider.ConfigMetadata
import com.ead.katalyst.config.yaml.YamlConfigProvider
import com.ead.katalyst.config.yaml.YamlConfigProviderLoader
import com.ead.katalyst.core.config.ConfigProvider
import com.ead.katalyst.example.config.loaders.DatabaseConfigLoader
import com.ead.katalyst.example.config.loaders.JwtConfigLoader
import org.slf4j.LoggerFactory

/**
 * Configuration usage examples for the Katalyst Configuration Framework.
 *
 * **Overview:**
 * This object demonstrates how to use the configuration framework for:
 * 1. Bootstrap configuration loading before DI
 * 2. Service-specific configuration loading
 * 3. Automatic discovery and validation
 * 4. Custom ServiceConfigLoader implementations
 *
 * **Key Components:**
 * - ConfigProvider: Format-agnostic configuration interface
 * - ServiceConfigLoader<T>: Type-safe configuration loader for specific services
 * - ConfigLoaders: Utility functions for common loading patterns
 * - YamlConfigProvider: YAML implementation with profile and environment variable support
 * - YamlProfileLoader: Handles profile-based YAML loading
 * - EnvironmentVariableSubstitutor: Substitutes ${VAR:default} in configuration
 *
 * **Typical Flow:**
 * ```
 * 1. Load ConfigProvider (YamlConfigProvider)
 * 2. Extract service-specific configs using ServiceConfigLoader implementations
 * 3. Pass configs to services/framework
 * 4. Optionally discover and validate all loaders
 * ```
 */
object ConfigurationExample {
    private val log = LoggerFactory.getLogger(ConfigurationExample::class.java)

    /**
     * Example 1: Basic Bootstrap Configuration Loading
     *
     * **Scenario:** You need to load database configuration before DI initialization.
     *
     * **Process:**
     * 1. Load ConfigProvider (YamlConfigProvider)
     * 2. Extract database configuration as map
     * 3. Convert map to DatabaseConfig
     * 4. Pass to framework.database()
     *
     * **When to Use:**
     * During application startup when you need configuration before Koin DI context
     * is initialized.
     *
     * **Example YAML (application.yaml):**
     * ```yaml
     * database:
     *   url: jdbc:postgresql://localhost:5432/katalyst_db
     *   username: postgres
     *   password: secret
     *   driver: org.postgresql.Driver
     * ```
     */
    fun exampleBasicBootstrapLoading() {
        log.info("Example 1: Basic Bootstrap Configuration Loading")

        // Step 1: Load ConfigProvider
        val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

        // Step 2: Extract raw database config as map
        val dbConfigMap = ConfigBootstrapHelper.loadDatabaseConfigMap(config)
        log.info("Database config map loaded: ${dbConfigMap.keys}")

        // Step 3: In actual code, convert to DatabaseConfig
        // val dbConfig = DatabaseConfig(
        //     url = dbConfigMap["url"] as String,
        //     driver = dbConfigMap["driver"] as String,
        //     // ... other fields
        // )

        // Step 4: Pass to framework
        // database(dbConfig)
    }

    /**
     * Example 2: Service-Specific Configuration Using ServiceConfigLoader
     *
     * **Scenario:** You want type-safe configuration loading for specific services.
     *
     * **Benefits:**
     * - Type-safe: Compile-time checking
     * - Testable: Easy to mock for unit tests
     * - Reusable: Same loader works across projects
     * - Self-validating: Validation logic in loader
     *
     * **Example YAML (application.yaml):**
     * ```yaml
     * jwt:
     *   secret: your-secret-key-min-32-characters
     *   issuer: katalyst-app
     *   audience: katalyst-api
     *   expirationTime: 86400000
     *   algorithm: HS256
     * ```
     */
    fun exampleServiceConfigLoading() {
        log.info("Example 2: Service-Specific Configuration Using ServiceConfigLoader")

        // Step 1: Load ConfigProvider
        val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

        // Step 2: Load JWT configuration using dedicated loader
        val jwtConfig = ConfigBootstrapHelper.loadServiceConfig(config, JwtConfigLoader())
        log.info("JWT config loaded: issuer=${jwtConfig.issuer}, algorithm=${jwtConfig.algorithm}")

        // Step 3: Load database configuration using dedicated loader
        val dbConfig = ConfigBootstrapHelper.loadServiceConfig(config, DatabaseConfigLoader())
        log.info("Database config loaded: driver=${dbConfig.driver}")

        // Now use jwtConfig and dbConfig in your services/framework setup
        // jwtSettings.secret = jwtConfig.secret
        // database(dbConfig)
    }

    /**
     * Example 3: Using ConfigLoaders Utility Functions
     *
     * **Scenario:** You're implementing a custom ServiceConfigLoader and need to load
     * different types of values safely.
     *
     * **Available Functions:**
     * - loadRequiredString(provider, key)
     * - loadOptionalString(provider, key, default)
     * - loadRequiredInt/Long(provider, key)
     * - loadOptionalInt/Long(provider, key, default)
     * - loadDuration(provider, key, default)
     * - loadList(provider, key, default)
     * - loadEnum(provider, key, default)
     * - loadBoolean(provider, key, default)
     * - validateRequiredKeys(provider, keys...)
     */
    fun exampleConfigLoadersUtility() {
        log.info("Example 3: Using ConfigLoaders Utility Functions")

        val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

        // Load different types of values
        val appName = ConfigLoaders.loadRequiredString(config, "application.name")
        val maxConnections = ConfigLoaders.loadOptionalInt(config, "database.pool.maxSize", 10)
        val enableLogging = ConfigLoaders.loadBoolean(config, "application.logging.enabled", true)

        log.info("Application name: $appName")
        log.info("Max connections: $maxConnections")
        log.info("Logging enabled: $enableLogging")
    }

    /**
     * Example 4: Profile-Based Configuration
     *
     * **Scenario:** You want different configurations for dev, staging, and production.
     *
     * **How It Works:**
     * 1. Base config loaded from application.yaml
     * 2. Profile config loaded from application-{profile}.yaml
     * 3. Profile config values override base config values
     * 4. Environment variables can be substituted
     *
     * **Set Active Profile:**
     * ```bash
     * export KATALYST_PROFILE=prod
     * java -jar application.jar
     * ```
     *
     * **Example Files:**
     * - application.yaml (base, all environments)
     * - application-dev.yaml (development overrides)
     * - application-staging.yaml (staging overrides)
     * - application-prod.yaml (production overrides)
     *
     * **Example application.yaml:**
     * ```yaml
     * application:
     *   name: katalyst-app
     *   logLevel: INFO
     * database:
     *   url: jdbc:postgresql://localhost:5432/katalyst_db
     *   username: postgres
     * ```
     *
     * **Example application-prod.yaml:**
     * ```yaml
     * application:
     *   logLevel: WARN
     * database:
     *   url: ${DB_URL:jdbc:postgresql://prod-db:5432/katalyst}
     *   username: ${DB_USER:katalyst_user}
     *   password: ${DB_PASS:}
     * ```
     */
    fun exampleProfileBasedConfiguration() {
        log.info("Example 4: Profile-Based Configuration")

        // When KATALYST_PROFILE=prod environment variable is set:
        // 1. application.yaml is loaded
        // 2. application-prod.yaml is loaded
        // 3. application-prod.yaml values override application.yaml values
        // 4. Environment variables are substituted

        val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
        val logLevel = config.getString("application.logLevel")
        log.info("Log level from active profile: $logLevel")
    }

    /**
     * Example 5: Environment Variable Substitution
     *
     * **Scenario:** You want to externalize secrets and environment-specific values.
     *
     * **Syntax:** `${VAR_NAME:defaultValue}`
     *
     * **Example YAML:**
     * ```yaml
     * database:
     *   url: ${DB_URL:jdbc:postgresql://localhost:5432/db}
     *   username: ${DB_USER:postgres}
     *   password: ${DB_PASSWORD:}
     * jwt:
     *   secret: ${JWT_SECRET:change-me-in-production}
     * ```
     *
     * **At Runtime:**
     * - If DB_URL environment variable is set: use it
     * - If DB_URL is not set: use default "jdbc:postgresql://localhost:5432/db"
     * - If DB_PASSWORD env var is not set: use empty string (no default provided)
     */
    fun exampleEnvironmentVariableSubstitution() {
        log.info("Example 5: Environment Variable Substitution")

        // Simulate setting environment variables
        // In real usage: export DB_URL=jdbc:postgresql://prod-db:5432/katalyst

        val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

        // If DB_URL is set in environment, this will use that value
        // Otherwise, it uses the default from YAML
        val dbUrl = config.getString("database.url")
        log.info("Database URL (may be from env var): $dbUrl")
    }

    /**
     * Example 6: Auto-Discovery and Validation of ServiceConfigLoaders
     *
     * **Scenario:** You want to automatically discover and validate all
     * ServiceConfigLoader implementations in your application.
     *
     * **How It Works:**
     * 1. ConfigMetadata scans classpath for ServiceConfigLoader implementations
     * 2. Instantiates each loader
     * 3. Calls validate() on each loader
     * 4. Collects errors and throws if any validation failed
     *
     * **When to Use:**
     * During startup to ensure all configuration is valid before running services.
     *
     * **Example Loaders to Discover:**
     * - DatabaseConfigLoader
     * - JwtConfigLoader
     * - YamlConfigProviderLoader
     * - Any other custom ServiceConfigLoader implementations
     */
    fun exampleAutoDiscoveryAndValidation() {
        log.info("Example 6: Auto-Discovery and Validation")

        try {
            // Load ConfigProvider
            val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

            // Discover all ServiceConfigLoader implementations
            val loaders = ConfigMetadata.discoverLoaders(arrayOf(
                "com.ead.katalyst.config.yaml",      // YamlConfigProviderLoader
                "com.ead.katalyst.example.config"    // DatabaseConfigLoader, JwtConfigLoader
            ))

            log.info("Found ${loaders.size} ServiceConfigLoader implementations")

            // Validate all loaders
            ConfigMetadata.validateLoaders(config, loaders)
            log.info("✓ All ServiceConfigLoader implementations validated successfully")
        } catch (e: Exception) {
            log.error("✗ Configuration validation failed: ${e.message}")
            // In production, this would fail fast before starting services
            throw e
        }
    }

    /**
     * Example 7: Accessing Raw Configuration Values
     *
     * **Scenario:** You need to access configuration values directly using
     * ConfigProvider methods.
     *
     * **Available Methods:**
     * - getString(key, default): Get string value
     * - getInt(key, default): Get integer value
     * - getLong(key, default): Get long value
     * - getBoolean(key, default): Get boolean value
     * - getList(key, default): Get list of strings
     * - get<T>(key, defaultValue): Generic type-safe get
     * - hasKey(key): Check if key exists
     * - getAllKeys(): Get all configuration keys
     */
    fun exampleAccessingRawValues() {
        log.info("Example 7: Accessing Raw Configuration Values")

        val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

        // Access different types of values
        val dbUrl = config.getString("database.url")
        val maxPoolSize = config.getInt("database.pool.maxSize", 10)
        val enableFeature = config.getBoolean("features.newFeature", false)
        val allowedHosts = config.getList("server.allowedHosts", listOf("*"))

        log.info("Database URL: $dbUrl")
        log.info("Max pool size: $maxPoolSize")
        log.info("Feature enabled: $enableFeature")
        log.info("Allowed hosts: $allowedHosts")

        // Get all configuration keys (useful for debugging)
        val allKeys = config.getAllKeys()
        log.info("Total configuration keys: ${allKeys.size}")
    }
}

/**
 * Production implementation functions for configuration loading.
 */
object ConfigurationImplementation {
    private val log = LoggerFactory.getLogger(ConfigurationImplementation::class.java)

    /**
     * Load database configuration for Application.kt bootstrap.
     *
     * **Called during:** katalystApplication { database(loadDatabaseConfig()) }
     *
     * **Process:**
     * 1. Load YamlConfigProvider (loads YAML + profile overrides + env vars)
     * 2. Use DatabaseConfigLoader to extract database configuration
     * 3. Return typed DatabaseConfig for framework
     *
     * @return DatabaseConfig ready to pass to database() framework function
     * @throws ConfigException if configuration is invalid
     */
    fun loadDatabaseConfig(): com.ead.katalyst.config.DatabaseConfig {
        log.info("Loading database configuration from YAML...")
        try {
            // Step 1: Load ConfigProvider (YamlConfigProvider with profiles + env vars)
            val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

            // Step 2: Use DatabaseConfigLoader for type-safe extraction and validation
            val dbConfig = ConfigBootstrapHelper.loadServiceConfig(config, DatabaseConfigLoader())

            log.info("✓ Database configuration loaded successfully")
            return dbConfig
        } catch (e: Exception) {
            log.error("✗ Failed to load database configuration: ${e.message}")
            throw e
        }
    }

    /**
     * Load JWT configuration for services that need authentication.
     *
     * **Example Usage:**
     * ```kotlin
     * fun main(args: Array<String>) = katalystApplication(args) {
     *     val jwtConfig = ConfigurationImplementation.loadJwtConfig()
     *     // Configure JWT settings service
     *     jwtSettingsService.configure(jwtConfig)
     * }
     * ```
     */
    fun loadJwtConfig(): com.ead.katalyst.example.config.loaders.JwtConfig {
        log.info("Loading JWT configuration from YAML...")
        try {
            val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
            val jwtConfig = ConfigBootstrapHelper.loadServiceConfig(config, JwtConfigLoader())

            log.info("✓ JWT configuration loaded successfully")
            return jwtConfig
        } catch (e: Exception) {
            log.error("✗ Failed to load JWT configuration: ${e.message}")
            throw e
        }
    }

    /**
     * Discover and validate all ServiceConfigLoader implementations.
     *
     * **When to Use:**
     * Call this during application startup after loading main configs to ensure
     * all service configurations are valid before starting services.
     *
     * **Example Usage:**
     * ```kotlin
     * fun main(args: Array<String>) = katalystApplication(args) {
     *     database(loadDatabaseConfig())
     *     validateAllConfigLoaders()  // Ensure all configs are valid
     *     scanPackages("com.ead.katalyst.example")
     * }
     * ```
     */
    fun validateAllConfigLoaders() {
        log.info("Discovering and validating all ServiceConfigLoader implementations...")
        try {
            val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

            // Discover loaders in framework and application packages
            val loaders = ConfigMetadata.discoverLoaders(arrayOf(
                "com.ead.katalyst.config.yaml",      // YamlConfigProviderLoader
                "com.ead.katalyst.example.config"    // DatabaseConfigLoader, JwtConfigLoader
            ))

            log.info("Found ${loaders.size} ServiceConfigLoader implementations")

            // Validate all loaders
            ConfigMetadata.validateLoaders(config, loaders)

            log.info("✓ All ServiceConfigLoader implementations validated successfully")
        } catch (e: Exception) {
            log.error("✗ Configuration validation failed: ${e.message}")
            throw e
        }
    }
}

/**
 * Demonstrates how to integrate configuration loading into your application.
 *
 * **Typical Application.kt Main Function (with implementation):**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     // Step 1: Load database config before DI initialization
 *     database(ConfigurationImplementation.loadDatabaseConfig())
 *
 *     // Step 2: Optional - Validate all configurations
 *     ConfigurationImplementation.validateAllConfigLoaders()
 *
 *     // Step 3: Scan application packages (auto-discovers components)
 *     scanPackages("com.ead.katalyst.example")
 * }
 * ```
 *
 * **Advanced Example with all features:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     // Step 1: Load configuration before DI
 *     val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 *
 *     // Step 2: Load service-specific configurations
 *     val jwtConfig = ConfigBootstrapHelper.loadServiceConfig(config, JwtConfigLoader())
 *     val dbConfig = ConfigBootstrapHelper.loadServiceConfig(config, DatabaseConfigLoader())
 *
 *     // Step 3: Discover and validate all loaders
 *     val loaders = ConfigMetadata.discoverLoaders(arrayOf("com.ead.katalyst.example"))
 *     ConfigMetadata.validateLoaders(config, loaders)
 *
 *     // Step 4: Configure framework
 *     database(dbConfig)
 *
 *     // Step 5: Scan application packages
 *     scanPackages("com.ead.katalyst.example")
 * }
 * ```
 */
