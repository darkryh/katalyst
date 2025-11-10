package com.ead.katalyst.config.yaml

import org.slf4j.LoggerFactory

/**
 * Substitutes environment variables in configuration values.
 *
 * **Syntax:** `${VAR_NAME:defaultValue}`
 *
 * **Examples:**
 * - `${DB_URL}` → Environment variable DB_URL or blank if not set
 * - `${DB_URL:jdbc:postgresql://localhost:5432/db}` → DB_URL or default
 * - `${DB_USER:postgres}` → DB_USER or "postgres"
 * - `${SECRET:}` → SECRET or empty string
 *
 * **Usage:**
 * ```kotlin
 * val substitutor = EnvironmentVariableSubstitutor()
 * val result = substitutor.substitute(configMap)  // Recursively substitutes all values
 * ```
 *
 * **Why This Matters:**
 * Allows externalizing secrets and environment-specific values without committing them to YAML:
 * ```yaml
 * database:
 *   url: ${DB_URL:jdbc:postgresql://localhost:5432/katalyst_db}
 *   username: ${DB_USER:postgres}
 *   password: ${DB_PASSWORD:}
 * jwt:
 *   secret: ${JWT_SECRET:change-me-in-production}
 * ```
 *
 * In production, set environment variables. In development, YAML defaults are used.
 */
class EnvironmentVariableSubstitutor {
    companion object {
        private val log = LoggerFactory.getLogger(EnvironmentVariableSubstitutor::class.java)
        // Regex pattern for ${VAR_NAME:defaultValue} syntax
        private val PATTERN = """\$\{([^:}]+)(?::([^}]*))?\}""".toRegex()
    }

    /**
     * Recursively substitute environment variables in all map values.
     *
     * **Process:**
     * 1. Iterate through all map values
     * 2. For String values: substitute environment variables
     * 3. For nested Maps: recursively substitute
     * 4. For Lists: substitute each element if it's a String or Map
     * 5. Return map with all substitutions applied
     *
     * @param map Configuration map to process
     * @return Map with environment variables substituted
     */
    @Suppress("UNCHECKED_CAST")
    fun substitute(map: Map<String, Any>): Map<String, Any> {
        return map.mapValues { (_, value) ->
            when (value) {
                is String -> substitute(value)
                is Map<*, *> -> substitute(value as Map<String, Any>)
                is List<*> -> value.map { item ->
                    when (item) {
                        is String -> substitute(item)
                        is Map<*, *> -> substitute(item as Map<String, Any>)
                        else -> item
                    }
                }
                else -> value
            }
        }
    }

    /**
     * Replace environment variable placeholders in a single string.
     *
     * **Pattern:** `${VAR_NAME:defaultValue}`
     *
     * **Matching Groups:**
     * - Group 1: Variable name (required, e.g., "DB_URL")
     * - Group 2: Default value (optional, e.g., "localhost")
     *
     * **Logic:**
     * - If environment variable exists: use its value
     * - If not exists and default provided: use default
     * - If not exists and no default: use empty string
     *
     * **Examples:**
     * ```
     * Input:  "jdbc:postgresql://${DB_HOST:localhost}:5432/db"
     * Output: "jdbc:postgresql://localhost:5432/db"  (if DB_HOST not set)
     *
     * Input:  "jdbc:postgresql://${DB_HOST:localhost}:5432/db"
     * Output: "jdbc:postgresql://prod-db:5432/db"  (if DB_HOST="prod-db")
     * ```
     *
     * @param value String potentially containing ${VAR:default} patterns
     * @return String with all patterns substituted
     */
    fun substitute(value: String): String {
        return PATTERN.replace(value) { matchResult ->
            val varName = matchResult.groupValues[1]
            val defaultValue = matchResult.groupValues[2]
            val envValue = System.getenv(varName)

            when {
                envValue != null -> {
                    log.debug("Substituted environment variable: $varName")
                    envValue
                }
                defaultValue.isNotEmpty() -> {
                    log.debug("Using default value for missing environment variable: $varName")
                    defaultValue
                }
                else -> {
                    log.debug("Using empty string for missing environment variable: $varName")
                    ""
                }
            }
        }
    }
}
