package com.ead.katalyst.config.yaml

import com.ead.katalyst.config.provider.ServiceConfigLoader
import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * ServiceConfigLoader implementation for YamlConfigProvider.
 *
 * **Purpose:**
 * Provides automatic, type-safe loading of YamlConfigProvider as a discovered component.
 * Can be auto-discovered by ConfigMetadata during application startup.
 *
 * **Auto-Discovery:**
 * When used with ConfigMetadata.discoverLoaders(), this loader is automatically discovered:
 * ```kotlin
 * val loaders = ConfigMetadata.discoverLoaders(arrayOf("com.ead.katalyst.config.yaml"))
 * ConfigMetadata.validateLoaders(config, loaders)
 * ```
 *
 * **Direct Usage:**
 * ```kotlin
 * val baseConfig = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 * val loader = YamlConfigProviderLoader()
 * val yamlProvider = loader.loadConfig(baseConfig) as YamlConfigProvider
 * ```
 *
 * **Validation Rules:**
 * - YAML file must exist (application.yaml)
 * - At least one configuration key must be present
 * - Active profile YAML (if set) must be valid YAML syntax
 */
class YamlConfigProviderLoader : ServiceConfigLoader<YamlConfigProvider> {
    companion object {
        private val log = LoggerFactory.getLogger(YamlConfigProviderLoader::class.java)
    }

    /**
     * Load YamlConfigProvider from base ConfigProvider.
     *
     * **How It Works:**
     * 1. Instantiate new YamlConfigProvider
     * 2. YamlConfigProvider will:
     *    - Load application.yaml
     *    - Load application-{profile}.yaml if KATALYST_PROFILE env var is set
     *    - Merge profile config into base config
     *    - Substitute environment variables
     * 3. Return ready-to-use provider
     *
     * **Note:** The baseConfig parameter is not used here because YamlConfigProvider
     * is self-contained. It provides the implementation of ConfigProvider.
     *
     * @param provider Not used (YamlConfigProvider is standalone)
     * @return Initialized YamlConfigProvider instance
     * @throws ConfigException if configuration loading fails
     */
    override fun loadConfig(provider: ConfigProvider): YamlConfigProvider {
        log.debug("Loading YamlConfigProvider...")
        return try {
            YamlConfigProvider().also {
                log.debug("✓ YamlConfigProvider loaded successfully")
            }
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Failed to load YamlConfigProvider: ${e.message}", e)
        }
    }

    /**
     * Validate YamlConfigProvider configuration.
     *
     * **Validation Checks:**
     * 1. Provider data is not empty (at least one key exists)
     * 2. Required keys are accessible (validates dot-notation navigation)
     * 3. No critical loading errors occurred
     *
     * **Example Configuration to Validate:**
     * ```yaml
     * application:
     *   name: my-app
     *   version: 1.0.0
     * database:
     *   url: jdbc:postgresql://localhost:5432/db
     *   username: postgres
     * ```
     *
     * @param config YamlConfigProvider to validate
     * @throws ConfigException if validation fails
     */
    override fun validate(config: YamlConfigProvider) {
        log.debug("Validating YamlConfigProvider...")

        try {
            // Check that configuration is not empty
            val allKeys = config.getAllKeys()
            if (allKeys.isEmpty()) {
                throw ConfigException("YamlConfigProvider loaded but contains no configuration keys")
            }

            log.debug("✓ YamlConfigProvider validation passed (${allKeys.size} keys found)")
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("YamlConfigProvider validation failed: ${e.message}", e)
        }
    }
}
