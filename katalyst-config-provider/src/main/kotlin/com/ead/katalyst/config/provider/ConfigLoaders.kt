package com.ead.katalyst.config.provider

import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import java.time.Duration

/**
 * Utility functions for common configuration loading patterns.
 *
 * **Purpose:**
 * Provides reusable, type-safe helpers for loading common configuration types
 * from ConfigProvider. Reduces boilerplate in ServiceConfigLoader implementations.
 *
 * **Usage:**
 * ```kotlin
 * class MyConfigLoader : ServiceConfigLoader<MyConfig> {
 *     override fun loadConfig(provider: ConfigProvider): MyConfig {
 *         return MyConfig(
 *             requiredString = ConfigLoaders.loadRequiredString(provider, "key"),
 *             optionalInt = ConfigLoaders.loadOptionalInt(provider, "port", 8080),
 *             duration = ConfigLoaders.loadDuration(provider, "timeout", Duration.ofSeconds(30))
 *         )
 *     }
 * }
 * ```
 */
object ConfigLoaders {

    /**
     * Load a required string value (must exist).
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @return String value
     * @throws ConfigException if key is missing or blank
     */
    fun loadRequiredString(provider: ConfigProvider, key: String): String {
        val value = provider.getString(key)
        if (value.isBlank()) {
            throw ConfigException("Required configuration key '$key' is missing or blank")
        }
        return value
    }

    /**
     * Load an optional string value with default.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @param default Default value if key is missing
     * @return String value or default
     */
    fun loadOptionalString(provider: ConfigProvider, key: String, default: String = ""): String {
        return provider.getString(key, default)
    }

    /**
     * Load a required integer value.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @return Integer value
     * @throws ConfigException if key is missing or not a valid integer
     */
    fun loadRequiredInt(provider: ConfigProvider, key: String): Int {
        val value = provider.getString(key)
        return value.toIntOrNull()
            ?: throw ConfigException("Configuration key '$key' must be a valid integer, got: $value")
    }

    /**
     * Load an optional integer value with default.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @param default Default value if key is missing
     * @return Integer value or default
     */
    fun loadOptionalInt(provider: ConfigProvider, key: String, default: Int = 0): Int {
        return provider.getInt(key, default)
    }


    /**
     * Load an optional boolean value with default.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @param default Default value if key is missing
     * @return Boolean value or default
     */
    fun loadOptionalBoolean(provider: ConfigProvider, key: String, default: Boolean = false): Boolean {
        return provider.getBoolean(key, default)
    }

    /**
     * Load a required long value.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @return Long value
     * @throws ConfigException if key is missing or not a valid long
     */
    fun loadRequiredLong(provider: ConfigProvider, key: String): Long {
        val value = provider.getString(key)
        return value.toLongOrNull()
            ?: throw ConfigException("Configuration key '$key' must be a valid long, got: $value")
    }

    /**
     * Load an optional long value with default.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @param default Default value if key is missing
     * @return Long value or default
     */
    fun loadOptionalLong(provider: ConfigProvider, key: String, default: Long = 0L): Long {
        return provider.getLong(key, default)
    }

    /**
     * Load a duration from milliseconds.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation (value in milliseconds)
     * @param default Default duration if key is missing
     * @return Duration
     */
    fun loadDuration(provider: ConfigProvider, key: String, default: Duration): Duration {
        val millis = provider.getLong(key, -1L)
        return if (millis > 0) Duration.ofMillis(millis) else default
    }

    /**
     * Load a list of strings.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @param default Default list if key is missing
     * @return List of strings
     */
    fun loadList(provider: ConfigProvider, key: String, default: List<String> = emptyList()): List<String> {
        return provider.getList(key, default)
    }

    /**
     * Load an integer range (min and max values).
     *
     * @param provider ConfigProvider to load from
     * @param minKey Configuration key for minimum value
     * @param maxKey Configuration key for maximum value
     * @param defaultMin Default minimum if key is missing
     * @param defaultMax Default maximum if key is missing
     * @return Pair of (min, max)
     * @throws ConfigException if min > max
     */
    fun loadIntRange(
        provider: ConfigProvider,
        minKey: String,
        maxKey: String,
        defaultMin: Int = 0,
        defaultMax: Int = Int.MAX_VALUE
    ): Pair<Int, Int> {
        val min = loadOptionalInt(provider, minKey, defaultMin)
        val max = loadOptionalInt(provider, maxKey, defaultMax)

        if (min > max) {
            throw ConfigException("Configuration range invalid: $minKey ($min) > $maxKey ($max)")
        }

        return Pair(min, max)
    }

    /**
     * Load an enum value from string.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @param default Default enum value if key is missing
     * @return Enum value
     * @throws ConfigException if string value doesn't match any enum constant
     */
    inline fun <reified T : Enum<T>> loadEnum(
        provider: ConfigProvider,
        key: String,
        default: T
    ): T {
        val value = provider.getString(key)
        return if (value.isBlank()) {
            default
        } else {
            try {
                java.lang.Enum.valueOf(T::class.java, value.uppercase())
            } catch (e: IllegalArgumentException) {
                throw ConfigException(
                    "Configuration key '$key' must be one of: ${enumValues<T>().joinToString()}, got: $value"
                )
            }
        }
    }

    /**
     * Load a boolean value with multiple accepted formats.
     *
     * Accepts:
     * - true/false
     * - yes/no
     * - on/off
     * - 1/0
     * - enabled/disabled
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @param default Default boolean if key is missing
     * @return Boolean value
     */
    fun loadBoolean(provider: ConfigProvider, key: String, default: Boolean = false): Boolean {
        return provider.getBoolean(key, default)
    }

    /**
     * Validate that all required keys exist in configuration.
     *
     * @param provider ConfigProvider to check
     * @param requiredKeys List of required configuration keys
     * @throws ConfigException if any required key is missing
     */
    fun validateRequiredKeys(provider: ConfigProvider, vararg requiredKeys: String) {
        val missingKeys = requiredKeys.filter { !provider.hasKey(it) }
        if (missingKeys.isNotEmpty()) {
            throw ConfigException("Missing required configuration keys: ${missingKeys.joinToString()}")
        }
    }
}
