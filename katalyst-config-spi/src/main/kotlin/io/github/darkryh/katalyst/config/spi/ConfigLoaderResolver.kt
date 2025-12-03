package io.github.darkryh.katalyst.config.spi

import io.ktor.server.config.ApplicationConfig
import java.util.ServiceLoader

/**
 * Resolves a ConfigLoader based on format or file extension, then loads configs.
 */
object ConfigLoaderResolver {
    private val loaders: List<ConfigLoader> by lazy {
        ServiceLoader.load(ConfigLoader::class.java).toList()
    }

    fun load(
        paths: List<String>,
        formatHint: ConfigFormat? = null
    ): ApplicationConfig {
        val format = formatHint ?: paths.firstNotNullOfOrNull { path ->
            path.substringAfterLast('.', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
                ?.let { ConfigFormat.fromExtension(it) }
        } ?: ConfigFormat.YAML // default to YAML for backward compatibility

        val loader = loaders.firstOrNull { it.supports(format) }
            ?: error("No ConfigLoader found for format '${format.id}'. Ensure the corresponding module is on the classpath.")

        return loader.load(paths)
    }

    fun resolveProfiledPaths(
        baseName: String = "application",
        formatHint: ConfigFormat? = null
    ): List<String> {
        val format = formatHint ?: ConfigFormat.YAML
        val profileAware = loaders.firstOrNull { it is ProfileAwareConfigLoader && it.supports(format) } as? ProfileAwareConfigLoader
        return profileAware?.resolveProfiledPaths(baseName) ?: listOf("$baseName.${format.id}")
    }
}
