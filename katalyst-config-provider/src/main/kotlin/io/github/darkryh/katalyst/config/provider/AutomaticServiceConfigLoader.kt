package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

/**
 * Interface for service configuration loaders that are automatically discovered and instantiated
 * during Katalyst DI initialization.
 *
 * Unlike [ServiceConfigLoader] which requires explicit manual loading via [ConfigBootstrapHelper],
 * `AutomaticServiceConfigLoader` implementations are discovered, loaded, validated, and registered
 * as singletons in Koin automatically during the component registration orchestration phase.
 *
 * **Usage Pattern:**
 *
 * 1. Implement this interface for your configuration:
 * ```kotlin
 * data class SmtpConfig(
 *     val host: String,
 *     val port: Int = 25,
 *     val username: String = "",
 *     val password: String = ""
 * )
 *
 * object SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> {
 *     override val configType = SmtpConfig::class
 *
 *     override fun loadConfig(provider: ConfigProvider): SmtpConfig {
 *         return SmtpConfig(
 *             host = ConfigLoaders.loadRequiredString(provider, "smtp.host"),
 *             port = ConfigLoaders.loadOptionalInt(provider, "smtp.port", 25),
 *             username = ConfigLoaders.loadOptionalString(provider, "smtp.username"),
 *             password = ConfigLoaders.loadOptionalString(provider, "smtp.password")
 *         )
 *     }
 *
 *     override fun validate(config: SmtpConfig) {
 *         require(config.host.isNotBlank()) { "SMTP host is required" }
 *         require(config.port > 0) { "SMTP port must be > 0" }
 *     }
 * }
 * ```
 *
 * 2. Components automatically receive the loaded configuration via constructor injection:
 * ```kotlin
 * class SmtpDeliveryService(
 *     val smtpConfig: SmtpConfig  // Auto-injected by DI
 * ) : Service {
 *     // Use smtpConfig directly, no manual loadConfig() calls needed
 * }
 * ```
 *
 * 3. The framework automatically:
 *    - Discovers all `AutomaticServiceConfigLoader<T>` implementations during component scanning
 *    - Loads each configuration during DI initialization (Phase 6a)
 *    - Validates each configuration (fail-fast on errors)
 *    - Registers each configuration as a singleton in Koin
 *    - Logs all loaded and registered configurations
 *
 * **Benefits:**
 * - Type-safe configuration injection (config type known at compile time)
 * - Automatic discovery (no manual registration needed)
 * - Fail-fast validation (config errors caught at startup)
 * - Centralized logging (see all configs in startup output)
 * - No boilerplate (no manual loadConfig() or ConfigBootstrapHelper calls)
 * - Testable (easy to mock loaders for unit tests)
 *
 * **Compared to ServiceConfigLoader:**
 * - ServiceConfigLoader: Manual loading via `ConfigBootstrapHelper.loadServiceConfig()`
 * - AutomaticServiceConfigLoader: Auto-discovered and auto-registered during DI bootstrap
 *
 * **Implementation Requirements:**
 * - Must be discoverable via classpath scanning (typically uses `object` declarations)
 * - Must provide `configType: KClass<T>` for type-safe registration
 * - Should provide a no-arg `loadConfig(provider: ConfigProvider): T` implementation
 * - May override `validate(config: T)` for validation logic
 * - Should be registered in the same package as the configuration class it loads
 *
 * @param T The configuration type this loader produces and registers
 *
 * @see ServiceConfigLoader For manual configuration loading
 * @see ConfigProvider For accessing configuration values
 * @see ConfigLoaders For utility functions to load individual config values
 */
interface AutomaticServiceConfigLoader<T : Any> {
    /**
     * The type of configuration this loader produces.
     *
     * Used by the framework to register the loaded configuration in Koin with proper type information.
     * This ensures that components can receive the configuration via constructor injection:
     *
     * ```kotlin
     * class MyService(config: SmtpConfig) : Service
     * ```
     *
     * **Important:** This must match the actual type produced by [loadConfig].
     */
    val configType: KClass<T>

    /**
     * Load and construct the service configuration from the provided [ConfigProvider].
     *
     * This method is called automatically during DI initialization (Phase 6a).
     * It should extract the necessary configuration values from the provider and construct
     * a fully-initialized configuration object.
     *
     * **Responsibilities:**
     * - Load all required configuration keys (throw exception if missing)
     * - Load optional configuration keys with sensible defaults
     * - Perform any necessary transformations (e.g., URL parsing, enum conversion)
     * - Return a fully-initialized configuration object
     *
     * **Notes:**
     * - This method is called once during application startup
     * - Exceptions thrown here will cause application startup to fail (fail-fast)
     * - Use [ConfigLoaders] utility functions for consistent loading patterns
     *
     * @param provider The ConfigProvider instance with all configuration values
     * @return A fully-initialized configuration object (never null)
     * @throws Exception if required configuration is missing or invalid format
     *
     * @see ConfigLoaders For utility functions to load individual configuration values
     */
    fun loadConfig(provider: ConfigProvider): T

    /**
     * Validate the loaded configuration object.
     *
     * This method is called automatically after [loadConfig] completes and before the configuration
     * is registered in Koin. Use this method to validate constraints that couldn't be checked during
     * loading (e.g., cross-field validation, complex business rules).
     *
     * **Default Behavior:** Does nothing (no validation). Override to add custom validation.
     *
     * **Best Practices:**
     * - Use `require()` for validation that ensures application correctness
     * - Provide descriptive error messages that help diagnose configuration issues
     * - Validate both individual fields and inter-field dependencies
     *
     * **Example:**
     * ```kotlin
     * override fun validate(config: SmtpConfig) {
     *     require(config.host.isNotBlank()) { "SMTP host is required" }
     *     require(config.port > 0) { "SMTP port must be > 0" }
     *     require(config.port <= 65535) { "SMTP port must be <= 65535" }
     *     if (config.useTls) {
     *         require(config.password.isNotBlank()) { "Password required when TLS is enabled" }
     *     }
     * }
     * ```
     *
     * @param config The configuration object to validate
     * @throws IllegalArgumentException if validation fails
     */
    fun validate(config: T) {
        // Default: no validation required
    }
}
