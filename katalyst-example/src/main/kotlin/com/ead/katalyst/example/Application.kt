package com.ead.katalyst.example

import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.client.feature.enableEvents
import com.ead.katalyst.config.yaml.enableConfigProvider
import com.ead.katalyst.example.config.ConfigurationImplementation
import com.ead.katalyst.migrations.extensions.enableMigrations
import com.ead.katalyst.scheduler.enableScheduler
import com.ead.katalyst.websockets.enableWebSockets
import io.ktor.server.application.Application
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

/**
 * Application entry point.
 *
 * Configuration flow:
 * 1. database() - Load database config
 * 2. validateAllConfigLoaders() - Validate configuration
 * 3. scanPackages() - Discover services
 * 4. enableConfigProvider() - Enable ConfigProvider DI feature
 * 5. Enable other features (events, scheduler, websockets, migrations)
 *
 * YAML files: application.yaml, application-dev.yaml, application-prod.yaml
 * Environment: Set KATALYST_PROFILE=dev|prod for profiles
 */
fun main(args: Array<String>) = katalystApplication(args) {
    database(ConfigurationImplementation.loadDatabaseConfig())
    ConfigurationImplementation.validateAllConfigLoaders()
    scanPackages("com.ead.katalyst.example")

    enableConfigProvider()
    enableEvents { withBus(true) }
    enableMigrations()
    enableScheduler()
    enableWebSockets()
}

fun Application.module() {
    /* Unused - Katalyst handles Ktor configuration automatically */
}