package com.ead.katalyst.example.config.loaders

import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.config.provider.ServiceConfigLoader
import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * JWT configuration data class.
 *
 * @param secret JWT signing secret (min 32 characters for HS256)
 * @param issuer Token issuer claim (identifies who issued the token)
 * @param audience Token audience claim (identifies intended recipients)
 * @param expirationTime Token expiration duration from issue time
 * @param algorithm JWT signing algorithm (e.g., "HS256", "RS256")
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val expirationTime: Duration,
    val algorithm: String
)

/**
 * ServiceConfigLoader implementation for JwtConfig.
 *
 * **Purpose:**
 * Loads JWT configuration from ConfigProvider in a type-safe manner.
 * Can be auto-discovered and validated during application startup.
 *
 * **Configuration Keys:**
 * - jwt.secret (REQUIRED, min 32 characters)
 * - jwt.issuer (REQUIRED)
 * - jwt.audience (REQUIRED)
 * - jwt.expirationTime (optional, in milliseconds, default: 24 hours)
 * - jwt.algorithm (optional, default: HS256)
 *
 * **Example YAML:**
 * ```yaml
 * jwt:
 *   secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}
 *   issuer: katalyst-app
 *   audience: katalyst-api
 *   expirationTime: 86400000  # 24 hours in milliseconds
 *   algorithm: HS256
 * ```
 *
 * **Usage in Application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 *     val jwtConfig = ConfigBootstrapHelper.loadServiceConfig(config, JwtConfigLoader())
 *     // Use jwtConfig to configure JWT authentication
 *     scanPackages("com.ead.katalyst.example")
 * }
 * ```
 *
 * **Auto-Discovery:**
 * ```kotlin
 * val loaders = ConfigMetadata.discoverLoaders(arrayOf("com.ead.katalyst.example"))
 * // JwtConfigLoader is automatically discovered and validated
 * ```
 */
class JwtConfigLoader : ServiceConfigLoader<JwtConfig> {
    companion object {
        private val log = LoggerFactory.getLogger(JwtConfigLoader::class.java)
        private const val MIN_SECRET_LENGTH = 32
    }

    /**
     * Load JWT configuration from ConfigProvider.
     *
     * **Process:**
     * 1. Extract required keys (secret, issuer, audience)
     * 2. Extract optional keys with defaults (expirationTime, algorithm)
     * 3. Construct and return JwtConfig
     * 4. Validation happens in validate() method
     *
     * @param provider ConfigProvider to load from
     * @return Loaded JwtConfig instance
     * @throws ConfigException if required keys are missing
     */
    override fun loadConfig(provider: ConfigProvider): JwtConfig {
        log.debug("Loading JWT configuration...")

        // Load required keys
        val secret = ConfigLoaders.loadRequiredString(provider, "jwt.secret")
        val issuer = ConfigLoaders.loadRequiredString(provider, "jwt.issuer")
        val audience = ConfigLoaders.loadRequiredString(provider, "jwt.audience")

        // Load optional keys with defaults
        val expirationTimeMillis = ConfigLoaders.loadOptionalLong(provider, "jwt.expirationTime", 86_400_000L) // 24 hours
        val algorithm = ConfigLoaders.loadOptionalString(provider, "jwt.algorithm", "HS256")

        return JwtConfig(
            secret = secret,
            issuer = issuer,
            audience = audience,
            expirationTime = Duration.ofMillis(expirationTimeMillis),
            algorithm = algorithm
        ).also {
            log.debug("✓ JWT configuration loaded: issuer=$issuer, algorithm=$algorithm")
        }
    }

    /**
     * Validate loaded JWT configuration.
     *
     * **Validation Checks:**
     * 1. Secret is at least 32 characters (256 bits for HS256)
     * 2. Issuer is not blank
     * 3. Audience is not blank
     * 4. Expiration time is positive
     * 5. Algorithm is supported
     *
     * **Why These Checks:**
     * - Short secrets are cryptographically weak and allow brute force attacks
     * - Empty issuer/audience make tokens less secure and portable
     * - Negative expiration time doesn't make sense
     * - Unsupported algorithms will fail at runtime
     *
     * @param config JwtConfig to validate
     * @throws ConfigException if validation fails
     */
    override fun validate(config: JwtConfig) {
        log.debug("Validating JWT configuration...")

        try {
            // Validate secret length
            if (config.secret.length < MIN_SECRET_LENGTH) {
                throw ConfigException(
                    "JWT secret must be at least $MIN_SECRET_LENGTH characters (256 bits). " +
                            "Current length: ${config.secret.length}. " +
                            "Use environment variable JWT_SECRET in production."
                )
            }

            // Validate issuer and audience
            if (config.issuer.isBlank()) {
                throw ConfigException("JWT issuer cannot be blank")
            }
            if (config.audience.isBlank()) {
                throw ConfigException("JWT audience cannot be blank")
            }

            // Validate expiration time
            if (!config.expirationTime.isPositive) {
                throw ConfigException("JWT expirationTime must be positive (in milliseconds)")
            }

            // Validate algorithm
            val supportedAlgorithms = listOf("HS256", "HS384", "HS512", "RS256", "RS384", "RS512")
            if (config.algorithm !in supportedAlgorithms) {
                throw ConfigException(
                    "JWT algorithm '${config.algorithm}' is not supported. " +
                            "Supported algorithms: $supportedAlgorithms"
                )
            }

            log.debug("✓ JWT configuration validation passed")
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("JWT configuration validation failed: ${e.message}", e)
        }
    }
}
