package com.ead.boshi.auth.config

import com.ead.boshi.auth.config.models.AuthConfig
import com.ead.katalyst.config.provider.ServiceConfigLoader
import com.ead.katalyst.config.provider.ConfigLoaders
import com.ead.katalyst.core.config.ConfigProvider

/**
 * Load API key authentication configuration from YAML
 * Reads from authentication.api-key property
 */
object AuthConfigLoader : ServiceConfigLoader<AuthConfig> {
    override fun loadConfig(provider: ConfigProvider): AuthConfig {
        return AuthConfig(apiKey = ConfigLoaders.loadRequiredString(provider, "authentication.api-key"))
    }

    override fun validate(config: AuthConfig) {
        require(config.apiKey.isNotBlank()) { "API key is required and cannot be blank" }
    }
}