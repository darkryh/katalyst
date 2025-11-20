package com.ead.boshi.auth.config

import com.ead.boshi.auth.config.models.AuthConfig
import com.ead.katalyst.config.provider.ConfigBootstrapHelper
import com.ead.katalyst.config.yaml.YamlConfigProvider

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