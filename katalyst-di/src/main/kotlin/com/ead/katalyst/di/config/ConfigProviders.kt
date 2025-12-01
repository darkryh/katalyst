package com.ead.katalyst.di.config

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.core.config.ConfigProvider
import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory

/**
 * Adapter that exposes Ktor's [ApplicationConfig] as a Katalyst [ConfigProvider].
 *
 * Used to read ktor.deployment.* values that were supplied via CLI flags or external
 * config files passed to the Ktor engine.
 */
class ApplicationConfigProvider(
    private val config: ApplicationConfig
) : ConfigProvider, Component {
    override fun <T> get(key: String, defaultValue: T?): T? {
        @Suppress("UNCHECKED_CAST")
        return config.propertyOrNull(key)?.getString() as T? ?: defaultValue
    }

    override fun getString(key: String, default: String): String {
        return config.propertyOrNull(key)?.getString() ?: default
    }

    override fun getInt(key: String, default: Int): Int {
        return config.propertyOrNull(key)?.getString()?.toIntOrNull() ?: default
    }

    override fun getLong(key: String, default: Long): Long {
        return config.propertyOrNull(key)?.getString()?.toLongOrNull() ?: default
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        val value = config.propertyOrNull(key)?.getString() ?: return default
        return when (value.lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> default
        }
    }

    override fun getList(key: String, default: List<String>): List<String> {
        return runCatching { config.property(key).getList() }
            .getOrElse { default }
    }

    override fun hasKey(key: String): Boolean = config.propertyOrNull(key) != null

    override fun getAllKeys(): Set<String> = emptySet()
}

/**
 * Combines multiple [ConfigProvider]s, using the first provider that contains a key.
 *
 * Providers are evaluated in order, so place higher-priority sources first
 * (e.g., CLI config provider before YAML provider).
 */
class CompositeConfigProvider(
    providers: List<ConfigProvider>
) : ConfigProvider, Component {
    private val logger = LoggerFactory.getLogger(CompositeConfigProvider::class.java)
    private val orderedProviders = providers.filterNotNull()

    override fun <T> get(key: String, defaultValue: T?): T? {
        orderedProviders.firstOrNull { it.hasKey(key) }?.let { provider ->
            return provider.get(key, defaultValue)
        }
        return defaultValue
    }

    override fun getString(key: String, default: String): String {
        orderedProviders.firstOrNull { it.hasKey(key) }?.let { provider ->
            return provider.getString(key, default)
        }
        return default
    }

    override fun getInt(key: String, default: Int): Int {
        orderedProviders.firstOrNull { it.hasKey(key) }?.let { provider ->
            return provider.getInt(key, default)
        }
        return default
    }

    override fun getLong(key: String, default: Long): Long {
        orderedProviders.firstOrNull { it.hasKey(key) }?.let { provider ->
            return provider.getLong(key, default)
        }
        return default
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        orderedProviders.firstOrNull { it.hasKey(key) }?.let { provider ->
            return provider.getBoolean(key, default)
        }
        return default
    }

    override fun getList(key: String, default: List<String>): List<String> {
        orderedProviders.firstOrNull { it.hasKey(key) }?.let { provider ->
            return provider.getList(key, default)
        }
        return default
    }

    override fun hasKey(key: String): Boolean {
        val found = orderedProviders.any { runCatching { it.hasKey(key) }.getOrDefault(false) }
        if (!found && orderedProviders.isEmpty()) {
            logger.debug("CompositeConfigProvider has no providers configured; returning false for {}", key)
        }
        return found
    }

    override fun getAllKeys(): Set<String> = orderedProviders.flatMap { it.getAllKeys() }.toSet()
}
