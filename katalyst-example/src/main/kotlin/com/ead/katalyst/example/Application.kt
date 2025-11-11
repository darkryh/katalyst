package com.ead.katalyst.example

import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.client.feature.enableEvents
import com.ead.katalyst.config.yaml.enableConfigProvider
import com.ead.katalyst.example.infra.config.DbConfigImpl
import com.ead.katalyst.migrations.extensions.enableMigrations
import com.ead.katalyst.scheduler.enableScheduler
import com.ead.katalyst.websockets.enableWebSockets
import io.ktor.server.application.Application


/**
 * Application entry point.
 *
 * Configuration flow:
 * 1. database() - Load database config
 * 2. scanPackages() - Discover services
 * 3. enableConfigProvider() - Enable ConfigProvider DI feature
 * 4. Enable other features (events, scheduler, websockets, migrations)
 *
 * YAML files: application.yaml, application-dev.yaml, application-prod.yaml
 * Environment: Set KATALYST_PROFILE=dev|prod for profiles
 */
fun main(args: Array<String>) = katalystApplication(args) {
    database(DbConfigImpl.loadDatabaseConfig())
    scanPackages("com.ead.katalyst.example")

    enableConfigProvider()
    enableEvents {
        withBus(true)
    }
    enableMigrations()
    enableScheduler()
    enableWebSockets()
}

fun Application.module() {
    /* Unused - Katalyst handles Ktor configuration automatically */
}