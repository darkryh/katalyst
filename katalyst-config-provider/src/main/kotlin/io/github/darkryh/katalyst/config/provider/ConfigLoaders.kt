package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import java.time.Duration

/**
 * Read a required string and fail fast when the key is missing or blank.
 */
fun ConfigProvider.requiredString(key: String): String =
    ConfigLoaders.loadRequiredString(this, key)

/**
 * Read a string when present, returning null when the key is missing.
 */
fun ConfigProvider.stringOrNull(key: String): String? =
    ConfigLoaders.loadStringOrNull(this, key)

/**
 * Read an optional string, returning [default] only when the key is missing.
 */
fun ConfigProvider.optionalString(key: String, default: String = ""): String =
    ConfigLoaders.loadOptionalString(this, key, default)

/**
 * Read a required integer and fail fast when the key is missing or malformed.
 */
fun ConfigProvider.requiredInt(key: String): Int =
    ConfigLoaders.loadRequiredInt(this, key)

/**
 * Read an integer when present, returning null when the key is missing and
 * failing fast when the present value is malformed.
 */
fun ConfigProvider.intOrNull(key: String): Int? =
    ConfigLoaders.loadIntOrNull(this, key)

/**
 * Read an optional integer using the provider's compatibility fallback behavior.
 */
fun ConfigProvider.optionalInt(key: String, default: Int = 0): Int =
    ConfigLoaders.loadOptionalInt(this, key, default)

/**
 * Read a required long and fail fast when the key is missing or malformed.
 */
fun ConfigProvider.requiredLong(key: String): Long =
    ConfigLoaders.loadRequiredLong(this, key)

/**
 * Read a long when present, returning null when the key is missing and
 * failing fast when the present value is malformed.
 */
fun ConfigProvider.longOrNull(key: String): Long? =
    ConfigLoaders.loadLongOrNull(this, key)

/**
 * Read an optional long using the provider's compatibility fallback behavior.
 */
fun ConfigProvider.optionalLong(key: String, default: Long = 0L): Long =
    ConfigLoaders.loadOptionalLong(this, key, default)

/**
 * Read a required boolean and fail fast when the key is missing or malformed.
 */
fun ConfigProvider.requiredBoolean(key: String): Boolean =
    ConfigLoaders.loadRequiredBoolean(this, key)

/**
 * Read a boolean with false as the default for missing keys.
 *
 * Unlike [optionalBoolean], malformed present values fail fast instead of
 * silently falling back to the default.
 */
fun ConfigProvider.boolean(key: String, default: Boolean = false): Boolean =
    ConfigLoaders.loadBoolean(this, key, default)

/**
 * Read an optional boolean using the provider's compatibility fallback behavior.
 */
fun ConfigProvider.optionalBoolean(key: String, default: Boolean = false): Boolean =
    ConfigLoaders.loadOptionalBoolean(this, key, default)

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
 *             optionalPort = ConfigLoaders.loadIntOrNull(provider, "port"),
 *             enabled = ConfigLoaders.loadBoolean(provider, "enabled")
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
        ensurePresent(provider, key)
        val value = provider.getString(key)
        if (value.isBlank()) {
            throw ConfigException("Required configuration key '$key' is missing or blank")
        }
        return value
    }

    /**
     * Load a string value when present, otherwise null.
     *
     * This is the preferred optional string API for Kotlin config objects with
     * nullable properties.
     */
    fun loadStringOrNull(provider: ConfigProvider, key: String): String? {
        val value = rawValueOrNull(provider, key) ?: return null
        return when (value) {
            is String -> value
            else -> value.toString()
        }
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
        val value = requiredRawValue(provider, key)
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
                ?: throw invalidValue(key, "integer", value)
            else -> throw invalidValue(key, "integer", value)
        }
    }

    /**
     * Load an integer value when present, otherwise null.
     *
     * Missing values are nullable; malformed present values fail fast.
     */
    fun loadIntOrNull(provider: ConfigProvider, key: String): Int? {
        val value = rawValueOrNull(provider, key) ?: return null
        return parseInt(key, value)
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
     * Load a required boolean value.
     *
     * @param provider ConfigProvider to load from
     * @param key Configuration key in dot notation
     * @return Boolean value
     * @throws ConfigException if key is missing or not a valid boolean
     */
    fun loadRequiredBoolean(provider: ConfigProvider, key: String): Boolean {
        val value = requiredRawValue(provider, key)
        return parseBooleanValue(key, value)
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
        val value = requiredRawValue(provider, key)
        return parseLong(key, value)
    }

    /**
     * Load a long value when present, otherwise null.
     *
     * Missing values are nullable; malformed present values fail fast.
     */
    fun loadLongOrNull(provider: ConfigProvider, key: String): Long? {
        val value = rawValueOrNull(provider, key) ?: return null
        return parseLong(key, value)
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
        val value = rawValueOrNull(provider, key) ?: return default
        return parseBooleanValue(key, value)
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

    private fun ensurePresent(provider: ConfigProvider, key: String) {
        if (!provider.hasKey(key)) {
            throw ConfigException("Required configuration key '$key' is missing")
        }
    }

    private fun requiredRawValue(provider: ConfigProvider, key: String): Any {
        ensurePresent(provider, key)
        return provider.get<Any>(key)
            ?: throw ConfigException("Required configuration key '$key' is missing")
    }

    private fun rawValueOrNull(provider: ConfigProvider, key: String): Any? {
        if (!provider.hasKey(key)) {
            return null
        }
        return provider.get<Any>(key)
    }

    private fun parseInt(key: String, value: Any): Int {
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
                ?: throw invalidValue(key, "integer", value)
            else -> throw invalidValue(key, "integer", value)
        }
    }

    private fun parseLong(key: String, value: Any): Long {
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
                ?: throw invalidValue(key, "long", value)
            else -> throw invalidValue(key, "long", value)
        }
    }

    private fun parseBooleanValue(key: String, value: Any): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> parseBoolean(key, value)
            is Number -> value.toInt() != 0
            else -> throw invalidValue(key, "boolean", value)
        }
    }

    private fun parseBoolean(key: String, value: String): Boolean {
        return when (value.trim().lowercase()) {
            "true", "yes", "on", "1", "enabled" -> true
            "false", "no", "off", "0", "disabled" -> false
            else -> throw invalidValue(key, "boolean", value)
        }
    }

    private fun invalidValue(key: String, expectedType: String, value: Any): ConfigException {
        return ConfigException(
            "Configuration key '$key' must be a valid $expectedType, got: $value"
        )
    }
}
