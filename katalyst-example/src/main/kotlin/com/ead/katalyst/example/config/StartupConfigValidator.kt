package com.ead.katalyst.example.config

import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import com.ead.katalyst.core.config.ConfigValidator
import org.slf4j.LoggerFactory

/**
 * Startup configuration validator.
 *
 * Automatically discovered and executed during application bootstrap
 * (before Ktor server starts).
 *
 * Validates that:
 * - Required configuration keys are present
 * - Configuration values meet environment-specific requirements
 * - Secrets are properly configured for the active environment
 */
class StartupConfigValidator(private val config: ConfigProvider) : ConfigValidator {
    companion object {
        private val log = LoggerFactory.getLogger(StartupConfigValidator::class.java)
    }

    override fun validate() {
        log.info("Validating startup configuration...")

        validateDatabase()
        validateJwt()
        validateEnvironmentSpecific()

        log.info("✓ Configuration validation passed")
    }

    private fun validateDatabase() {
        val url = config.getString("database.url")
        if (url.isBlank()) {
            throw ConfigException("database.url is required")
        }

        val username = config.getString("database.username")
        if (username.isBlank()) {
            throw ConfigException("database.username is required")
        }

        log.debug("✓ Database configuration validated")
    }

    private fun validateJwt() {
        val secret = config.getString("jwt.secret")
        if (secret.isBlank()) {
            throw ConfigException("jwt.secret is required and must not be blank")
        }

        val environment = config.getString("app.environment", "development")
        val isProd = environment.equals("production", ignoreCase = true)

        if (isProd && secret.length < 32) {
            throw ConfigException(
                "jwt.secret must be at least 32 characters long in production " +
                        "(current length: ${secret.length})"
            )
        }

        if (secret == "local-secret-for-development-only") {
            log.warn("⚠️  Using default JWT secret - ensure to configure JWT_SECRET env var in production")
        }

        val issuer = config.getString("jwt.issuer")
        if (issuer.isBlank()) {
            throw ConfigException("jwt.issuer is required")
        }

        log.debug("✓ JWT configuration validated")
    }

    private fun validateEnvironmentSpecific() {
        val environment = config.getString("app.environment", "development")
        val debug = config.getBoolean("app.debug", false)

        when (environment.lowercase()) {
            "production" -> {
                if (debug) {
                    throw ConfigException("Debug mode must be disabled in production")
                }
                log.info("Production environment detected - strict validation enabled")
            }
            "staging" -> {
                log.info("Staging environment detected")
            }
            "development" -> {
                log.info("Development environment detected - lenient validation enabled")
            }
            else -> {
                throw ConfigException("Unknown environment: $environment (allowed: development, staging, production)")
            }
        }

        log.debug("✓ Environment-specific validation passed")
    }
}
