@file:Suppress("unused")

package com.ead.boshi.app.config

import com.ead.katalyst.config.provider.ServiceConfigLoader
import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.core.config.ConfigProvider


data class SmtpServerConfig(
    val serverName: String,
    val maxConnections: Int,
    val connectionTimeoutSeconds: Int,
    val maxRetries: Int,
    val retryDelaySeconds: Int,
    val retentionDays: Int,
    val cleanupBatchSize: Int,
    val cleanupSchedule: String
)

/**
 * Loads SMTP server configuration from YAML files
 */
object SmtpServerConfigLoader : ServiceConfigLoader<SmtpServerConfig> {
    override fun loadConfig(provider: ConfigProvider): SmtpServerConfig {
        val serverName = ConfigLoaders.loadOptionalString(provider, "smtp.server.name","boshi.local")
        val maxConnections = ConfigLoaders.loadOptionalInt(provider, "smtp.server.maxConnections",100)
        val connectionTimeout = ConfigLoaders.loadOptionalInt(provider, "smtp.server.connectionTimeoutSeconds",30)
        val maxRetries = ConfigLoaders.loadOptionalInt(provider, "smtp.delivery.maxRetries",3)
        val retryDelay = ConfigLoaders.loadOptionalInt(provider, "smtp.delivery.retryDelaySeconds",300)
        val retentionDays = ConfigLoaders.loadOptionalInt(provider, "smtp.storage.retentionDays",14)
        val cleanupBatchSize = ConfigLoaders.loadOptionalInt(provider, "smtp.storage.cleanupBatchSize",1000)
        val cleanupSchedule = ConfigLoaders.loadOptionalString(provider, "smtp.storage.cleanupSchedule","0 2 * * * ?")

        return SmtpServerConfig(
            serverName = serverName,
            maxConnections = maxConnections,
            connectionTimeoutSeconds = connectionTimeout,
            maxRetries = maxRetries,
            retryDelaySeconds = retryDelay,
            retentionDays = retentionDays,
            cleanupBatchSize = cleanupBatchSize,
            cleanupSchedule = cleanupSchedule
        )
    }

    override fun validate(config: SmtpServerConfig) {
        require(config.serverName.isNotBlank()) { "SMTP server name is required" }
        require(config.maxConnections > 0) { "maxConnections must be > 0" }
        require(config.connectionTimeoutSeconds > 0) { "connectionTimeoutSeconds must be > 0" }
        require(config.maxRetries >= 0) { "maxRetries must be >= 0" }
        require(config.retryDelaySeconds >= 0) { "retryDelaySeconds must be >= 0" }
        require(config.retentionDays >= 1) { "retentionDays must be >= 1" }
        require(config.cleanupBatchSize > 0) { "cleanupBatchSize must be > 0" }
    }
}