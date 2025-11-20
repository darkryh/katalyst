package com.ead.katalyst.di.analysis

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger("DependencyGraph")

/**
 * Complete dependency graph for all discovered components.
 *
 * The graph contains:
 * - Nodes: Each discovered component type
 * - Edges: Dependency relationships (A depends on B)
 * - Secondary type bindings: Multi-type binding registry
 *
 * @param nodes Map of component type to node information
 * @param edges Map of component type to set of types it depends on
 * @param secondaryTypeBindings Map of interface type to set of components providing it
 * @param koinProvidedTypes Set of types already available in Koin (from features)
 */
data class DependencyGraph(
    val nodes: Map<KClass<*>, ComponentNode> = emptyMap(),
    val edges: Map<KClass<*>, Set<KClass<*>>> = emptyMap(),
    val secondaryTypeBindings: Map<KClass<*>, Set<KClass<*>>> = emptyMap(),
    val koinProvidedTypes: Set<KClass<*>> = emptySet()
) {

    /**
     * Get all direct dependencies of a component.
     *
     * @param type The component type
     * @return Set of types this component directly depends on
     */
    fun getDependencies(type: KClass<*>): Set<KClass<*>> =
        edges[type] ?: emptySet()

    /**
     * Get all transitive dependencies of a component (including indirect).
     *
     * @param type The component type
     * @return Set of all types this component transitively depends on
     */
    fun getTransitiveDependencies(type: KClass<*>): Set<KClass<*>> {
        val visited = mutableSetOf<KClass<*>>()
        val queue = mutableListOf(type)

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            if (current in visited) continue

            visited.add(current)
            queue.addAll(getDependencies(current))
        }

        visited.remove(type)
        return visited
    }

    /**
     * Check if a type is available (either in Koin or discoverable).
     *
     * @param type The type to check
     * @return true if available from Koin or as discovered component
     */
    fun isAvailable(type: KClass<*>): Boolean =
        type in koinProvidedTypes || type in nodes

    /**
     * Get providers of a secondary type binding.
     *
     * @param interfaceType The interface type to look up
     * @return Set of component types that provide this interface
     */
    fun getProvidersOfSecondaryType(interfaceType: KClass<*>): Set<KClass<*>> =
        secondaryTypeBindings[interfaceType] ?: emptySet()

    /**
     * Check if a dependency can be resolved.
     *
     * @param type The required type
     * @return true if the type is available directly or via secondary type binding
     */
    fun canResolve(type: KClass<*>): Boolean =
        isAvailable(type) || getProvidersOfSecondaryType(type).isNotEmpty()

    /**
     * Get number of nodes in the graph.
     */
    fun nodeCount(): Int = nodes.size

    /**
     * Get total number of edges in the graph.
     */
    fun edgeCount(): Int = edges.values.sumOf { it.size }

    /**
     * Get components with no dependencies (leaf nodes).
     *
     * @return Set of component types that have no dependencies
     */
    fun getLeafComponents(): Set<KClass<*>> =
        nodes.keys.filter { getDependencies(it).isEmpty() }.toSet()

    /**
     * Get components that depend on all others (root nodes).
     *
     * @return Set of component types that nothing depends on
     */
    fun getRootComponents(): Set<KClass<*>> {
        val hasDependents = mutableSetOf<KClass<*>>()
        edges.values.forEach { deps -> hasDependents.addAll(deps) }
        return nodes.keys.subtract(hasDependents)
    }

    /**
     * Get a topological ordering of nodes.
     *
     * Useful for determining instantiation order.
     * Components with no dependencies come first.
     *
     * @return List of component types in topological order (if acyclic)
     * @throws IllegalStateException if graph contains cycles
     */
    fun topologicalSort(): List<KClass<*>> {
        // Filter out KatalystMigration classes - they have different lifecycle
        // and shouldn't be included in component instantiation order
        val componentsToSort = mutableSetOf<KClass<*>>()
        val migrationsFound = mutableSetOf<KClass<*>>()

        for (componentType in nodes.keys) {
            try {
                if (!com.ead.katalyst.migrations.KatalystMigration::class.java.isAssignableFrom(componentType.java)) {
                    componentsToSort.add(componentType)
                } else {
                    migrationsFound.add(componentType)
                }
            } catch (_: Exception) {
                // KatalystMigration not available, include the component
                componentsToSort.add(componentType)
            }
        }

        logger.debug("Topological sort: {} components to sort, {} migrations excluded",
            componentsToSort.size, migrationsFound.size)
        if (componentsToSort.isNotEmpty()) {
            logger.debug("  Components: {}",
                componentsToSort.map { it.simpleName }.joinToString(", "))
        }
        if (migrationsFound.isNotEmpty()) {
            logger.debug("  Migrations (excluded): {}",
                migrationsFound.map { it.simpleName }.joinToString(", "))
        }

        val inDegree = mutableMapOf<KClass<*>, Int>()
        val adjList = mutableMapOf<KClass<*>, MutableSet<KClass<*>>>()

        // Initialize - only for non-migration components
        componentsToSort.forEach { node ->
            inDegree[node] = 0
            adjList[node] = mutableSetOf()
        }

        // Build adjacency list and in-degrees
        // Only include edges between non-migration components
        // IMPORTANT: edges[A] = {B, C} means "A depends on B and C"
        // For Kahn's algorithm:
        // - inDegree[A] = number of things A depends on
        // - adjList[B] should contain A (because A depends on B, so B comes before A)
        edges.forEach { (from, tos) ->
            if (from in componentsToSort) {
                tos.filter { it in componentsToSort }.forEach { to ->
                    // from depends on to, so:
                    adjList.getOrPut(to) { mutableSetOf() }.add(from)  // to comes before from
                    inDegree[from] = (inDegree[from] ?: 0) + 1  // from depends on something
                }
            }
        }

        // Kahn's algorithm for topological sort
        val queue = inDegree.filter { it.value == 0 }.map { it.key }.toMutableList()
        val result = mutableListOf<KClass<*>>()

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            result.add(current)

            adjList[current]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 0) - 1
                if (inDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }

        if (result.size != componentsToSort.size) {
            logger.error("Expected {} components but got {} in sorted order", componentsToSort.size, result.size)
            logger.error("Components in graph: {}", componentsToSort.map { it.simpleName })
            logger.error("Components in result: {}", result.map { it.simpleName })
            throw IllegalStateException("Graph contains cycles (after excluding ${nodes.size - componentsToSort.size} migration classes)")
        }

        logger.debug("Topological sort result ({} components):", result.size)
        result.forEachIndexed { index, componentType ->
            logger.debug("  [{}] {}", index + 1, componentType.simpleName)
        }

        return result
    }

    /**
     * Get a human-readable description of the graph.
     *
     * @return String representation of the graph
     */
    fun describe(): String = buildString {
        appendLine("Dependency Graph:")
        appendLine("  Nodes: ${nodeCount()}")
        appendLine("  Edges: ${edgeCount()}")
        appendLine("  Koin types: ${koinProvidedTypes.size}")
        appendLine("  Secondary type bindings: ${secondaryTypeBindings.size}")
        appendLine()

        if (nodes.isNotEmpty()) {
            appendLine("Components:")
            nodes.values.forEach { node ->
                appendLine("  ${node.describe().replace("\n", "\n    ")}")
            }
        }
    }
}
