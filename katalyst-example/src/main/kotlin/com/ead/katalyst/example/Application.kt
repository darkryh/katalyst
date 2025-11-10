package com.ead.katalyst.example

import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.client.feature.enableEvents
import com.ead.katalyst.example.config.ConfigurationImplementation
import com.ead.katalyst.migrations.extensions.enableMigrations
import com.ead.katalyst.scheduler.enableScheduler
import com.ead.katalyst.websockets.enableWebSockets
import io.ktor.server.application.Application
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

/**
 * Application entry point demonstrating YAML configuration framework integration.
 *
 * **Configuration Flow:**
 * 1. Load database config from YAML (application.yaml + profile overrides + env vars)
 * 2. Validate all ServiceConfigLoader implementations
 * 3. Scan and auto-discover all components
 * 4. Enable optional framework features
 *
 * **YAML Configuration Files:**
 * - application.yaml (base configuration)
 * - application-dev.yaml (development overrides, set KATALYST_PROFILE=dev)
 * - application-prod.yaml (production overrides, set KATALYST_PROFILE=prod)
 *
 * **Environment Variables:**
 * Configuration supports ${VAR_NAME:defaultValue} substitution:
 * - DB_URL: Database connection URL
 * - DB_USER: Database username
 * - DB_PASS: Database password
 * - JWT_SECRET: JWT signing secret
 */
fun main(args: Array<String>) = katalystApplication(args) {
    // Step 1: Load database configuration from YAML with validation
    // This happens before DI initialization, so services can be configured
    database(ConfigurationImplementation.loadDatabaseConfig())

    // Step 2: Optional - Validate all ServiceConfigLoader implementations
    // This ensures all configuration is valid before services start
    ConfigurationImplementation.validateAllConfigLoaders()

    // Step 3: Discover and auto-inject all components, services, repositories, validators
    // This scan happens AFTER configuration is loaded and validated
    scanPackages("com.ead.katalyst.example")

    // Step 4: Enable optional framework features
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