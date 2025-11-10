package com.ead.katalyst.config.yaml

import com.ead.katalyst.core.config.ConfigProvider
import com.ead.katalyst.di.KatalystApplicationBuilder
import com.ead.katalyst.di.feature.KatalystFeature
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Koin DI module for ConfigProvider.
 *
 * This module is loaded by ConfigProviderFeature during Phase 1 of DI initialization.
 * Ensures ConfigProvider is available in the Koin registry BEFORE component discovery
 * (Phase 3), allowing all services to properly inject ConfigProvider via constructor injection.
 *
 * **How It Works:**
 * 1. ConfigProviderFeature is registered via enableConfigProvider() in Application.kt
 * 2. Katalyst's DI system calls ConfigProviderFeature.provideModules() during Phase 1
 * 3. Returns this module for Koin to load
 * 4. YamlConfigProvider is instantiated and registered as ConfigProvider
 * 5. Services in Phase 3 can now inject ConfigProvider via constructor
 *
 * **Benefits:**
 * - ConfigProvider available before component discovery
 * - Services use proper constructor injection
 * - Follows established Katalyst patterns
 * - Modularized and reusable across projects
 */
val configProviderModule: Module = module {
    // Register YamlConfigProvider as a singleton
    // When Koin looks for ConfigProvider, it provides YamlConfigProvider
    single<ConfigProvider> {
        YamlConfigProvider()
    }
}

/**
 * Feature for ConfigProvider in Katalyst DI system.
 *
 * Implements Katalyst's KatalystFeature interface to provide Koin modules during Phase 1
 * of DI initialization. Must be explicitly registered via enableConfigProvider() in
 * Application.kt (builder pattern - not automatic classpath scanning).
 *
 * **Lifecycle:**
 * - Phase 1: provideModules() called → returns configProviderModule
 * - Phase 1: Module loaded into Koin → YamlConfigProvider created + registered
 * - Phase 2: onKoinReady() called → ConfigProvider verified in registry
 * - Phase 3+: All services can inject ConfigProvider
 *
 * **Registration Pattern (Builder Pattern):**
 * Features in Katalyst are explicitly registered, not auto-discovered. Each feature
 * provides a builder extension function that calls feature() internally:
 * ```
 * fun KatalystApplicationBuilder.enableConfigProvider(): KatalystApplicationBuilder {
 *     return feature(ConfigProviderFeature())
 * }
 * ```
 * This is the same pattern used by enableEvents(), enableScheduler(), enableWebSockets().
 */
class ConfigProviderFeature : KatalystFeature {
    companion object {
        private val log = LoggerFactory.getLogger(ConfigProviderFeature::class.java)
    }

    /**
     * Unique identifier for this feature (used for logging/debugging).
     */
    override val id: String = "config-provider"

    /**
     * Provide Koin modules for ConfigProvider.
     *
     * Called by Katalyst's Feature system during Phase 1 (DIConfiguration).
     * Returns the modules to be loaded into Koin immediately.
     *
     * @return List of Koin modules (in this case, just configProviderModule)
     */
    override fun provideModules(): List<Module> {
        log.info("ConfigProviderFeature: Providing ConfigProvider module for DI")
        return listOf(configProviderModule)
    }

    /**
     * Lifecycle hook called after all Koin modules are loaded.
     *
     * Used for:
     * - Verifying ConfigProvider is successfully registered
     * - Logging module initialization details
     * - Failing fast if configuration is invalid
     *
     * Called by Katalyst's Feature system after Phase 1 module loading completes.
     *
     * @param koin The initialized Koin instance
     */
    override fun onKoinReady(koin: Koin) {
        log.debug("ConfigProviderFeature: Verifying ConfigProvider is ready")
        try {
            // Get ConfigProvider from Koin to verify it was loaded
            val configProvider = koin.get<ConfigProvider>()

            val keyCount = configProvider.getAllKeys().size
            log.info("✓ ConfigProvider successfully registered in Koin")
            log.debug("  Type: ${configProvider::class.simpleName}")
            log.debug("  Keys loaded: $keyCount")
        } catch (e: Exception) {
            log.error("✗ Failed to verify ConfigProvider in Koin: ${e.message}", e)
            throw e
        }
    }
}

/**
 * Builder extension function to enable ConfigProvider feature.
 *
 * Follows the Katalyst feature registration pattern (builder pattern, not auto-discovery).
 * Call this function in main() to register ConfigProviderFeature during Phase 1.
 *
 * **Usage in Application.kt:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(ConfigurationImplementation.loadDatabaseConfig())
 *     scanPackages("com.ead.katalyst.example")
 *
 *     enableConfigProvider()  // Register ConfigProvider feature
 *     enableEvents { withBus(true) }
 *     enableScheduler()
 *     enableWebSockets()
 * }
 * ```
 *
 * **What Happens:**
 * 1. This function calls `feature(ConfigProviderFeature())`
 * 2. Feature is registered with KatalystApplicationBuilder
 * 3. During Phase 1, ConfigProviderFeature.provideModules() is called
 * 4. configProviderModule is loaded into Koin
 * 5. YamlConfigProvider is created and registered as ConfigProvider singleton
 * 6. Phase 2: onKoinReady() hook verifies ConfigProvider in Koin
 * 7. Phase 3+: Services can inject ConfigProvider via constructor
 *
 * @return The builder instance for method chaining
 */
fun KatalystApplicationBuilder.enableConfigProvider(): KatalystApplicationBuilder =
    feature(ConfigProviderFeature())
