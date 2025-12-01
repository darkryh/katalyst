package com.ead.katalyst.config.provider

import com.ead.katalyst.core.config.ConfigProvider

/**
 * Factory for creating a ConfigProvider without hard-coding the implementation.
 *
 * Attempts to load providers in preferred order (currently YAML if present),
 * and fails fast with a clear message if none are available on the classpath.
 */
object ConfigProviderFactory {
    private val providerClassNames = listOf(
        "com.ead.katalyst.config.yaml.YamlConfigProvider"
    )

    private val serviceLoader: List<ConfigProvider> by lazy {
        runCatching {
            java.util.ServiceLoader.load(ConfigProvider::class.java).toList()
        }.getOrElse { emptyList() }
    }

    /**
     * @return an instantiated ConfigProvider if available on the classpath
     * @throws IllegalStateException if no known provider can be loaded
     */
    fun create(): ConfigProvider {
        if (serviceLoader.isNotEmpty()) {
            return serviceLoader.first()
        }

        providerClassNames.forEach { className ->
            val provider = runCatching {
                val clazz = Class.forName(className).asSubclass(ConfigProvider::class.java)
                clazz.getDeclaredConstructor().newInstance()
            }.getOrNull()

            if (provider != null) {
                return provider
            }
        }

        throw IllegalStateException(
            "No ConfigProvider implementation found. Add a config provider module (e.g., katalyst-config-yaml) to the classpath."
        )
    }
}
