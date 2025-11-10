package com.ead.katalyst.example

import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.client.feature.enableEvents
import com.ead.katalyst.example.config.ConfigBootstrap
import com.ead.katalyst.migrations.extensions.enableMigrations
import com.ead.katalyst.scheduler.enableScheduler
import com.ead.katalyst.websockets.enableWebSockets
import io.ktor.server.application.Application
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

fun main(args: Array<String>) = katalystApplication(args) {
    // Load configuration from YAML (application.yaml + profile overrides)
    database(ConfigBootstrap.loadDatabaseConfig())

    // Discover and auto-inject all components, services, repositories, validators
    scanPackages("com.ead.katalyst.example")

    // Enable optional features
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