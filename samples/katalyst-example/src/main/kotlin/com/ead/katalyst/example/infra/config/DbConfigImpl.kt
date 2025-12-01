package com.ead.katalyst.example.infra.config

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.config.provider.ConfigBootstrapHelper
import com.ead.katalyst.config.provider.ConfigProviderFactory
import org.slf4j.LoggerFactory

/**
 * Configuration helpers for Application.kt bootstrap.
 */
object DbConfigImpl {
    private val log = LoggerFactory.getLogger(DbConfigImpl::class.java)

    /**
     * Load database configuration before DI initialization.
     */
    fun loadDatabaseConfig(): DatabaseConfig {
        log.info("Loading database configuration...")
        try {
            val provider = ConfigProviderFactory.create()
            val dbConfig = ConfigBootstrapHelper.loadServiceConfig(provider, DatabaseConfigLoader)
            log.info("✓ Database configuration loaded")
            return dbConfig
        } catch (e: Exception) {
            log.error("✗ Failed to load database configuration: ${e.message}")
            throw e
        }
    }
}
