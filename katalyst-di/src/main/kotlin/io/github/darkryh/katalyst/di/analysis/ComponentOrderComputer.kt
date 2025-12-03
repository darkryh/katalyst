package io.github.darkryh.katalyst.di.analysis

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger("ComponentOrderComputer")

/**
 * Computes the safe instantiation order for components using topological sort.
 *
 * Given a dependency graph, this computer determines the order in which components
 * must be instantiated such that all dependencies are available when a component
 * is instantiated.
 *
 * For example, if A depends on B and B depends on C, the order is: C → B → A
 *
 * @param graph The dependency graph with all components and their relationships
 */
class ComponentOrderComputer(private val graph: DependencyGraph) {

    /**
     * Computes the instantiation order using topological sort (Kahn's algorithm).
     *
     * The topological sort guarantees that:
     * - Each component appears in the list exactly once
     * - A component appears before any component that depends on it
     * - The order is deterministic
     *
     * @return List of component types in safe instantiation order
     * @throws IllegalStateException if the graph contains cycles
     */
    fun computeOrder(): List<KClass<*>> {
        logger.info("Computing component instantiation order")

        try {
            // Use the graph's built-in topological sort
            val order = graph.topologicalSort()

            logger.info("Computed order for {} components:", order.size)
            order.forEachIndexed { index, type ->
                logger.debug("  [{}] {}", index + 1, type.simpleName)
            }

            return order
        } catch (e: IllegalStateException) {
            logger.error("Cannot compute order: graph contains cycles")
            throw e
        }
    }

    /**
     * Validates that the computed order is correct.
     *
     * Verification checks:
     * - Order has correct number of components (excluding migrations)
     * - Each component appears exactly once
     * - All dependencies of a component appear before it in the list
     *
     * @param order The computed order to validate
     * @return true if order is valid, false otherwise
     */
    fun validateOrder(order: List<KClass<*>>): Boolean {
        logger.debug("Validating instantiation order")

        // Count non-migration components
        var nonMigrationCount = 0
        for (node in graph.nodes.keys) {
            try {
                if (!io.github.darkryh.katalyst.migrations.KatalystMigration::class.java.isAssignableFrom(node.java)) {
                    nonMigrationCount++
                }
            } catch (_: Exception) {
                nonMigrationCount++
            }
        }

        // Check: all components present (excluding migrations)
        if (order.size != nonMigrationCount) {
            logger.error(
                "Order size mismatch: expected {} (excluding migrations), got {}",
                nonMigrationCount,
                order.size
            )
            return false
        }

        // Check: no duplicates
        if (order.distinct().size != order.size) {
            logger.error("Order contains duplicates")
            return false
        }

        // Check: each component appears before its dependents
        val positions = order.mapIndexed { index, type -> type to index }.toMap()

        for ((componentType, dependencies) in graph.edges) {
            val componentPosition = positions[componentType] ?: continue

            for (dependency in dependencies) {
                val dependencyPosition = positions[dependency] ?: continue

                if (dependencyPosition >= componentPosition) {
                    logger.error(
                        "Invalid order: {} should come before {} but comes after",
                        dependency.simpleName,
                        componentType.simpleName
                    )
                    return false
                }
            }
        }

        logger.debug("✓ Order validation passed")
        return true
    }

    /**
     * Gets components with no dependencies (can be instantiated first).
     *
     * @return List of "leaf" components that have no dependencies
     */
    fun getInitialComponents(): List<KClass<*>> {
        return graph.getLeafComponents().toList()
    }

    /**
     * Gets the instantiation group for a specific component.
     *
     * Components can be grouped by their distance from leaf nodes:
     * - Group 0: Components with no dependencies
     * - Group 1: Components that only depend on group 0
     * - Group 2: Components that only depend on group 0-1
     * - etc.
     *
     * @param type The component type
     * @return The instantiation group (0 = no dependencies, 1+ = depends on others)
     */
    fun getInstantiationGroup(type: KClass<*>): Int {
        val visited = mutableSetOf<KClass<*>>()
        return computeGroup(type, visited)
    }

    /**
     * Recursive helper to compute instantiation group.
     */
    private fun computeGroup(type: KClass<*>, visited: MutableSet<KClass<*>>): Int {
        if (type in visited) return 0  // Cycle protection

        visited.add(type)

        val dependencies = graph.getDependencies(type)
        if (dependencies.isEmpty()) {
            return 0  // No dependencies = group 0
        }

        // Group is 1 + max group of dependencies
        return 1 + (dependencies.maxOfOrNull { getInstantiationGroup(it) } ?: 0)
    }

    /**
     * Gets a detailed description of the instantiation order.
     *
     * @return Human-readable order description
     */
    fun describe(): String = buildString {
        val order = try {
            computeOrder()
        } catch (_: Exception) {
            return "Cannot compute order due to cycles"
        }

        appendLine("Instantiation Order:")
        order.forEachIndexed { index, type ->
            val group = getInstantiationGroup(type)
            val dependencies = graph.getDependencies(type)

            appendLine()
            appendLine("[${index + 1}/${order.size}] ${type.simpleName}")
            appendLine("  Group: $group (depends on ${dependencies.size} component(s))")

            if (dependencies.isNotEmpty()) {
                appendLine("  Dependencies:")
                dependencies.forEach { dep ->
                    appendLine("    - ${dep.simpleName}")
                }
            }
        }
    }
}
