package io.github.darkryh.katalyst.di.analysis

import io.github.darkryh.katalyst.di.extension.ExtensionPoints

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
     * The result is checked with [validateOrder] before it is returned. That post-condition used
     * to have no caller anywhere in the framework: a sort that silently dropped a component, or
     * placed a dependency after its dependent, would have been handed straight to the registrar and
     * surfaced later as an unrelated injection failure. Handing back a corrupted order is worse
     * than failing here, so a violation aborts.
     *
     * @return List of component types in safe instantiation order
     * @throws IllegalStateException if the graph contains cycles, or if the computed order does
     *   not satisfy [validateOrder]
     */
    fun computeOrder(): List<KClass<*>> {
        logger.info("Computing component instantiation order")

        val order = try {
            // Use the graph's built-in topological sort
            graph.topologicalSort()
        } catch (e: IllegalStateException) {
            logger.error("Cannot compute order: graph contains cycles")
            throw e
        }

        logger.info("Computed order for {} components:", order.size)
        order.forEachIndexed { index, type ->
            logger.debug("  [{}] {}", index + 1, type.simpleName)
        }

        if (!validateOrder(order)) {
            throw IllegalStateException(
                "Computed instantiation order failed its post-condition check: it does not contain " +
                    "every ordered component exactly once with each dependency before its dependent. " +
                    "See the preceding ComponentOrderComputer error for the specific violation."
            )
        }

        return order
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

        // Count components that take part in dependency ordering (migrations do not).
        val nonMigrationCount = graph.nodes.keys.count { ExtensionPoints.joinsDependencyGraph(it) }

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

        for (componentType in graph.edges.keys) {
            val componentPosition = positions[componentType] ?: continue

            for (dependency in graph.getDependencyNodes(componentType)) {
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

        val dependencies = graph.getDependencyNodes(type)
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
