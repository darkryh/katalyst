package com.ead.katalyst.di.internal

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.core.config.ConfigProvider
import com.ead.katalyst.di.analysis.ComponentOrderComputer
import com.ead.katalyst.di.analysis.DependencyAnalyzer
import com.ead.katalyst.di.exception.FatalDependencyValidationException
import com.ead.katalyst.di.validation.DependencyValidator
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.bus.GlobalEventHandlerRegistry
import com.ead.katalyst.ktor.KtorModule
import com.ead.katalyst.migrations.KatalystMigration
import com.ead.katalyst.repositories.CrudRepository
import org.koin.core.Koin
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger("ComponentRegistrationOrchestrator")

/**
 * Orchestrates the enhanced component discovery and registration process.
 *
 * This class coordinates all phases of the new DI system:
 * 1. Component discovery (existing logic from AutoBindingRegistrar)
 * 2. Dependency graph analysis (NEW)
 * 3. Dependency validation (NEW)
 * 4. Safe instantiation order computation (NEW)
 * 5. Instantiation in deterministic order (modified)
 * 6. Post-registration validation (NEW)
 *
 * @param koin The Koin DI container
 * @param scanPackages Packages to scan for components
 */
class ComponentRegistrationOrchestrator(
    private val koin: Koin,
    private val scanPackages: Array<String>
) {

    /**
     * Executes the complete enhanced component registration process.
     *
     * Runs all phases in sequence, failing fast if validation errors are detected.
     *
     * @throws FatalDependencyValidationException if validation fails
     */
    fun registerAllWithValidation() {
        logger.info("Starting enhanced component registration process")

        // Phase 1: Discover components (using existing AutoBindingRegistrar)
        val discovered = discoverAllComponents()

        logger.info("✓ Phase 1 (Discovery): Found {} components",
            discovered.values.sumOf { it.size })

        // Phase 2: Analyze dependencies
        val graph = analyzeComponentDependencies(discovered)

        logger.info("✓ Phase 2 (Analysis): Built dependency graph with {} nodes, {} edges",
            graph.nodeCount(), graph.edgeCount())

        // Phase 3: Validate dependencies
        val validationReport = validateDependencies(graph)

        if (!validationReport.isValid) {
            logger.error("✗ Phase 3 (Validation): {} validation errors found",
                validationReport.totalErrorCount)

            val exception = FatalDependencyValidationException(
                validationErrors = validationReport.errors,
                discoveredTypes = discovered,
                koin = koin
            )
            exception.printDetailedReport()
            throw exception
        }

        logger.info("✓ Phase 3 (Validation): Passed ({}ms)",
            validationReport.validationDurationMs)

        // Phase 4: Compute instantiation order
        val orderComputer = ComponentOrderComputer(graph)
        val order = try {
            orderComputer.computeOrder()
        } catch (e: IllegalStateException) {
            logger.error("✗ Phase 4 (Order Computation): Failed - {}", e.message)
            throw e
        }

        logger.info("✓ Phase 4 (Order Computation): Computed order for {} components",
            order.size)

        // Debug: Show what's in the order
        logger.info("Component registration order:")
        order.forEachIndexed { index, type ->
            logger.info("  [{}] {}", index + 1, type.simpleName)
        }

        // Phase 5-6: Register components in order (integrated)
        registerComponentsInOrder(order, discovered)

        logger.info("✓ Phase 5-6 (Registration): All components registered and verified")
        logger.info("═".repeat(80))
        logger.info("✓ COMPONENT REGISTRATION COMPLETE")
        logger.info("═".repeat(80))
    }

    /**
     * Phase 1: Discovers all framework components using the existing registrar logic.
     *
     * @return Map of component type to set of discovered classes
     */
    private fun discoverAllComponents(): Map<String, Set<KClass<*>>> {
        logger.info("Phase 1: Component Discovery")

        val discovered = mutableMapOf<String, Set<KClass<*>>>()
        val registrar = AutoBindingRegistrar(koin, scanPackages)

        // Discover each component type (methods are now internal - no reflection needed)
        listOf(
            "repositories" to CrudRepository::class.java,
            "components" to Component::class.java,
            "services" to Service::class.java,
            "event handlers" to EventHandler::class.java,
            "ktor modules" to KtorModule::class.java,
            "migrations" to KatalystMigration::class.java
        ).forEach { (label, baseClass) ->
            val types = registrar.discoverConcreteTypes(baseClass)
            discovered[label] = types.map { it.kotlin }.toSet()
            logger.debug("Discovered {} {}", types.size, label)
        }

        return discovered
    }

    /**
     * Phase 2: Analyzes component dependencies and builds a dependency graph.
     *
     * @param discovered Map of discovered component types
     * @return Dependency graph with all nodes and edges
     */
    private fun analyzeComponentDependencies(discovered: Map<String, Set<KClass<*>>>): com.ead.katalyst.di.analysis.DependencyGraph {
        logger.info("Phase 2: Dependency Analysis")

        val allTypes = mutableMapOf<String, Set<KClass<*>>>()

        // Merge all types into single map
        discovered.forEach { (key, types) ->
            allTypes[key] = types
        }

        // Pre-discover automatic config types so they can be included in dependency analysis
        // This ensures the validator knows these types will be available
        val automaticConfigTypes = discoverAutomaticConfigTypes()

        val analyzer = DependencyAnalyzer(allTypes, koin, scanPackages, automaticConfigTypes)

        val graph = analyzer.buildGraph()

        logger.debug("Graph nodes: {}, edges: {}, secondary bindings: {}",
            graph.nodeCount(), graph.edgeCount(), graph.secondaryTypeBindings.size)

        return graph
    }

    /**
     * Phase 3: Validates dependencies for errors.
     *
     * @param graph The dependency graph to validate
     * @return Validation report with all errors found
     */
    private fun validateDependencies(
        graph: com.ead.katalyst.di.analysis.DependencyGraph
    ): com.ead.katalyst.di.error.ValidationReport {
        logger.info("Phase 3: Dependency Validation")

        val validator = DependencyValidator(graph)

        val report = validator.validateAll()

        if (!report.isValid) {
            logger.error("Validation found {} errors:", report.totalErrorCount)
            report.errors.forEachIndexed { index, error ->
                logger.error("[{}] {} - {}", index + 1, error::class.simpleName, error.message)
            }
        }

        return report
    }

    /**
     * Phase 5-6: Registers components in the computed order and verifies registration.
     *
     * Note: The order excludes migrations (different lifecycle).
     * Migrations are registered AFTER all non-migration components.
     *
     * @param order The computed instantiation order (excluding migrations)
     * @param discovered The originally discovered components
     */
    private fun registerComponentsInOrder(
        order: List<KClass<*>>,
        discovered: Map<String, Set<KClass<*>>>
    ) {
        logger.info("Phase 5a: Automatic Configuration Loading and Registration")

        // Load and register automatic configurations BEFORE registering components
        // This ensures components can have their config dependencies injected
        try {
            registerAutomaticConfigurations()
        } catch (e: Exception) {
            logger.error("Failed to load automatic configurations: {}", e.message)
            logger.debug("Full error during automatic config loading", e)
            throw e  // Fail-fast for configuration errors
        }

        logger.info("Phase 5b: Instantiation in Safe Order")

        val registrar = AutoBindingRegistrar(koin, scanPackages)
        val allRegisteredComponents = mutableListOf<KClass<*>>()

        // Register non-migration components in order
        logger.info("Registering {} non-migration components in topological order", order.size)
        registerComponentsInLoop(
            components = order,
            registrar = registrar,
            discovered = discovered,
            label = "",
            useDebugLogging = false
        )
        allRegisteredComponents.addAll(order)

        // Register migrations AFTER non-migration components
        // Migrations were excluded from topological sort but still need to be registered
        val migrations = (discovered["migrations"] ?: emptySet()).toList()
        if (migrations.isNotEmpty()) {
            logger.info("Registering {} migrations after service components", migrations.size)
            registerComponentsInLoop(
                components = migrations,
                registrar = registrar,
                discovered = discovered,
                label = "migration",
                useDebugLogging = true
            )
            allRegisteredComponents.addAll(migrations)
        }

        logger.info("Phase 6: Database Table Discovery and Registration")

        // Register Exposed table implementations for schema creation
        // Tables must be registered before route discovery so they're available during Phase 4
        try {
            registerDatabaseTables(registrar)
        } catch (e: Exception) {
            logger.warn("Could not register database tables: {}", e.message)
            // Don't fail - tables are optional
        }

        logger.info("Phase 6b: Post-Registration Verification")

        // If we got here without exceptions, all components were successfully registered
        // The registerInstanceWithKoin method would have thrown an exception if registration failed
        logger.info("✓ All {} components registered successfully (including {} migrations)",
            allRegisteredComponents.size, migrations.size)

        // IMPORTANT: Register route functions discovered from packages
        // These are auto-discovered functions like authRoutes(), userRoutes(), etc.
        // They MUST be registered after regular components so services are available for injection
        logger.info("Phase 7: Route Discovery and Registration")

        // Register route functions discovered from packages
        try {
            registrar.registerRouteFunctions()
            logger.info("✓ Route functions discovered and registered")
        } catch (e: Exception) {
            logger.warn("Could not register route functions: {}", e.message)
            // Don't fail - routes are optional
        }
    }

    /**
     * Phase 6: Discovers, loads, validates, and registers AutomaticServiceConfigLoader implementations.
     *
     * This phase:
     * 1. Discovers all [AutomaticServiceConfigLoader] implementations in scan packages
     * 2. Loads each configuration using the discovered loader
     * 3. Validates each loaded configuration
     * 4. Registers each configuration as a singleton in Koin
     * 5. Logs all registered configurations
     *
     * This phase runs after ConfigProvider is available (from DI modules) but before
     * component instantiation, so components can receive configurations through constructor injection.
     *
     * Failure to load or validate a configuration is fatal - the application will not start.
     * This ensures configuration errors are caught immediately at startup.
     *
     * @throws Exception if configuration loading or validation fails
     */
    private fun registerAutomaticConfigurations() {
        logger.info("Discovering AutomaticServiceConfigLoader implementations...")

        try {
            // Get ConfigProvider from Koin (available from features)
            val configProvider = runCatching { koin.get<ConfigProvider>() }.getOrNull()
            if (configProvider == null) {
                logger.debug("ConfigProvider not available, skipping automatic configuration loading")
                return
            }

            // Use reflection to call AutomaticConfigLoaderDiscovery.discoverLoaders()
            // This avoids circular dependency between katalyst-di and katalyst-config-provider
            try {
                val loaders = discoverAutomaticConfigLoaders()

                if (loaders.isEmpty()) {
                    logger.debug("No AutomaticServiceConfigLoader implementations discovered")
                    return
                }

                logger.info("Discovered {} automatic config loader(s)", loaders.size)

                // Load, validate, and register each configuration
                loaders.forEach { (configType, loader) ->
                    try {
                        logger.info("Loading configuration for {}", configType.simpleName)

                        // Call loader.loadConfig(configProvider)
                        val loadConfigMethod = loader::class.java.getDeclaredMethod(
                            "loadConfig",
                            ConfigProvider::class.java
                        ).apply { isAccessible = true }

                        val config = loadConfigMethod.invoke(loader, configProvider)
                        logger.debug("Loaded configuration: {}", config)

                        // Call loader.validate(config)
                        try {
                            val validateMethod = loader::class.java.getDeclaredMethod(
                                "validate",
                                Any::class.java
                            ).apply { isAccessible = true }
                            validateMethod.invoke(loader, config)
                        } catch (_: NoSuchMethodException) {
                            // validate() is optional (default implementation)
                            logger.debug("No validate() method on {}", loader::class.simpleName)
                        }

                        logger.debug("Configuration validated: {}", configType.simpleName)

                        // Register the configuration in Koin using AutoBindingRegistrar's method
                        try {
                            // Create an AutoBindingRegistrar to use its registration logic
                            val registrar = AutoBindingRegistrar(koin, scanPackages)
                            // Call registerInstanceWithKoin directly (method is now internal)
                            registrar.registerInstanceWithKoin(config, configType, emptyList())

                            logger.info("✓ Registered {} configuration", configType.simpleName)
                        } catch (e: Exception) {
                            logger.error("Failed to register {} in Koin: {}", configType.simpleName, e.message)
                            throw e
                        }
                    } catch (e: Exception) {
                        logger.error("✗ Failed to load {} configuration: {}",
                            configType.simpleName, e.message)
                        logger.error("Exception details: {}", e.toString())
                        throw e  // Fail-fast - config errors are fatal
                    }
                }

                logger.info("✓ All {} automatic configuration(s) loaded and registered", loaders.size)
            } catch (_: ClassNotFoundException) {
                logger.debug("AutomaticConfigLoaderDiscovery class not found, skipping automatic config loading")
                logger.debug("Ensure katalyst-config-provider is on the classpath to enable automatic config loading")
            }
        } catch (e: NoSuchElementException) {
            // ConfigProvider not in Koin
            logger.debug("ConfigProvider not registered in Koin, skipping automatic configuration loading: {}", e.message)
        } catch (e: Exception) {
            logger.error("Error during automatic configuration loading: {}", e.message)
            logger.debug("Full error during config loading", e)
            throw e
        }
    }

    /**
     * Phase 6b: Discovers and registers Exposed table implementations for database schema creation.
     *
     * @param registrar The AutoBindingRegistrar instance
     */
    private fun registerDatabaseTables(registrar: AutoBindingRegistrar) {
        logger.info("Discovering Exposed table implementations...")

        try {
            registrar.registerTables()
            logger.info("✓ Database table discovery and registration completed")
        } catch (e: Exception) {
            logger.error("Error during table discovery and registration: {}", e.message)
            logger.debug("Full error during table discovery", e)
        }
    }

    /**
     * Registers a single component using the existing registrar logic.
     *
     * @param registrar The AutoBindingRegistrar instance
     * @param componentType The type to register
     * @param discovered The discovered types
     */
    private fun registerComponentOfType(
        registrar: AutoBindingRegistrar,
        componentType: KClass<*>,
        discovered: Map<String, Set<KClass<*>>>
    ) {
        // Determine which base class this component implements
        val baseClass = when (componentType) {
            in (discovered["services"] ?: emptySet()) -> Service::class
            in (discovered["components"] ?: emptySet()) -> Component::class
            in (discovered["repositories"] ?: emptySet()) -> CrudRepository::class
            in (discovered["event handlers"] ?: emptySet()) -> EventHandler::class
            in (discovered["ktor modules"] ?: emptySet()) -> KtorModule::class
            in (discovered["migrations"] ?: emptySet()) -> KatalystMigration::class
            else -> {
                logger.warn("Unknown component type: {}", componentType.simpleName)
                return
            }
        }

        // Call the registration methods directly (no reflection needed - methods are internal)
        try {
            val instance = registrar.instantiate(componentType)
            registrar.injectWellKnownProperties(instance)
            val secondaryTypes = registrar.computeSecondaryTypes(componentType, baseClass)
            registrar.registerInstanceWithKoin(instance, componentType, secondaryTypes)

            // Register in feature-specific registries
            when (instance) {
                is Service -> ServiceRegistry.register(instance)
                is EventHandler<*> -> GlobalEventHandlerRegistry.register(instance)
                is KtorModule -> KtorModuleRegistry.register(instance)
            }
        } catch (e: Exception) {
            logger.error("Failed to register component {}: {}", componentType.simpleName, e.message)
            throw e
        }
    }

    /**
     * Helper: Registers components in a loop with consistent error handling and logging.
     *
     * @param components Components to register (in order)
     * @param registrar The AutoBindingRegistrar instance
     * @param discovered Map of discovered component types
     * @param label Label for logging (e.g., "", "migration")
     * @param useDebugLogging Whether to use debug level (true) or info level (false)
     */
    private fun registerComponentsInLoop(
        components: List<KClass<*>>,
        registrar: AutoBindingRegistrar,
        discovered: Map<String, Set<KClass<*>>>,
        label: String = "",
        useDebugLogging: Boolean = false
    ) {
        val logMessage = if (label.isEmpty()) "Registering" else "Registering $label"
        components.forEachIndexed { index, componentType ->
            try {
                val progressMessage = "[${ index + 1}/${ components.size}] $logMessage {}"
                if (useDebugLogging) {
                    logger.debug(progressMessage, componentType.simpleName)
                } else {
                    logger.info(progressMessage, componentType.simpleName)
                }

                registerComponentOfType(registrar, componentType, discovered)

                val successMessage = "✓ $logMessage {}"
                if (useDebugLogging) {
                    logger.debug(successMessage, componentType.simpleName)
                } else {
                    logger.info(successMessage, componentType.simpleName)
                }
            } catch (e: Exception) {
                logger.error("✗ Failed to $logMessage {} - {}", componentType.simpleName, e.message)
                throw e
            }
        }
    }

    /**
     * Pre-discover automatic configuration types before validation.
     *
     * This scans for all AutomaticServiceConfigLoader implementations and extracts their
     * config types. These types are then marked as "will be available" in the dependency
     * graph during validation, preventing "missing dependency" errors for configs that
     * will be registered during Phase 6a.
     *
     * @return Set of KClass types that will be registered as configurations
     */
    private fun discoverAutomaticConfigTypes(): Set<KClass<*>> {
        val configTypes = mutableSetOf<KClass<*>>()

        if (scanPackages.isEmpty()) {
            return configTypes
        }

        try {
            val loaders = discoverAutomaticConfigLoaders()
            // Extract the config types from the discovered loaders
            configTypes.addAll(loaders.keys)

            if (loaders.isNotEmpty()) {
                logger.debug("Pre-discovered {} automatic config types", configTypes.size)
            }
        } catch (e: Exception) {
            logger.warn("Error pre-discovering automatic config types: {}", e.message)
            logger.debug("Full error during config type pre-discovery", e)
        }

        return configTypes
    }

    /**
     * Helper: Discovers AutomaticServiceConfigLoader implementations via reflection.
     *
     * Uses reflection to call AutomaticConfigLoaderDiscovery.discoverLoaders() because
     * AutomaticConfigLoaderDiscovery is in a different module (katalyst-config-provider)
     * which may not be on the classpath.
     *
     * @return Map of config type to loader instance, or empty map if discovery fails
     */
    private fun discoverAutomaticConfigLoaders(): Map<KClass<*>, Any> {
        return try {
            val discoveryClass = Class.forName(
                "com.ead.katalyst.config.provider.AutomaticConfigLoaderDiscovery"
            )

            val instanceField = discoveryClass.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            val discoveryInstance = instanceField.get(null)

            val discoverMethod = discoveryClass.getDeclaredMethod(
                "discoverLoaders",
                Array<String>::class.java
            ).apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            discoverMethod.invoke(discoveryInstance, scanPackages) as Map<KClass<*>, Any>
        } catch (_: ClassNotFoundException) {
            logger.debug("AutomaticConfigLoaderDiscovery not found - automatic config loading disabled")
            emptyMap()
        } catch (e: Exception) {
            logger.debug("Error discovering automatic config loaders: {}", e.message)
            emptyMap()
        }
    }
}