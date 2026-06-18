package io.github.darkryh.katalyst.config.yaml

import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.di.KatalystApplicationBuilder
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import org.slf4j.LoggerFactory

/**
 * Katalyst bean module for ConfigProvider.
 *
 * This module is loaded by YamlConfigurationFeature during Phase 1 of DI initialization.
 * Ensures ConfigProvider is available in the active bean registry BEFORE component discovery
 * (Phase 3), allowing all services to properly inject ConfigProvider via constructor injection.
 *
 * **How It Works:**
 * 1. YamlConfigurationFeature is registered via enableYamlConfiguration() in Application.kt
 * 2. Katalyst's DI system calls YamlConfigurationFeature.provideBeanModules() during Phase 1
 * 3. Returns this bean module for the active DI adapter to load
 * 4. YamlConfigurationSource is instantiated and registered as ConfigProvider
 * 5. Services in Phase 3 can now inject ConfigProvider via constructor
 *
 * **Benefits:**
 * - ConfigProvider available before component discovery
 * - Services use proper constructor injection
 * - Follows established Katalyst patterns
 * - Modularized and reusable across projects
 */
private fun yamlConfigurationModule(source: ConfigProvider): KatalystBeanModule = katalystBeanModule {
    single<ConfigProvider> { source }
}

/**
 * Feature for ConfigProvider in Katalyst DI system.
 *
 * Implements Katalyst's KatalystFeature interface to provide bean modules during Phase 1
 * of DI initialization. Must be explicitly registered via enableYamlConfiguration() in
 * Application.kt (builder pattern - not automatic classpath scanning).
 *
 * **Lifecycle:**
 * - Phase 1: provideBeanModules() called → returns configProviderModule
 * - Phase 1: Module loaded by the active DI adapter → YamlConfigurationSource created + registered
 * - Phase 2: onReady() called → ConfigProvider verified in registry
 * - Phase 3+: All services can inject ConfigProvider
 *
 * **Registration Pattern (Builder Pattern):**
 * Features in Katalyst are explicitly registered, not auto-discovered. Each feature
 * provides a builder extension function that calls feature() internally:
 * ```
 * fun KatalystApplicationBuilder.enableYamlConfiguration(): KatalystApplicationBuilder {
 *     return feature(YamlConfigurationFeature())
 * }
 * ```
 * Optional feature toggles use the same registration system through `features { ... }`.
 */
class YamlConfigurationFeature(
    private val source: ConfigProvider = YamlConfigurationSource()
) : KatalystFeature {
    companion object {
        private val log = LoggerFactory.getLogger(YamlConfigurationFeature::class.java)
    }

    /**
     * Unique identifier for this feature (used for logging/debugging).
     */
    override val id: String = "yaml-configuration"

    /**
     * Provide bean modules for ConfigProvider.
     *
     * Called by Katalyst's Feature system during Phase 1 (DIConfiguration).
     * Returns modules to be loaded by the active DI adapter immediately.
     *
     * @return List of bean modules (in this case, just configProviderModule)
     */
    override fun provideBeanModules(): List<KatalystBeanModule> {
        log.info("YamlConfigurationFeature: Providing YAML configuration source module for DI")
        return listOf(yamlConfigurationModule(source))
    }

    /**
     * Lifecycle hook called after all bean modules are loaded.
     *
     * Used for:
     * - Verifying ConfigProvider is successfully registered
     * - Logging module initialization details
     * - Failing fast if configuration is invalid
     *
     * Called by Katalyst's Feature system after Phase 1 module loading completes.
     *
     * @param context Access to the active Katalyst bean container
     */
    override fun onReady(context: KatalystBeanContext) {
        log.debug("YamlConfigurationFeature: Verifying ConfigProvider is ready")
        try {
            // Get ConfigProvider from the Katalyst bean context to verify it was loaded
            val configProvider = context.get<ConfigProvider>()

            val keyCount = configProvider.getAllKeys().size
            log.info("✓ ConfigProvider successfully registered in the active DI container")
            log.debug("  Type: ${configProvider::class.simpleName}")
            log.debug("  Keys loaded: $keyCount")
        } catch (e: Exception) {
            log.error("✗ Failed to verify ConfigProvider in the active DI container: ${e.message}", e)
            throw e
        }
    }
}

/**
 * Builder extension function to enable YAML configuration feature.
 *
 * Follows the Katalyst feature registration pattern (builder pattern, not auto-discovery).
 * Call this function in main() to register YamlConfigurationFeature during Phase 1.
 *
 * **Usage in Application.kt:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     enableYamlConfiguration()
 *     database { fromConfiguration() }
 *     scanPackages("io.github.darkryh.katalyst.example")
 *
 *     features {
 *         enableEvents()
 *         enableScheduler()
 *         enableWebSockets()
 *     }
 * }
 * ```
 *
 * **What Happens:**
 * 1. This function calls `feature(YamlConfigurationFeature())`
 * 2. Feature is registered with KatalystApplicationBuilder
 * 3. During Phase 1, YamlConfigurationFeature.provideBeanModules() is called
 * 4. configProviderModule is loaded by the active DI adapter
 * 5. YamlConfigurationSource is created and registered as ConfigProvider singleton
 * 6. Phase 2: onReady() hook verifies ConfigProvider in the active DI container
 * 7. Phase 3+: Services can inject ConfigProvider via constructor
 *
 * @return The builder instance for method chaining
 */
fun KatalystApplicationBuilder.enableYamlConfiguration(
    source: ConfigProvider = YamlConfigurationSource()
): KatalystApplicationBuilder =
    configuration(source).feature(YamlConfigurationFeature(source))
