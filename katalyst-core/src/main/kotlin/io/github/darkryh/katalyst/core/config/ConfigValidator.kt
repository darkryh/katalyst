package io.github.darkryh.katalyst.core.config

import io.github.darkryh.katalyst.core.component.Component

/**
 * Configuration validator interface for startup validation.
 *
 * **Design Philosophy:**
 * - Optional component that applications can implement to validate config at startup
 * - Framework discovers all ConfigValidator implementations automatically
 * - Each validator runs during application bootstrap before Ktor starts
 * - Validation failures throw ConfigException and prevent application startup
 *
 * **When to use:**
 * - Verify required configuration keys are present
 * - Check configuration value constraints (e.g., secret length >= 32 chars)
 * - Validate environment-specific requirements (e.g., stricter rules for production)
 * - Ensure database connectivity during startup
 *
 * **Example Implementation:**
 * ```kotlin
 * class SecurityConfigValidator(private val config: ConfigProvider) : ConfigValidator {
 *     override fun validate() {
 *         val jwtSecret = config.getString("jwt.secret")
 *         val isProd = config.getString("app.environment") == "prod"
 *
 *         if (jwtSecret.isBlank()) {
 *             throw ConfigException("jwt.secret is required")
 *         }
 *
 *         if (isProd && jwtSecret.length < 32) {
 *             throw ConfigException("jwt.secret must be 32+ characters in production")
 *         }
 *     }
 * }
 * ```
 *
 * **How Framework Uses It:**
 * 1. During bootstrap, discovers all ConfigValidator implementations
 * 2. Calls validate() on each in discovery order
 * 3. If any validator throws ConfigException, application startup fails with clear error
 * 4. If all validators pass, application proceeds normally
 *
 * **Execution Timeline:**
 * 1. Koin DI context created
 * 2. ConfigProvider loaded and registered
 * 3. All ConfigValidator instances discovered and validated
 * 4. Other components discovered
 * 5. Database initialized
 * 6. Ktor server starts
 */
interface ConfigValidator : Component {

    /**
     * Validate configuration.
     *
     * Called during application bootstrap before Ktor server starts.
     * If validation fails, throw ConfigException with descriptive message.
     * Application startup will fail and display the error message.
     *
     * @throws ConfigException if validation fails
     */
    fun validate()
}
