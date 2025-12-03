package io.github.darkryh.katalyst.core.config

import io.github.darkryh.katalyst.core.component.Component

/**
 * Configuration provider interface for Katalyst applications.
 *
 * **Design Philosophy:**
 * - Marker interface to integrate configuration into Katalyst's automatic discovery
 * - Applications implement this interface to provide configuration
 * - Framework automatically discovers and injects implementations via reflection
 * - No annotations or manual wiring needed
 *
 * **How to use:**
 * 1. Create a class implementing ConfigProvider
 * 2. Load configuration from YAML, properties, environment, etc.
 * 3. Framework discovers it automatically (implements Component)
 * 4. Inject into services via constructor: `class MyService(config: ConfigProvider)`
 *
 * **Example Implementation:**
 * ```kotlin
 * class YamlConfigProvider : ConfigProvider {
 *     private val data = loadYamlFile("application.yaml")
 *
 *     override fun getString(key: String, default: String): String =
 *         navigatePath(key) as? String ?: default
 * }
 * ```
 *
 * **Example Usage in Service:**
 * ```kotlin
 * class DatabaseService(private val config: ConfigProvider) : Service {
 *     fun getDatabaseUrl(): String = config.getString("database.url")
 * }
 * ```
 */
interface ConfigProvider : Component {

    /**
     * Retrieve a configuration value by key path.
     *
     * Paths use dot notation: "database.url", "jwt.secret", "server.port"
     *
     * @param key Dot-separated path to configuration value
     * @param defaultValue Value to return if key not found (default: null)
     * @return Configuration value or defaultValue if not found
     * @throws ConfigException if value cannot be converted to requested type T
     */
    fun <T> get(key: String, defaultValue: T? = null): T?

    /**
     * Retrieve a string configuration value.
     *
     * @param key Dot-separated path to configuration value
     * @param default Value to return if key not found (default: "")
     * @return String configuration value or default if not found
     * @throws ConfigException if value exists but cannot be converted to String
     */
    fun getString(key: String, default: String = ""): String

    /**
     * Retrieve an integer configuration value.
     *
     * @param key Dot-separated path to configuration value
     * @param default Value to return if key not found (default: 0)
     * @return Integer configuration value or default if not found or unparseable
     */
    fun getInt(key: String, default: Int = 0): Int

    /**
     * Retrieve a long configuration value.
     *
     * @param key Dot-separated path to configuration value
     * @param default Value to return if key not found (default: 0L)
     * @return Long configuration value or default if not found or unparseable
     */
    fun getLong(key: String, default: Long = 0L): Long

    /**
     * Retrieve a boolean configuration value.
     *
     * Accepts: true/false, yes/no, on/off, 1/0 (case-insensitive)
     *
     * @param key Dot-separated path to configuration value
     * @param default Value to return if key not found (default: false)
     * @return Boolean configuration value or default if not found or unparseable
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean

    /**
     * Retrieve a list of strings from configuration.
     *
     * Format depends on implementation (YAML arrays, comma-separated, etc.)
     *
     * @param key Dot-separated path to configuration value
     * @param default Value to return if key not found (default: empty list)
     * @return List of strings or default if not found
     */
    fun getList(key: String, default: List<String> = emptyList()): List<String>

    /**
     * Check if a configuration key exists.
     *
     * @param key Dot-separated path to configuration value
     * @return true if key exists, false otherwise
     */
    fun hasKey(key: String): Boolean

    /**
     * Retrieve all configuration keys.
     *
     * @return Set of all configuration keys in dot notation
     */
    fun getAllKeys(): Set<String>
}
