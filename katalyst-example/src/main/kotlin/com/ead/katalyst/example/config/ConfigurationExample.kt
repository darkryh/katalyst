package com.ead.katalyst.example.config

import com.ead.katalyst.config.provider.ConfigBootstrapHelper
import com.ead.katalyst.config.provider.ConfigMetadata
import com.ead.katalyst.config.yaml.YamlConfigProvider
import com.ead.katalyst.example.config.loaders.DatabaseConfigLoader
import org.slf4j.LoggerFactory

/**
 * Configuration helpers for Application.kt bootstrap.
 */
object ConfigurationImplementation {
    private val log = LoggerFactory.getLogger(ConfigurationImplementation::class.java)

    /**
     * Load database configuration before DI initialization.
     */
    fun loadDatabaseConfig(): com.ead.katalyst.config.DatabaseConfig {
        log.info("Loading database configuration...")
        try {
            val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
            val dbConfig = ConfigBootstrapHelper.loadServiceConfig(config, DatabaseConfigLoader())
            log.info("✓ Database configuration loaded")
            return dbConfig
        } catch (e: Exception) {
            log.error("✗ Failed to load database configuration: ${e.message}")
            throw e
        }
    }

    /**
     * Validate all ServiceConfigLoader implementations.
     */
    fun validateAllConfigLoaders() {
        log.info("Validating all configuration loaders...")
        try {
            val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
            val loaders = ConfigMetadata.discoverLoaders(arrayOf(
                "com.ead.katalyst.config.yaml",
                "com.ead.katalyst.example.config"
            ))
            ConfigMetadata.validateLoaders(config, loaders)
            log.info("✓ All configuration loaders validated")
        } catch (e: Exception) {
            log.error("✗ Configuration validation failed: ${e.message}")
            throw e
        }
    }
}
