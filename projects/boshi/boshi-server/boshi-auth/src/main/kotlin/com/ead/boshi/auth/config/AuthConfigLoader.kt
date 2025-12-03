package com.ead.boshi.auth.config

import com.ead.boshi.auth.config.models.AuthConfig
import io.github.darkryh.katalyst.config.provider.ServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.ConfigLoaders
import io.github.darkryh.katalyst.core.config.ConfigProvider

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