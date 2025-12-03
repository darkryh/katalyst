package com.ead.boshi.auth.config

import com.ead.boshi.auth.config.models.AuthConfig
import io.github.darkryh.katalyst.config.provider.ConfigBootstrapHelper
import io.github.darkryh.katalyst.config.yaml.YamlConfigProvider

object AuthConfigImpl {
    fun loadConfig() : AuthConfig {
        val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)

        return ConfigBootstrapHelper
            .loadServiceConfig(
                config = config,
                loader = AuthConfigLoader
            )
    }
}