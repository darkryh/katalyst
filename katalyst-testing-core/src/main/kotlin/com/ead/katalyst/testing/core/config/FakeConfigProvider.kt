package com.ead.katalyst.testing.core.config

import com.ead.katalyst.core.config.ConfigProvider

/**
 * Lightweight ConfigProvider backed by an in-memory map.
 *
 * Useful for unit tests that need deterministic configuration without loading
 * real YAML/properties providers.
 */
class FakeConfigProvider(
    private val entries: Map<String, Any?> = emptyMap()
) : ConfigProvider {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String, defaultValue: T?): T? =
        entries[key] as? T ?: defaultValue

    override fun getString(key: String, default: String): String =
        when (val value = entries[key]) {
            null -> default
            is String -> value
            else -> value.toString()
        }

    override fun getInt(key: String, default: Int): Int =
        when (val value = entries[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    override fun getLong(key: String, default: Long): Long =
        when (val value = entries[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: default
            else -> default
        }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        when (val value = entries[key]) {
            is Boolean -> value
            is String -> value.equals("true", true) ||
                value.equals("yes", true) ||
                value.equals("on", true) ||
                value == "1"
            else -> default
        }

    override fun getList(key: String, default: List<String>): List<String> =
        when (val value = entries[key]) {
            is List<*> -> value.filterIsInstance<String>()
            is String -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> default
        }

    override fun hasKey(key: String): Boolean = entries.containsKey(key)

    override fun getAllKeys(): Set<String> = entries.keys
}
