package com.ead.katalyst.example.config

import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory
import java.io.File

/**
 * YAML-based configuration provider for Katalyst applications.
 *
 * **Load Order (highest to lowest priority):**
 * 1. `application-{KATALYST_PROFILE}.yaml` (if KATALYST_PROFILE env var set)
 * 2. `application.yaml` (base configuration)
 * 3. Environment variables (override file values)
 *
 * **Environment Variable Substitution:**
 * YAML values can reference environment variables using `${VAR_NAME:defaultValue}` syntax:
 * ```yaml
 * database:
 *   url: ${DB_URL:jdbc:postgresql://localhost:5432/postgres}
 *   username: ${DB_USERNAME:postgres}
 *   password: ${DB_PASSWORD:}
 * ```
 *
 * **Example Profiles:**
 * - Development: `application-dev.yaml` (less strict validation)
 * - Staging: `application-staging.yaml`
 * - Production: `application-prod.yaml` (stricter validation, secrets from env)
 *
 * **Usage:**
 * ```kotlin
 * class DatabaseService(private val config: ConfigProvider) : Service {
 *     fun connect() {
 *         val url = config.getString("database.url")
 *         val user = config.getString("database.username")
 *     }
 * }
 * ```
 */
class YamlConfigProvider : ConfigProvider {
    companion object {
        private val log = LoggerFactory.getLogger(YamlConfigProvider::class.java)
        private const val PROFILE_ENV_VAR = "KATALYST_PROFILE"
        private const val BASE_CONFIG_FILE = "application.yaml"
    }

    private val data: Map<String, Any>

    init {
        try {
            log.info("Loading YAML configuration...")
            data = loadConfiguration()
            log.info("✓ Configuration loaded successfully (${data.size} keys)")
            logActiveProfile()
        } catch (e: Exception) {
            throw ConfigException("Failed to load YAML configuration: ${e.message}", e)
        }
    }

    private fun loadConfiguration(): Map<String, Any> {
        val baseConfig = loadYamlFile(BASE_CONFIG_FILE)
        val profile = System.getenv(PROFILE_ENV_VAR)

        return if (profile != null && profile.isNotBlank()) {
            val profileFile = "application-$profile.yaml"
            log.info("Loading profile-specific configuration: $profileFile")
            val profileConfig = loadYamlFile(profileFile)
            baseConfig.merge(profileConfig)
        } else {
            baseConfig
        }
    }

    private fun loadYamlFile(filename: String): Map<String, Any> {
        val resource = this::class.java.classLoader.getResource(filename)
            ?: return emptyMap()

        val content = resource.readText()
        return parseYaml(content)
    }

    /**
     * Parse YAML content into nested map structure.
     * Simple parser that handles:
     * - Key: value pairs
     * - Nested structure (indentation-based)
     * - Environment variable substitution ${VAR:default}
     */
    private fun parseYaml(content: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val lines = content.lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }

        var currentMap = result
        val mapStack = mutableListOf<Pair<String, MutableMap<String, Any>>>()

        for (line in lines) {
            val indent = line.takeWhile { it == ' ' }.length
            val trimmed = line.trimStart()

            // Reset stack based on indentation
            while (mapStack.isNotEmpty() && mapStack.last().first.length >= indent) {
                mapStack.removeAt(mapStack.size - 1)
            }

            currentMap = if (mapStack.isEmpty()) result else mapStack.last().second

            if (trimmed.endsWith(":") && !trimmed.contains(": ")) {
                // Parent key (nested structure)
                val key = trimmed.dropLast(1)
                val nestedMap = mutableMapOf<String, Any>()
                currentMap[key] = nestedMap
                mapStack.add(key to nestedMap)
            } else if (trimmed.contains(": ")) {
                // Key-value pair
                val (key, value) = trimmed.split(": ", limit = 2)
                currentMap[key] = substituteEnvironmentVariables(value.trim())
            }
        }

        return result
    }

    /**
     * Replace ${VAR_NAME:defaultValue} with environment variable or default.
     */
    private fun substituteEnvironmentVariables(value: String): String {
        val regex = """\$\{([^:}]+)(?::([^}]*))?\}""".toRegex()
        return regex.replace(value) { matchResult ->
            val varName = matchResult.groupValues[1]
            val defaultValue = matchResult.groupValues[2]
            System.getenv(varName) ?: defaultValue ?: ""
        }
    }

    private fun logActiveProfile() {
        val profile = System.getenv(PROFILE_ENV_VAR)
        if (profile != null && profile.isNotBlank()) {
            log.info("Active profile: $profile")
        } else {
            log.info("No active profile set (using default configuration)")
        }
    }

    /**
     * Recursively merge profile config into base config.
     */
    private fun Map<String, Any>.merge(other: Map<String, Any>): Map<String, Any> {
        val result = this.toMutableMap()
        for ((key, value) in other) {
            result[key] = when {
                value is Map<*, *> && result[key] is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    (result[key] as Map<String, Any>).merge(value as Map<String, Any>)
                }
                else -> value
            }
        }
        return result
    }

    override fun <T> get(key: String, defaultValue: T?): T? {
        val value = navigatePath(key)
        return when {
            value == null -> defaultValue
            defaultValue != null && value !is String -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    value as T
                } catch (e: ClassCastException) {
                    throw ConfigException(
                        "Configuration key '$key' has type ${value::class.simpleName}, " +
                                "expected ${defaultValue::class.simpleName}",
                        e
                    )
                }
            }
            else -> {
                @Suppress("UNCHECKED_CAST")
                value as T
            }
        }
    }

    override fun getString(key: String, default: String): String {
        val value = navigatePath(key)
        return when (value) {
            null -> default
            is String -> value
            else -> value.toString()
        }
    }

    override fun getInt(key: String, default: Int): Int {
        val value = navigatePath(key) ?: return default
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    override fun getLong(key: String, default: Long): Long {
        val value = navigatePath(key) ?: return default
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: default
            else -> default
        }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        val value = navigatePath(key) ?: return default
        return when (value) {
            is Boolean -> value
            is String -> when (value.lowercase()) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> default
            }
            is Number -> value.toInt() != 0
            else -> default
        }
    }

    override fun getList(key: String, default: List<String>): List<String> {
        val value = navigatePath(key) ?: return default
        return when (value) {
            is List<*> -> value.map { it?.toString() ?: "" }
            is String -> value.split(",").map { it.trim() }
            else -> default
        }
    }

    override fun hasKey(key: String): Boolean {
        return navigatePath(key) != null
    }

    override fun getAllKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        fun traverse(map: Map<*, *>, prefix: String = "") {
            for ((key, value) in map) {
                val fullKey = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                keys.add(fullKey)
                if (value is Map<*, *>) {
                    traverse(value, fullKey)
                }
            }
        }
        traverse(data)
        return keys
    }

    /**
     * Navigate nested map using dot notation: "database.url" → data["database"]["url"]
     */
    private fun navigatePath(path: String): Any? {
        val parts = path.split(".")
        var current: Any? = data

        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return null
            }
        }

        return current
    }
}
