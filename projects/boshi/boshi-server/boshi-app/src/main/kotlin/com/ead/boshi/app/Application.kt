package com.ead.boshi.app

import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations
import io.github.darkryh.katalyst.scheduler.enableScheduler

fun main(args: Array<String>) = katalystApplication(args) {
    // Step 1: Select engine (REQUIRED)
    engine(NettyServer)
    beanEngine(KoinBeanEngine)

    // Step 2: Enable YAML configuration once.
    enableYamlConfiguration()

    // Step 3: Configure database from YAML with Hikari defaults.
    database {
        fromConfiguration()
    }

    // Step 4: Scan packages for components
    scanPackages("com.ead.boshi")
    schema { createMissing() }

    // Step 5: Enable optional features
    features {
        // Loads all ktor.deployment.* properties from YAML
        enableServerConfiguration()
        enableMigrations()
        enableScheduler()
    }
}
