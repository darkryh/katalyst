package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider

/**
 * Nullable-first configuration read API.
 *
 * Two families of extensions are provided on [ConfigProvider]:
 *
 *  - `requiredX` — fail fast with a [ConfigException] naming the key when it is missing
 *    (or blank, for strings) or malformed.
 *  - `xOrNull` — return `null` when the key is ABSENT, but still throw a [ConfigException]
 *    when the key is PRESENT but malformed. Optional reads never silently fall back to a
 *    default for malformed values; callers combine them with `?:` to supply defaults.
 *
 * Raw values are read through the low-level [ConfigProvider] members [ConfigProvider.hasKey]
 * and [ConfigProvider.get].
 */

/**
 * Read a required string and fail fast when the key is missing or blank.
 */
fun ConfigProvider.requiredString(key: String): String {
    if (!hasKey(key)) {
        throw ConfigException("Required configuration key '$key' is missing")
    }
    val value = get<Any>(key)?.toString()
    if (value.isNullOrBlank()) {
        throw ConfigException("Required configuration key '$key' is missing or blank")
    }
    return value
}

/**
 * Read a string when present, returning null when the key is missing.
 */
fun ConfigProvider.stringOrNull(key: String): String? {
    val value = rawValueOrNull(key) ?: return null
    return value.toString()
}

/**
 * Read a required integer and fail fast when the key is missing or malformed.
 */
fun ConfigProvider.requiredInt(key: String): Int = parseInt(key, requiredRawValue(key))

/**
 * Read an integer when present, returning null when the key is missing and
 * failing fast when the present value is malformed.
 */
fun ConfigProvider.intOrNull(key: String): Int? {
    val value = rawValueOrNull(key) ?: return null
    return parseInt(key, value)
}

/**
 * Read a required long and fail fast when the key is missing or malformed.
 */
fun ConfigProvider.requiredLong(key: String): Long = parseLong(key, requiredRawValue(key))

/**
 * Read a long when present, returning null when the key is missing and
 * failing fast when the present value is malformed.
 */
fun ConfigProvider.longOrNull(key: String): Long? {
    val value = rawValueOrNull(key) ?: return null
    return parseLong(key, value)
}

/**
 * Read a required boolean and fail fast when the key is missing or malformed.
 */
fun ConfigProvider.requiredBoolean(key: String): Boolean = parseBooleanValue(key, requiredRawValue(key))

/**
 * Read a boolean when present, returning null when the key is missing and
 * failing fast when the present value is malformed.
 */
fun ConfigProvider.booleanOrNull(key: String): Boolean? {
    val value = rawValueOrNull(key) ?: return null
    return parseBooleanValue(key, value)
}

// ---------------------------------------------------------------------------------
// Private parsing helpers (shared by the read extensions and ConfigBinder).
// ---------------------------------------------------------------------------------

private fun ConfigProvider.requiredRawValue(key: String): Any {
    if (!hasKey(key)) {
        throw ConfigException("Required configuration key '$key' is missing")
    }
    return get<Any>(key)
        ?: throw ConfigException("Required configuration key '$key' is missing")
}

private fun ConfigProvider.rawValueOrNull(key: String): Any? {
    if (!hasKey(key)) {
        return null
    }
    return get<Any>(key)
}

internal fun parseInt(key: String, value: Any): Int {
    return when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
            ?: throw invalidValue(key, "integer", value)
        else -> throw invalidValue(key, "integer", value)
    }
}

internal fun parseLong(key: String, value: Any): Long {
    return when (value) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
            ?: throw invalidValue(key, "long", value)
        else -> throw invalidValue(key, "long", value)
    }
}

internal fun parseBooleanValue(key: String, value: Any): Boolean {
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
