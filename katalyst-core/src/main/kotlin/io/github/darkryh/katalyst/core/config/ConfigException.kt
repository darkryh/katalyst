package io.github.darkryh.katalyst.core.config

/**
 * Exception thrown when configuration loading or validation fails.
 *
 * Raised in these scenarios:
 * - YAML/config file cannot be parsed
 * - Required configuration key is missing
 * - Configuration value cannot be converted to requested type
 * - Startup validation fails
 */
class ConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
