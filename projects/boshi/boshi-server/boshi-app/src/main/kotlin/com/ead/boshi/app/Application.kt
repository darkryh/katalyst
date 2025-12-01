package com.ead.boshi.app

import com.ead.boshi.app.config.DbConfigImpl
import com.ead.katalyst.com.ead.katalyst.ktor.engine.netty.embeddedServer
import com.ead.katalyst.config.yaml.enableConfigProvider
import com.ead.katalyst.di.feature.enableServerConfiguration
import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.migrations.extensions.enableMigrations
import com.ead.katalyst.scheduler.enableScheduler

fun main(args: Array<String>) = katalystApplication(args) {
    // Step 1: Select engine (REQUIRED)
    engine(args.embeddedServer())

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
