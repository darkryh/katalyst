package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * YAML-based configuration provider for Katalyst applications using SnakeYAML.
 *
 * **Implements Component Interface:**
 * This class implements `Component` so it's automatically discovered and registered
 * in Koin DI container during application startup. Services can depend on ConfigProvider
 * and will receive this implementation automatically.
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
 * **Usage in Services:**
 * ```kotlin
 * class DatabaseService(private val config: ConfigProvider) : Service {
 *     fun connect() {
 *         val url = config.getString("database.url")
 *         val user = config.getString("database.username")
 *     }
 * }
 * ```
 *
 * **Bootstrap Usage:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     // Load before DI scans components
 *     database(ConfigurationImplementation.loadDatabaseConfig())
 *
 *     // YamlConfigProvider is auto-discovered during scanPackages()
 *     // and injected into services that depend on ConfigProvider
 *     scanPackages("io.github.darkryh.katalyst.example")
 * }
 * ```
 *
 * **Internal Composition:**
 * Uses YamlProfileLoader for profile-based loading and YamlParser for parsing.
 * This separation allows reusing these components independently.
 */
class YamlConfigProvider(
    profileLoader: YamlProfileLoader = YamlProfileLoader(),
    substitutor: EnvironmentVariableSubstitutor = EnvironmentVariableSubstitutor()
) : ConfigProvider, Component {
    companion object {
        private val log = LoggerFactory.getLogger(YamlConfigProvider::class.java)
    }

    private val data: Map<String, Any>

    init {
        try {
            log.info("Loading YAML configuration...")
            data = substitutor.substitute(profileLoader.loadConfiguration())
            validateRequiredKeys()
            log.info("✓ Configuration loaded successfully (${data.size} keys)")
        } catch (e: Exception) {
            throw ConfigException("Failed to load YAML configuration: ${e.message}", e)
        }
    }

    override fun <T> get(key: String, defaultValue: T?): T? {
        val value = navigatePath(key) ?: return defaultValue
        if (defaultValue != null) {
            val expectedType = defaultValue::class
            if (!expectedType.isInstance(value)) {
                throw ConfigException(
                    "Configuration key '$key' has type ${value::class.simpleName}, expected ${expectedType.simpleName}"
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun getString(key: String, default: String): String {
        return when (val value = navigatePath(key)) {
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
    @Suppress("UNCHECKED_CAST")
    private fun navigatePath(path: String): Any? {
        val parts = path.split(".")
        var current: Any? = data

        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> (current as Map<String, Any>)[part]
                else -> return null
            }
        }

        return current
    }

    /**
     * Validate presence of critical keys after merging base/profile configs.
     * Crashes fast when required settings are missing to avoid silent fallbacks.
     */
    private fun validateRequiredKeys() {
        val requiredKeys = listOf(
            // Ktor deployment essentials
            "ktor.deployment.host",
            "ktor.deployment.port",
            "ktor.deployment.shutdownGracePeriod",
            "ktor.deployment.shutdownTimeout",
            // Database essentials
            "database.url",
            "database.username",
            "database.driver"
        )

        val missing = requiredKeys.filter { navigatePath(it) == null }
        if (missing.isNotEmpty()) {
            throw ConfigException(
                "Missing required configuration keys: ${missing.joinToString(", ")}"
            )
        }
    }
}
