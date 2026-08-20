package io.github.darkryh.katalyst.config.spi

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig

/**
 * Test-only [ConfigLoader] registered via `META-INF/services` so [ConfigLoaderResolver]'s
 * ServiceLoader discovery can be exercised. Supports only YAML and records the paths it was asked
 * to load so tests can assert the resolver routed to it.
 */
class FakeYamlConfigLoader : ProfileAwareConfigLoader {

    override fun supports(format: ConfigFormat): Boolean = format == ConfigFormat.YAML

    override fun load(paths: List<String>): ApplicationConfig {
        lastLoadedPaths = paths
        return MapApplicationConfig("loaded.by" to "fake-yaml", "loaded.count" to paths.size.toString())
    }

    override fun resolveProfiledPaths(baseName: String): List<String> =
        listOf("$baseName.yaml", "$baseName-test.yaml")

    companion object {
        @Volatile
        var lastLoadedPaths: List<String> = emptyList()
    }
}
