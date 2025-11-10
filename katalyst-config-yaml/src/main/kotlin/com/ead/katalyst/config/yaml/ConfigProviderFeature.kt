package com.ead.katalyst.config.yaml

import com.ead.katalyst.core.config.ConfigProvider
import com.ead.katalyst.di.feature.KatalystFeature
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Koin DI module for ConfigProvider.
 *
 * This module is automatically discovered by Katalyst's Feature system and loaded during
 * Phase 1 of DI initialization (DIConfiguration). This ensures ConfigProvider is available
 * in the Koin registry BEFORE component discovery (Phase 3), allowing all services to
 * properly inject ConfigProvider via constructor injection.
 *
 * **How It Works:**
 * 1. Katalyst's Feature system scans classpath during Phase 1
 * 2. Discovers ConfigProviderFeature (see below)
 * 3. Calls ConfigProviderFeature.provideModules()
 * 4. Returns this module for Koin to load
 * 5. YamlConfigProvider is instantiated and registered as ConfigProvider
 * 6. Services in Phase 3 can now inject ConfigProvider via constructor
 *
 * **Benefits:**
 * - ConfigProvider available before component discovery
 * - Services use proper constructor injection
 * - Follows established Katalyst patterns
 * - Automatic discovery - no manual wiring needed
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
 * Feature for automatic discovery of ConfigProviderDIModule by Katalyst.
 *
 * Implements Katalyst's KatalystFeature interface to enable auto-discovery during Phase 1
 * of DI initialization. The Feature system scans the classpath for all KatalystFeature
 * implementations and calls their methods in the following order:
 *
 * 1. provideModules() - Returns list of Koin modules to load
 * 2. onKoinReady() - Called after all modules are loaded for verification
 *
 * **Lifecycle:**
 * - Phase 1: Discovered by Feature system
 * - Phase 1: provideModules() called → configProviderModule returned
 * - Phase 1: Module loaded into Koin → YamlConfigProvider created
 * - Phase 1: onKoinReady() called → ConfigProvider verified in registry
 * - Phase 3+: All services can inject ConfigProvider
 *
 * **Why KatalystFeature Interface:**
 * The KatalystFeature interface tells Katalyst's DIConfiguration to auto-discover this class.
 * Without it, ConfigProvider module wouldn't be loaded automatically.
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
