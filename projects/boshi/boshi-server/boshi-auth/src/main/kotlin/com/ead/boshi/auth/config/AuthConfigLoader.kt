package com.ead.boshi.auth.config

import com.ead.boshi.auth.config.models.AuthConfig
import io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.requiredString
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.reflect.KClass

/**
 * Load API key authentication configuration from YAML
 * Reads from authentication.api-key property
 */
object AuthConfigLoader : AutomaticServiceConfigLoader<AuthConfig> {
    override val configType: KClass<AuthConfig> = AuthConfig::class

    override fun loadConfig(provider: ConfigProvider): AuthConfig {
        return AuthConfig(apiKey = provider.requiredString("authentication.api-key"))
    }

    override fun validate(config: AuthConfig) {
        require(config.apiKey.isNotBlank()) { "API key is required and cannot be blank" }
    }
}
