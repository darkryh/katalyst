package com.ead.boshi.app.config

import io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.boolean
import io.github.darkryh.katalyst.config.provider.intOrNull
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

/**
 * Rate limiting configuration for API endpoints
 */
data class RateLimitConfig(
    val enabled: Boolean = true,
    val requestsPerSecond: Int = 10,          // Max requests per second
    val refillPeriodSeconds: Int = 1,          // Token refill period
    val initialTokens: Int = 10,               // Initial burst allowance
    val emailSendLimit: Int = 100,            // Max emails per minute
    val statusCheckLimit: Int = 100           // Max status checks per minute
) {
    val tokensPerSecond: Double = requestsPerSecond / refillPeriodSeconds.toDouble()
}

/**
 * Load rate limit configuration from YAML
 */
object RateLimitConfigLoader : AutomaticServiceConfigLoader<RateLimitConfig> {
    override val configType: KClass<RateLimitConfig> = RateLimitConfig::class

    override fun loadConfig(provider: ConfigProvider): RateLimitConfig {
        return RateLimitConfig(
            enabled = provider.boolean("rateLimit.enabled", default = true),
            requestsPerSecond = provider.intOrNull("rateLimit.requestsPerSecond") ?: 10,
            refillPeriodSeconds = provider.intOrNull("rateLimit.refillPeriodSeconds") ?: 1,
            initialTokens = provider.intOrNull("rateLimit.initialTokens") ?: 10,
            emailSendLimit = provider.intOrNull("rateLimit.emailSendLimit") ?: 100,
            statusCheckLimit = provider.intOrNull("rateLimit.statusCheckLimit") ?: 100
        )
    }

    override fun validate(config: RateLimitConfig) {
        require(config.requestsPerSecond > 0) { "requestsPerSecond must be > 0" }
        require(config.refillPeriodSeconds > 0) { "refillPeriodSeconds must be > 0" }
        require(config.initialTokens > 0) { "initialTokens must be > 0" }
        require(config.emailSendLimit > 0) { "emailSendLimit must be > 0" }
        require(config.statusCheckLimit > 0) { "statusCheckLimit must be > 0" }
    }
}
