package com.ead.katalyst.example

import com.ead.katalyst.client.feature.enableEvents
import com.ead.katalyst.com.ead.katalyst.ktor.engine.netty.embeddedServer
import com.ead.katalyst.config.yaml.enableConfigProvider
import com.ead.katalyst.di.feature.enableServerConfiguration
import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.example.infra.config.DbConfigImpl
import com.ead.katalyst.migrations.extensions.enableMigrations
import com.ead.katalyst.scheduler.enableScheduler
import com.ead.katalyst.websockets.enableWebSockets


/**
 * Application entry point - Option 3: Custom Configuration (Programmatic)
 *
 * This approach:
 * 1. Loads ktor.deployment configuration from application.yaml
 * 2. Allows programmatic access to customize values if needed
 * 3. Works with any project - dependencies on YAML provider are optional
 *
 * Configuration flow:
 * 1. engine() - Select Ktor engine (REQUIRED)
 * 2. database() - Load database config
 * 3. scanPackages() - Discover services
 * 4. enableServerConfiguration() - Load ktor.deployment from YAML (OPTIONAL)
 * 5. enableConfigProvider() - Enable ConfigProvider DI feature
 * 6. Enable other features (events, scheduler, websockets, migrations)
 *
 * YAML Configuration:
 * - application.yaml: Base configuration with all ktor.deployment properties
 * - application-dev.yaml: Development profile (localhost, lower thread counts)
 * - application-prod.yaml: Production profile (environment variables, higher thread counts)
 *
 * Profile Selection:
 * Set KATALYST_PROFILE environment variable: dev, prod, or leave blank for default
 *
 * Example:
 * ```bash
 * # Development
 * export KATALYST_PROFILE=dev
 * java -jar app.jar
 *
 * # Production
 * export KATALYST_PROFILE=prod
 * java -jar app.jar
 * ```
 */
fun main(args: Array<String>) = katalystApplication(args) {
    // Step 1: Select engine (REQUIRED)
    engine(embeddedServer())
    // Step 2: Configure database
    database(DbConfigImpl.loadDatabaseConfig())
    // Step 3: Scan packages for components
    scanPackages("com.ead.katalyst.example")

    // Step 4: Enable server configuration loading from application.yaml
    // This loads all ktor.deployment.* properties from YAML
    enableServerConfiguration()

    // Step 5: Enable ConfigProvider for runtime configuration access
    enableConfigProvider()

    // Step 6: Enable optional features
    enableEvents {
        withBus(true)
    }
    enableMigrations()
    enableScheduler()
    enableWebSockets()
}
