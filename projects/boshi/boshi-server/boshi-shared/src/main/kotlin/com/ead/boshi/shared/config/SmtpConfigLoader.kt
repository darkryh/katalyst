package com.ead.boshi.shared.config

import com.ead.boshi.shared.config.models.SmtpConfig
import io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.boolean
import io.github.darkryh.katalyst.config.provider.intOrNull
import io.github.darkryh.katalyst.config.provider.requiredString
import io.github.darkryh.katalyst.config.provider.stringOrNull
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

/**
 * Automatically loads SMTP configuration from YAML during DI bootstrap.
 *
 * This loader is auto-discovered and auto-registered during component registration,
 * enabling services to inject SmtpConfig directly through constructor injection.
 *
 * Configuration properties read from:
 * - smtp.host (required): SMTP server hostname or IP
 * - smtp.port (optional, default: 25): SMTP server port
 * - smtp.localHostname (optional, default: boshi.local): Local hostname for EHLO
 * - smtp.username (optional): Authentication username
 * - smtp.password (optional): Authentication password
 * - smtp.useTls (optional, default: false): Enable TLS
 * - smtp.connectionTimeoutSeconds (optional, default: 30): Connection timeout
 * - smtp.readTimeoutSeconds (optional, default: 30): Read timeout
 *
 * **Usage:**
 * ```kotlin
 * class SmtpClient(
 *     val smtpConfig: SmtpConfig  // Auto-injected by DI!
 * ) : Component {
 *     fun sendEmail(host: String, ...) {
 *         val port = smtpConfig.port
 *         // Use smtpConfig directly
 *     }
 * }
 * ```
 */
@Suppress("unused")
object SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> {
    override val configType: KClass<SmtpConfig> = SmtpConfig::class

    override fun loadConfig(provider: ConfigProvider): SmtpConfig {
        return SmtpConfig(
            host = provider.requiredString("smtp.host"),
            port = provider.intOrNull("smtp.port") ?: 25,
            localHostname = provider.stringOrNull("smtp.localHostname") ?: "boshi.local",
            username = provider.stringOrNull("smtp.username").orEmpty(),
            password = provider.stringOrNull("smtp.password").orEmpty(),
            useTls = provider.boolean("smtp.useTls"),
            connectionTimeoutSeconds = provider.intOrNull("smtp.connectionTimeoutSeconds") ?: 30,
            readTimeoutSeconds = provider.intOrNull("smtp.readTimeoutSeconds") ?: 30
        )
    }

    override fun validate(config: SmtpConfig) {
        require(config.host.isNotBlank()) { "SMTP host is required and cannot be blank" }
        require(config.port > 0) { "SMTP port must be > 0" }
        require(config.port <= 65535) { "SMTP port must be <= 65535" }
        require(config.connectionTimeoutSeconds > 0) { "Connection timeout must be > 0" }
        require(config.readTimeoutSeconds > 0) { "Read timeout must be > 0" }
    }
}
