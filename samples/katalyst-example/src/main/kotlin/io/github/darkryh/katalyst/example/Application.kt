package io.github.darkryh.katalyst.example

import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.feature.enableEvents
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations
import io.github.darkryh.katalyst.scheduler.enableScheduler
import io.github.darkryh.katalyst.websockets.enableWebSockets


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
 * 2. enableYamlConfiguration() - Install the YAML source once
 * 3. database { fromConfiguration() } - Load database.* from the installed YAML source
 * 4. scanPackages() - Discover services
 * 5. features { ... } - Enable optional server/config/runtime features
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
    engine(NettyServer)
    beanEngine(KoinBeanEngine)

    // Step 2: Enable YAML configuration once.
    enableYamlConfiguration()

    // Step 3: Configure database from YAML with Hikari defaults.
    database {
        fromConfiguration()
    }

    // Step 4: Scan packages for components
    scanPackages("io.github.darkryh.katalyst.example")
    schema { createMissing() }

    // Step 5: Enable optional features
    features {
        // Loads all ktor.deployment.* properties from YAML
        enableServerConfiguration()
        enableEvents()
        enableMigrations()
        enableScheduler()
        enableWebSockets()
    }
}
