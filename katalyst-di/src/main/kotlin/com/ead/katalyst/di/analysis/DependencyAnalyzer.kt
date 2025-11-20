package com.ead.katalyst.di.analysis

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.core.transaction.DatabaseTransactionManager
import com.ead.katalyst.repositories.CrudRepository
import kotlinx.serialization.Serializable
import org.koin.core.Koin
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

private val logger = LoggerFactory.getLogger("DependencyAnalyzer")

/**
 * Analyzes component dependencies and builds a dependency graph.
 *
 * This analyzer:
 * 1. Extracts constructor dependencies from discovered components
 * 2. Extracts well-known property dependencies (DatabaseTransactionManager, SchedulerService)
 * 3. Computes secondary type bindings (interfaces implemented by components)
 * 4. Checks Koin for available types (from features)
 * 5. Includes additional types that will be available (like automatic configs)
 * 6. Builds a complete dependency graph with resolvability information
 *
 * @param discoveredTypes All discovered component types organized by base type
 * @param koin The Koin DI container (to check what types are available)
 * @param scanPackages Array of packages that were scanned
 * @param additionalAvailableTypes Additional types that will be available (e.g., automatic configs)
 */
class DependencyAnalyzer(
    private val discoveredTypes: Map<String, Set<KClass<*>>>,
    private val koin: Koin,
    private val scanPackages: Array<String>,
    private val additionalAvailableTypes: Set<KClass<*>> = emptySet()
) {

    /**
     * Class type for SchedulerService if available (lazy loaded).
     * This is used for well-known property injection detection.
     */
    private val schedulerServiceKClass: KClass<*>? by lazy {
        try {
            Class.forName("com.ead.katalyst.scheduler.SchedulerService")
                .kotlin
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    /**
     * Builds the complete dependency graph from all discovered types.
     *
     * @return Complete dependency graph with all nodes, edges, and bindings
     */
    fun buildGraph(): DependencyGraph {
        logger.info("Building dependency graph from discovered types")

        val allDiscoveredTypes = discoveredTypes.values.flatten().toSet()
        val nodes = mutableMapOf<KClass<*>, ComponentNode>()
        val edges = mutableMapOf<KClass<*>, Set<KClass<*>>>()
        val secondaryTypeBindings = mutableMapOf<KClass<*>, MutableSet<KClass<*>>>()

        // Get types already in Koin (from features) and include additional available types
        // (e.g., configurations that will be registered in Phase 6a)
        val koinProvidedTypes = getKoinProvidedTypes() + additionalAvailableTypes

        logger.debug("Koin provides {} types (including {} additional available types)",
            koinProvidedTypes.size, additionalAvailableTypes.size)

        // Build nodes and extract dependencies for each component
        for (componentType in allDiscoveredTypes) {
            logger.debug("Analyzing component: {}", componentType.qualifiedName)

            // Extract all dependencies (constructor + properties)
            val dependencies = extractDependencies(componentType)

            logger.debug("  Found {} dependencies", dependencies.size)

            // Extract secondary types (interfaces this component implements)
            val secondaryTypes = computeSecondaryTypes(componentType)

            logger.debug("  Provides {} secondary types", secondaryTypes.size)

            // Register secondary type bindings
            secondaryTypes.forEach { secondary ->
                secondaryTypeBindings
                    .getOrPut(secondary) { mutableSetOf() }
                    .add(componentType)
            }

            // Check instantiability
            val isInstantiable = canBeInstantiated(componentType)
            if (!isInstantiable) {
                logger.debug("  WARNING: Component is not instantiable")
            }

            // Create node
            val node = ComponentNode(
                type = componentType,
                dependencies = dependencies,
                secondaryTypes = secondaryTypes,
                isDiscoverable = true,
                isInstantiable = isInstantiable
            )

            nodes[componentType] = node

            // Create edges for all dependencies
            val dependencyTypes = dependencies
                .filter { !it.isOptional }  // Only required dependencies form edges
                .map { it.type }
                .toSet()

            edges[componentType] = dependencyTypes

            logger.debug("  Created edges to {} dependencies", dependencyTypes.size)
        }

        // Now resolve dependencies (check if they can be found in Koin or discovered)
        val finalNodes = mutableMapOf<KClass<*>, ComponentNode>()
        for ((type, node) in nodes) {
            val updatedDependencies = node.dependencies.map { dep ->
                val isResolvable = canResolveDependency(dep.type, nodes, secondaryTypeBindings, koinProvidedTypes)
                if (isResolvable) {
                    logger.debug("    Dependency {} is resolvable", dep.type.simpleName)
                } else {
                    logger.debug("    WARNING: Dependency {} cannot be resolved", dep.type.simpleName)
                }
                dep.copy(isResolvable = isResolvable)
            }

            finalNodes[type] = node.copy(dependencies = updatedDependencies)
        }

        val graph = DependencyGraph(
            nodes = finalNodes,
            edges = edges,
            secondaryTypeBindings = secondaryTypeBindings,
            koinProvidedTypes = koinProvidedTypes
        )

        logger.info("Dependency graph built: {} nodes, {} edges, {} secondary bindings",
            graph.nodeCount(), graph.edgeCount(), secondaryTypeBindings.size)

        return graph
    }

    /**
     * Extracts all dependencies for a component type.
     *
     * Includes:
     * 1. Constructor parameter dependencies
     * 2. Well-known mutable property dependencies
     *
     * @param type The component type to analyze
     * @return List of all dependencies
     */
    private fun extractDependencies(type: KClass<*>): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()

        // Extract constructor dependencies
        dependencies.addAll(extractConstructorDependencies(type))

        // Extract well-known property dependencies
        dependencies.addAll(extractWellKnownPropertyDependencies(type))

        return dependencies
    }

    /**
     * Extracts constructor parameter dependencies.
     *
     * @param type The component type
     * @return List of constructor parameter dependencies
     */
    private fun extractConstructorDependencies(type: KClass<*>): List<Dependency> {
        val constructor = type.primaryConstructor ?: return emptyList()

        return constructor.parameters
            .filter { it.kind.name == "VALUE" }  // Only VALUE parameters, not receivers
            .map { param ->
                val paramType = param.type.jvmErasure
                Dependency(
                    type = paramType,
                    parameterName = param.name ?: "param",
                    isOptional = param.isOptional,
                    source = DependencySource.CONSTRUCTOR
                )
            }
    }

    /**
     * Extracts well-known property dependencies.
     *
     * These are mutable properties that will be auto-injected:
     * - DatabaseTransactionManager
     * - SchedulerService (if available)
     *
     * @param type The component type
     * @return List of well-known property dependencies
     */
    private fun extractWellKnownPropertyDependencies(type: KClass<*>): List<Dependency> {
        val properties = mutableListOf<Dependency>()

        val mutableProps = type.memberProperties
            .filterIsInstance<KMutableProperty1<Any, Any?>>()

        mutableProps.forEach { property ->
            val propType = property.returnType.jvmErasure

            when {
                propType == DatabaseTransactionManager::class -> {
                    properties.add(Dependency(
                        type = DatabaseTransactionManager::class,
                        parameterName = property.name,
                        isOptional = true,  // May or may not use it
                        source = DependencySource.WELL_KNOWN_PROPERTY
                    ))
                }
                schedulerServiceKClass != null && propType == schedulerServiceKClass -> {
                    properties.add(Dependency(
                        type = schedulerServiceKClass!!,
                        parameterName = property.name,
                        isOptional = true,  // May or may not use it
                        source = DependencySource.WELL_KNOWN_PROPERTY
                    ))
                }
            }
        }

        return properties
    }

    /**
     * Checks if a type can be resolved from available sources.
     *
     * Resolution sources (in order):
     * 1. Try to get from Koin directly (for framework and feature-provided types)
     * 2. Discovered component type
     * 3. Secondary type binding from another component
     * 4. Known feature types
     *
     * @param type The required type
     * @param discoveredComponents Map of discovered component types
     * @param secondaryBindings Secondary type binding registry
     * @param koinTypes Types known to be available from Koin
     * @return true if type can be resolved
     */
    private fun canResolveDependency(
        type: KClass<*>,
        discoveredComponents: Map<KClass<*>, ComponentNode>,
        secondaryBindings: Map<KClass<*>, Set<KClass<*>>>,
        koinTypes: Set<KClass<*>>
    ): Boolean {
        // Special handling for known framework and feature types that will be registered
        // but might not be eager-loaded yet during validation
        val knownAvailableTypes = setOf(
            // Core framework types
            "com.ead.katalyst.database.DatabaseFactory",
            "com.ead.katalyst.core.transaction.DatabaseTransactionManager",

            // Feature types
            "com.ead.katalyst.core.config.ConfigProvider",      // From enableConfigProvider()
            "com.ead.katalyst.events.bus.EventBus",             // From enableEvents()
            "com.ead.katalyst.scheduler.SchedulerService"       // From enableScheduler()
        )

        if (type.qualifiedName in knownAvailableTypes) {
            logger.debug("Type {} is a known framework/feature type, assuming it will be available", type.simpleName)
            return true
        }

        // Try to actually get it from Koin - this catches feature and other types
        try {
            @Suppress("UNCHECKED_CAST")
            val instance: Any = koin.get(type as KClass<Any>)
            logger.debug("Type {} is available in Koin", type.simpleName)
            return true
        } catch (e: Exception) {
            // Not in Koin yet - continue checking other sources
            logger.debug("Type {} not directly available in Koin: {}", type.simpleName, e.message)
        }

        // Check if in the hardcoded Koin types list
        if (type in koinTypes) return true

        // Check if it's a discovered component
        if (type in discoveredComponents) return true

        // Check if it's provided as secondary type binding
        if (type in secondaryBindings) return true

        // Special case: Koin itself can always be injected
        if (type == Koin::class) return true

        return false
    }

    /**
     * Computes secondary type bindings for a component.
     *
     * Secondary types are interfaces implemented by the component (excluding base types).
     *
     * @param type The component type
     * @return List of secondary interface types
     */
    private fun computeSecondaryTypes(type: KClass<*>): List<KClass<*>> {
        val reserved = setOf(
            Any::class,
            Component::class,
            Service::class,
            CrudRepository::class,
            Comparable::class,
            Serializable::class
        )

        return type.supertypes
            .map { it.jvmErasure }
            .filter { candidate ->
                candidate != type &&
                    candidate != Any::class &&
                    candidate.java.isInterface &&
                    candidate !in reserved
            }
    }

    /**
     * Checks if a component type can be instantiated.
     *
     * Returns false if:
     * - The class is abstract
     * - The class is an interface
     * - There is no accessible constructor
     *
     * @param type The component type
     * @return true if the type can be instantiated
     */
    private fun canBeInstantiated(type: KClass<*>): Boolean {
        if (type.isAbstract || type.java.isInterface) return false

        val constructor = type.primaryConstructor ?: type.constructors.firstOrNull()
        return constructor != null
    }

    /**
     * Gets all types currently available in Koin.
     *
     * These are types provided by feature modules and the core DI module.
     *
     * This method checks both:
     * 1. What's actually registered in Koin
     * 2. What feature classes are available on the classpath
     *
     * The validation phase will definitively check if dependencies can be resolved.
     *
     * @return Set of types available in Koin (best effort)
     */
    private fun getKoinProvidedTypes(): Set<KClass<*>> {
        val types = mutableSetOf<KClass<*>>()

        // DatabaseTransactionManager is always provided by CoreDIModule
        try {
            koin.get<DatabaseTransactionManager>()
            types.add(DatabaseTransactionManager::class)
        } catch (_: Exception) {
            logger.debug("DatabaseTransactionManager not available in Koin")
        }

        // Check for feature-provided types by verifying they're available on classpath
        val featureTypes = listOf(
            "com.ead.katalyst.events.bus.EventBus",
            "com.ead.katalyst.scheduler.SchedulerService",
            "com.ead.katalyst.config.ConfigProvider"
        )

        featureTypes.forEach { className ->
            try {
                val clazz = Class.forName(className).kotlin
                types.add(clazz)
                logger.debug("Found feature type available: {}", className)
            } catch (_: ClassNotFoundException) {
                // Feature not available on classpath - that's OK
                logger.debug("Feature type not available on classpath: {}", className)
            }
        }

        // Koin itself is always available
        types.add(Koin::class)

        logger.debug("Koin provides {} types: {}", types.size, types.map { it.simpleName })

        return types
    }
}
