package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.config.provider.ServiceConfigLoader
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * ServiceConfigLoader for the YAML-backed configuration source.
 */
class YamlConfigurationSourceLoader : ServiceConfigLoader<YamlConfigurationSource> {
    private val log = LoggerFactory.getLogger(YamlConfigurationSourceLoader::class.java)

    override fun loadConfig(provider: ConfigProvider): YamlConfigurationSource {
        log.debug("Loading YAML configuration source")
        return try {
            YamlConfigurationSource().also {
                log.debug("YAML configuration source loaded")
            }
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Failed to load YAML configuration source: ${e.message}", e)
        }
    }

    override fun validate(config: YamlConfigurationSource) {
        try {
            val allKeys = config.getAllKeys()
            if (allKeys.isEmpty()) {
                throw ConfigException("YAML configuration source loaded but contains no configuration keys")
            }
            log.debug("YAML configuration source validation passed ({} keys found)", allKeys.size)
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("YAML configuration source validation failed: ${e.message}", e)
        }
    }
}
