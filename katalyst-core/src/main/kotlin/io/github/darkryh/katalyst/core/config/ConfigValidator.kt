package io.github.darkryh.katalyst.core.config

import io.github.darkryh.katalyst.core.component.Component

/**
 * Marker interface for a component that validates configuration.
 *
 * **Current behavior:**
 * `ConfigValidator` extends [Component], so an implementation under a scanned package is
 * discovered, constructed with dependency injection, and registered in the container exactly
 * like any other [Component]. There is currently no framework-driven mechanism that calls
 * [validate] automatically — nothing in bootstrap invokes it for you. Callers that want
 * fail-fast config validation must inject the implementation and call [validate] themselves
 * (for example, from a `StartupHook`).
 *
 * **When to use:**
 * - Verify required configuration keys are present
 * - Check configuration value constraints (e.g., secret length >= 32 chars)
 * - Validate environment-specific requirements (e.g., stricter rules for production)
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
 */
interface ConfigValidator : Component {

    /**
     * Validate configuration.
     *
     * Not called automatically by the framework — invoke it explicitly (for example, from a
     * `StartupHook`) if you need it to run during bootstrap.
     *
     * @throws ConfigException if validation fails
     */
    fun validate()
}
