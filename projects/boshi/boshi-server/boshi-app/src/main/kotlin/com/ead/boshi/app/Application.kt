package com.ead.boshi.app

import com.ead.boshi.app.config.DbConfigImpl
import io.github.darkryh.katalyst.ktor.engine.netty.embeddedServer
import io.github.darkryh.katalyst.config.yaml.enableConfigProvider
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations
import io.github.darkryh.katalyst.scheduler.enableScheduler

fun main(args: Array<String>) = katalystApplication(args) {
    // Step 1: Select engine (REQUIRED)
    engine(embeddedServer())

    // Step 2: Configure database
    database(DbConfigImpl.loadDatabaseConfig())

    // Step 3: Scan packages for components
    scanPackages("com.ead.boshi")

    // Step 4: Enable server configuration loading from application.yaml
    // This loads all ktor.deployment.* properties from YAML
    enableServerConfiguration()

    // Step 5: Enable ConfigProvider for runtime configuration access
    enableConfigProvider()

    // Step 7: Enable optional features
    enableMigrations()
    enableScheduler()
}
