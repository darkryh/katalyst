package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.di.analysis.ComponentOrderComputer
import io.github.darkryh.katalyst.di.discovery.DiscoverySnapshot
import io.github.darkryh.katalyst.di.discovery.DiscoverySnapshotBuilder
import io.github.darkryh.katalyst.di.exception.FatalDependencyValidationException
import io.github.darkryh.katalyst.di.exception.KatalystDIException
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.planning.BindingPlan
import io.github.darkryh.katalyst.di.planning.BindingPlanBuilder
import io.github.darkryh.katalyst.di.validation.DependencyValidator
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.GlobalEventHandlerRegistry
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.repositories.CrudRepository
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
 * @param container The active Katalyst container
 * @param beanEngine The installed bean engine that owns native registration
 * @param scanPackages Packages to scan for components
 */
class ComponentRegistrationOrchestrator(
    private val container: KatalystContainer,
    private val beanEngine: KatalystBeanEngine,
    private val scanPackages: Array<String>
) {

    /**
     * Cache for discovered config types to avoid redundant classpath scanning.
     * Discovery is expensive (reflection + bytecode scanning), so we cache results.
     */
    private var cachedConfigTypes: Set<KClass<*>>? = null

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

        logger.info("✓ LIFECYCLE_COMPONENT_DISCOVERY complete: found {} components",
            discovered.allTypes.size)

        // Phase 2: Analyze dependencies
        val plan = buildBindingPlan(discovered)
        val graph = plan.graph

        logger.info("✓ LIFECYCLE_DEPENDENCY_ANALYSIS complete: graph has {} nodes, {} edges",
            graph.nodeCount(), graph.edgeCount())

        // Phase 3: Validate dependencies
        val validationReport = validateDependencies(graph)

        if (!validationReport.isValid) {
            logger.error("✗ LIFECYCLE_DEPENDENCY_VALIDATION failed: {} validation errors found",
                validationReport.totalErrorCount)

            val exception = FatalDependencyValidationException(
                validationErrors = validationReport.errors,
                discoveredTypes = discovered.asValidationMap()
            )
            throw exception
        }

        logger.info("✓ LIFECYCLE_DEPENDENCY_VALIDATION passed ({}ms)",
            validationReport.validationDurationMs)

        // Phase 4: Compute instantiation order
        val orderComputer = ComponentOrderComputer(graph)
        val order = try {
            orderComputer.computeOrder()
        } catch (e: IllegalStateException) {
            logger.error("✗ LIFECYCLE_INSTANTIATION_ORDER_COMPUTATION failed: {}", e.message)
            throw e
        }

        logger.info("✓ LIFECYCLE_INSTANTIATION_ORDER_COMPUTATION complete: order for {} components",
            order.size)

        // Debug: Show what's in the order
        logger.info("Component registration order:")
        order.forEachIndexed { index, type ->
            logger.info("  [{}] {}", index + 1, type.simpleName)
        }

        // Phase 5-6: Register components in order (integrated)
        registerComponentsInOrder(order, plan.discovery)

        logger.info("✓ LIFECYCLE_COMPONENT_REGISTRATION complete: all components registered and verified")
        logger.info("═".repeat(80))
        logger.info("✓ COMPONENT REGISTRATION COMPLETE")
        logger.info("═".repeat(80))
    }

    /**
     * Phase 1: Discovers all framework components using the existing registrar logic.
     *
     * @return Map of component type to set of discovered classes
     */
    private fun discoverAllComponents(): DiscoverySnapshot {
        logger.info("LIFECYCLE_COMPONENT_DISCOVERY starting")

        val builder = DiscoverySnapshotBuilder()
        val registrar = AutoBindingRegistrar(container, beanEngine, scanPackages)

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
            builder.category(label, types.map { it.kotlin }.toSet())
            logger.debug("Discovered {} {}", types.size, label)
        }

        return builder.build()
    }

    /**
     * Phase 2: Analyzes component dependencies and builds a dependency graph.
     *
     * @param discovered Map of discovered component types
     * @return Dependency graph with all nodes and edges
     */
    private fun buildBindingPlan(discovered: DiscoverySnapshot): BindingPlan {
        logger.info("LIFECYCLE_DEPENDENCY_ANALYSIS starting")

        // Pre-discover automatic config types so they can be included in dependency analysis
        // This ensures the validator knows these types will be available
        val automaticConfigTypes = discoverAutomaticConfigTypes()

        val plan = BindingPlanBuilder(container, scanPackages)
            .build(discovered, automaticConfigTypes)

        logger.debug("Graph nodes: {}, edges: {}, secondary bindings: {}",
            plan.graph.nodeCount(), plan.graph.edgeCount(), plan.graph.secondaryTypeBindings.size)

        return plan
    }

    /**
     * Phase 3: Validates dependencies for errors.
     *
     * @param graph The dependency graph to validate
     * @return Validation report with all errors found
     */
    private fun validateDependencies(
        graph: io.github.darkryh.katalyst.di.analysis.DependencyGraph
    ): io.github.darkryh.katalyst.di.error.ValidationReport {
        logger.info("LIFECYCLE_DEPENDENCY_VALIDATION starting")

        val validator = DependencyValidator(graph)

        val report = validator.validateAll()

        if (!report.isValid) {
            logger.debug("Validation found {} errors:", report.totalErrorCount)
            report.errors.forEachIndexed { index, error ->
                logger.debug("[{}] {} - {}", index + 1, error::class.simpleName, error.message)
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
        discovered: DiscoverySnapshot
    ) {
        logger.info("LIFECYCLE_CONFIG_AUTO_REGISTRATION starting")

        // Load and register automatic configurations BEFORE registering components
        // This ensures components can have their config dependencies injected
        try {
            registerAutomaticConfigurations()
        } catch (e: Exception) {
            logger.error("Failed to load automatic configurations: {}", e.message)
            logger.debug("Full error during automatic config loading", e)
            throw e  // Fail-fast for configuration errors
        }

        logger.info("LIFECYCLE_COMPONENT_INSTANTIATION starting")

        val registrar = AutoBindingRegistrar(container, beanEngine, scanPackages)
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
        val migrations = discovered.types("migrations").toList()
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

        logger.info("LIFECYCLE_DATABASE_TABLE_REGISTRATION starting")

        // Register Exposed table implementations for schema creation
        // Tables must be registered before route discovery so they're available during Phase 4
        try {
            registerDatabaseTables(registrar)
        } catch (e: Exception) {
            logger.warn("Could not register database tables: {}", e.message)
            // Don't fail - tables are optional
        }

        logger.info("LIFECYCLE_POST_REGISTRATION_VERIFICATION starting")

        // If we got here without exceptions, all components were successfully registered
        // The registerInstance method would have thrown an exception if registration failed
        logger.info("✓ All {} components registered successfully (including {} migrations)",
            allRegisteredComponents.size, migrations.size)

        // IMPORTANT: Register route functions discovered from packages
        // These are auto-discovered functions like authRoutes(), userRoutes(), etc.
        // They MUST be registered after regular components so services are available for injection
        logger.info("LIFECYCLE_ROUTE_DISCOVERY_REGISTRATION starting")

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
     * Phase 6: Discovers, binds, and registers annotated/code configuration types.
     *
     * This phase:
     * 1. Discovers all bindable configuration types (via [io.github.darkryh.katalyst.config.provider.ConfigBinder])
     *    — classes annotated with `@ConfigPrefix` and `ConfigBinding` implementors.
     * 2. Binds each configuration from the active ConfigProvider (data-class `init {}` validation runs).
     * 3. Registers each bound instance as a singleton in the active container.
     *
     * This phase runs after ConfigProvider is available (from DI modules) but before
     * component instantiation, so components can receive configurations through constructor injection.
     *
     * Failure to bind a configuration is fatal - the application will not start.
     * This ensures configuration errors are caught immediately at startup.
     *
     * @throws Exception if configuration binding fails
     */
    private fun registerAutomaticConfigurations() {
        logger.info("Discovering configuration types...")

        try {
            // Discover config types FIRST to know if we need ConfigProvider
            val configTypes = discoverConfigTypesReflectively()

            if (configTypes.isEmpty()) {
                logger.debug("No configuration types discovered")
                return
            }

            logger.info("Discovered {} configuration type(s)", configTypes.size)

            // Now check if ConfigProvider is available - FAIL FAST if not
            val configProvider = container.getOrNull<ConfigProvider>()
            if (configProvider == null) {
                throw KatalystDIException(
                    buildString {
                        appendLine("ConfigProvider is required but not available!")
                        appendLine()
                        appendLine("Found ${configTypes.size} configuration type(s) requiring ConfigProvider:")
                        configTypes.forEach { configType ->
                            appendLine("  - ${configType.simpleName}")
                        }
                        appendLine()
                        appendLine("Add enableYamlConfiguration() to your katalystApplication block:")
                        appendLine()
                        appendLine("  katalystApplication(args) {")
                        appendLine("      enableYamlConfiguration()  // Required for automatic config loading")
                        appendLine("      scanPackages(\"your.app.package\")")
                        appendLine("      // ... other configuration")
                        appendLine("  }")
                    }
                )
            }

            // Bind every configuration (fail-fast inside ConfigBinder.bindAll). Reuse the types
            // discovered above instead of re-scanning the classpath inside ConfigBinder.
            val configs = bindAllReflectively(configProvider, configTypes)

            // Register each bound instance by its type.
            val registrar = AutoBindingRegistrar(container, beanEngine, scanPackages)
            configs.forEach { (configType, instance) ->
                try {
                    registrar.registerInstance(instance, configType, emptyList())
                    logger.info("✓ Registered {} configuration", configType.simpleName)
                } catch (e: Exception) {
                    logger.error("Failed to register {} in container: {}", configType.simpleName, e.message)
                    throw e
                }
            }

            logger.info("✓ All {} configuration(s) bound and registered", configs.size)
        } catch (e: KatalystDIException) {
            // Re-throw DI exceptions as-is (includes FatalDependencyValidationException)
            throw e
        } catch (e: Exception) {
            logger.error("Error during configuration binding: {}", e.message)
            logger.debug("Full error during config binding", e)
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
        discovered: DiscoverySnapshot
    ) {
        // Determine which base class this component implements
        val baseClass = when (componentType) {
            in discovered.types("services") -> Service::class
            in discovered.types("components") -> Component::class
            in discovered.types("repositories") -> CrudRepository::class
            in discovered.types("event handlers") -> EventHandler::class
            in discovered.types("ktor modules") -> KtorModule::class
            in discovered.types("migrations") -> KatalystMigration::class
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
            registrar.registerInstance(instance, componentType, secondaryTypes)

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
        discovered: DiscoverySnapshot,
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
     * Pre-discover configuration types before validation.
     *
     * This scans for all bindable configuration types (via ConfigBinder). These types are
     * then marked as "will be available" in the dependency graph during validation,
     * preventing "missing dependency" errors for configs that will be registered during
     * Phase 6a.
     *
     * @return Set of KClass types that will be registered as configurations
     */
    private fun discoverAutomaticConfigTypes(): Set<KClass<*>> {
        if (scanPackages.isEmpty()) {
            return emptySet()
        }

        return try {
            val configTypes = discoverConfigTypesReflectively()
            if (configTypes.isNotEmpty()) {
                logger.debug("Pre-discovered {} configuration types", configTypes.size)
            }
            configTypes
        } catch (e: Exception) {
            logger.warn("Error pre-discovering configuration types: {}", e.message)
            logger.debug("Full error during config type pre-discovery", e)
            emptySet()
        }
    }

    /**
     * Helper: Discovers configuration types via reflection.
     *
     * Uses reflection to call `ConfigBinder.discoverConfigTypes()` because ConfigBinder is in a
     * different module (katalyst-config-provider) which may not be on the classpath.
     *
     * Results are cached to avoid redundant classpath scanning (discovery is called in both
     * Phase 2 for pre-discovery and Phase 5a for actual binding).
     *
     * @return Set of config types, or empty set if config-provider is absent / discovery fails
     */
    private fun discoverConfigTypesReflectively(): Set<KClass<*>> {
        // Return cached result if available
        cachedConfigTypes?.let {
            logger.debug("Using cached config types ({} types)", it.size)
            return it
        }

        val types = try {
            val binderClass = Class.forName(
                "io.github.darkryh.katalyst.config.provider.ConfigBinder"
            )

            val instanceField = binderClass.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            val binderInstance = instanceField.get(null)

            val discoverMethod = binderClass.getDeclaredMethod(
                "discoverConfigTypes",
                Array<String>::class.java
            ).apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            discoverMethod.invoke(binderInstance, scanPackages) as Set<KClass<*>>
        } catch (_: ClassNotFoundException) {
            logger.debug("ConfigBinder not found - automatic config binding disabled")
            emptySet()
        } catch (e: Exception) {
            logger.debug("Error discovering configuration types: {}", e.message)
            emptySet()
        }

        // Cache the result
        cachedConfigTypes = types
        return types
    }

    /**
     * Helper: Binds all configuration types via reflection.
     *
     * Uses reflection to call `ConfigBinder.bindAll()` because ConfigBinder is in a different
     * module (katalyst-config-provider) which may not be on the classpath.
     *
     * Takes the already-discovered [types] (see [discoverConfigTypesReflectively]) and calls the
     * `bindAll(Set<KClass<*>>, ConfigProvider)` overload so ConfigBinder reuses them instead of
     * re-running its own classpath/Reflections scan - discovery happens exactly once per startup.
     *
     * @return Map of config type to bound instance
     */
    private fun bindAllReflectively(provider: ConfigProvider, types: Set<KClass<*>>): Map<KClass<*>, Any> {
        val binderClass = Class.forName(
            "io.github.darkryh.katalyst.config.provider.ConfigBinder"
        )

        val instanceField = binderClass.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        val binderInstance = instanceField.get(null)

        val bindAllMethod = binderClass.getDeclaredMethod(
            "bindAll",
            Set::class.java,
            ConfigProvider::class.java
        ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        return bindAllMethod.invoke(binderInstance, types, provider) as Map<KClass<*>, Any>
    }
}
