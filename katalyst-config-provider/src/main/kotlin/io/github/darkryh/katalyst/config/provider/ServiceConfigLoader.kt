package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider

/**
 * Type-safe configuration loader for services.
 *
 * **Purpose:**
 * Provides a clean pattern for loading service-specific configuration from ConfigProvider.
 * Each service configuration (JWT, Database, etc.) implements this interface.
 *
 * **Design Benefits:**
 * - Type-safe configuration loading
 * - Decouples config loading from service constructors
 * - Reusable across projects
 * - Testable: easy to mock for unit tests
 * - Self-documenting: clear which keys are required
 *
 * **Example Implementation:**
 * ```kotlin
 * data class DatabaseConfig(
 *     val url: String,
 *     val username: String,
 *     val password: String,
 *     val maxPoolSize: Int
 * )
 *
 * class DatabaseConfigLoader : ServiceConfigLoader<DatabaseConfig> {
 *     override fun loadConfig(provider: ConfigProvider): DatabaseConfig {
 *         return DatabaseConfig(
 *             url = provider.getString("database.url"),
 *             username = provider.getString("database.username"),
 *             password = provider.getString("database.password"),
 *             maxPoolSize = provider.getInt("database.pool.maxSize", 10)
 *         )
 *     }
 *
 *     override fun validate(config: DatabaseConfig) {
 *         if (config.url.isBlank()) {
 *             throw ConfigException("database.url cannot be blank")
 *         }
 *     }
 * }
 * ```
 *
 * **Usage in Application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 *     val databaseConfig = DatabaseConfigLoader().loadConfig(config)
 *     database(ConfigBootstrapHelper.convertToDatabaseConfig(databaseConfig))
 * }
 * ```
 *
 * @param T The type of configuration this loader produces
 */
interface ServiceConfigLoader<T> {

    /**
     * Load and construct service configuration from ConfigProvider.
     *
     * **Responsibility:**
     * - Extract configuration values using provided ConfigProvider
     * - Construct and return typed configuration object
     * - Let validation handle error checking (see validate())
     *
     * **Should NOT:**
     * - Perform validation (use validate() instead)
     * - Have side effects
     * - Access external resources
     *
     * @param provider ConfigProvider to load values from
     * @return Loaded and constructed service configuration
     * @throws ConfigException if required configuration keys are missing
     */
    fun loadConfig(provider: ConfigProvider): T

    /**
     * Validate loaded service configuration.
     *
     * **Optional:** Override to perform validation after loading.
     *
     * Called after loadConfig() to verify:
     * - Configuration constraints (min/max values, length checks, etc.)
     * - Environment-specific rules (e.g., stricter in production)
     * - Cross-field validations (e.g., port in valid range)
     *
     * **Example:**
     * ```kotlin
     * override fun validate(config: JwtConfig) {
     *     if (config.secret.length < 32) {
     *         throw ConfigException("JWT secret must be 32+ characters")
     *     }
     * }
     * ```
     *
     * @param config Loaded configuration to validate
     * @throws ConfigException if validation fails
     */
    fun validate(config: T) {
        // Default: no validation. Override to add constraints.
    }
}
