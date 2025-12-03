package io.github.darkryh.katalyst.config.yaml.spi

import io.github.darkryh.katalyst.config.spi.ConfigFormat
import io.github.darkryh.katalyst.config.spi.ProfileAwareConfigLoader
import io.ktor.server.config.*
import io.ktor.server.config.yaml.*

class YamlApplicationConfigLoader : ProfileAwareConfigLoader {
    override fun supports(format: ConfigFormat): Boolean = format.id.equals("yaml", ignoreCase = true)

    override fun load(paths: List<String>): ApplicationConfig {
        require(paths.isNotEmpty()) { "No config paths provided" }
        val configs: List<ApplicationConfig> = paths.map { path ->
            requireNotNull(YamlConfig(path)) { "Could not load YAML config at $path" }
        }
        if (configs.size == 1) return configs.first()

        // Last wins merge using flattened maps
        val merged = mutableMapOf<String, String>()
        configs.forEach { cfg ->
            flatten(cfg).forEach { (k, v) -> merged[k] = v }
        }
        val pairs: Array<Pair<String, String>> = merged.map { it.key to it.value }.toTypedArray()
        return MapApplicationConfig(*pairs)
    }

    override fun resolveProfiledPaths(baseName: String): List<String> {
        val profile = System.getProperty("katalyst.profile")
            ?.ifBlank { null }
            ?: System.getenv("KATALYST_PROFILE")
                ?.ifBlank { null }

        return if (profile.isNullOrBlank()) {
            listOf("$baseName.yaml")
        } else {
            listOf("$baseName.yaml", "$baseName-$profile.yaml")
        }
    }

    private fun flatten(config: ApplicationConfig): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()

        fun recurse(prefix: String, value: Any) {
            when (value) {
                is Map<*, *> -> value.forEach { (k, v) ->
                    if (k == null || v == null) return@forEach
                    val key = if (prefix.isEmpty()) "$k" else "$prefix.$k"
                    recurse(key, v)
                }
                is Iterable<*> -> value.filterNotNull().forEach { element ->
                    pairs += prefix to element.toString()
                }
                else -> pairs += prefix to value.toString()
            }
        }

        recurse("", config.toMap())
        return pairs
    }
}
