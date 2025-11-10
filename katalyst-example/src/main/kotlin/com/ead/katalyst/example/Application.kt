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
 * Application entry point with modularized ConfigProvider DI injection.
 *
 * **Refactored Configuration Flow:**
 * 1. Phase 1: ConfigProviderDIModule is auto-discovered
 *    └─ ConfigProvider registered in Koin as singleton
 * 2. Phase 2: Ktor plugins configured
 * 3. Phase 3: Scan and auto-discover all components
 *    └─ Services can now inject ConfigProvider via constructor
 * 4. Phase 4+: Database initialization, initializers, server startup
 *
 * **Key Changes:**
 * - Removed: ConfigurationImplementation.loadDatabaseConfig()
 *   ConfigProvider is now managed by DI system automatically
 * - Removed: Manual bootstrap configuration calls
 *   Feature system handles ConfigProvider registration in Phase 1
 * - Services: Now use constructor injection for ConfigProvider
 *   JwtSettingsService(config: ConfigProvider) : Service
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
 *
 * **How It Works:**
 * 1. katalystApplication() starts DI initialization
 * 2. Phase 1: Feature system discovers ConfigProviderFeature
 *    ├─ Calls: ConfigProviderFeature.provideModules()
 *    ├─ Returns: configProviderModule
 *    ├─ Koin: Loads module and creates YamlConfigProvider
 *    └─ Result: ConfigProvider available in Koin ✓
 * 3. Phase 3: scanPackages() triggers component discovery
 *    ├─ Finds: JwtSettingsService(config: ConfigProvider)
 *    ├─ Resolves: ConfigProvider (found in Phase 1)
 *    └─ Creates: JwtSettingsService(configProvider)
 * 4. All services initialized with ConfigProvider injected ✓
 */
fun main(args: Array<String>) = katalystApplication(args) {
    // ConfigProviderDIModule is automatically loaded during Phase 1
    // ConfigProvider is now available in Koin for all services
    // No manual configuration loading needed!

    // Step 1: Optional - Validate all ServiceConfigLoader implementations
    // This ensures all configuration is valid before services start
    ConfigurationImplementation.validateAllConfigLoaders()

    // Step 2: Discover and auto-inject all components, services, repositories, validators
    // JwtSettingsService and other services now have ConfigProvider injected
    // This scan happens during Phase 3 (after ConfigProvider is in Phase 1)
    scanPackages("com.ead.katalyst.example")

    // Step 3: Enable optional framework features
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