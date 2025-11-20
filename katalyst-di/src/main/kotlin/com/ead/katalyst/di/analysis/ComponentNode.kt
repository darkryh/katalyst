package com.ead.katalyst.di.analysis

import com.ead.katalyst.di.error.ValidationError
import kotlin.reflect.KClass

/**
 * Represents a component node in the dependency graph.
 *
 * Each discovered component (Service, Component, Repository, etc.) is represented
 * as a node with its dependencies and properties.
 *
 * @param type The component class
 * @param dependencies List of all dependencies this component requires
 * @param secondaryTypes List of secondary interface types this component provides
 * @param isDiscoverable Whether this type is discoverable (extends Service/Component/etc)
 * @param isInstantiable Whether this type can be instantiated (public constructor, not abstract)
 * @param instantiationOrder The order in which this component should be instantiated (computed later)
 * @param errors Validation errors specific to this component
 */
data class ComponentNode(
    val type: KClass<*>,
    val dependencies: List<Dependency> = emptyList(),
    val secondaryTypes: List<KClass<*>> = emptyList(),
    val isDiscoverable: Boolean = true,
    val isInstantiable: Boolean = true,
    val instantiationOrder: Int = -1,
    val errors: List<ValidationError> = emptyList()
) {

    /**
     * Get all required (non-optional) dependencies.
     */
    fun requiredDependencies(): List<Dependency> =
        dependencies.filter { !it.isOptional }

    /**
     * Get all optional dependencies.
     */
    fun optionalDependencies(): List<Dependency> =
        dependencies.filter { it.isOptional }

    /**
     * Get all unresolvable dependencies.
     */
    fun unresolvableDependencies(): List<Dependency> =
        dependencies.filter { !it.isResolvable }

    /**
     * Check if this node has any validation errors.
     */
    fun hasErrors(): Boolean = errors.isNotEmpty()

    /**
     * Check if this node can be instantiated given current information.
     */
    fun canBeInstantiatedGiven(availableTypes: Set<KClass<*>>): Boolean {
        if (!isInstantiable) return false
        return dependencies.all { dep ->
            dep.isOptional || availableTypes.contains(dep.type)
        }
    }

    /**
     * Get a human-readable description of this node.
     */
    fun describe(): String = buildString {
        append("${type.simpleName}")
        if (secondaryTypes.isNotEmpty()) {
            append(" (provides: ${secondaryTypes.map { it.simpleName }.joinToString(", ")})")
        }
        if (dependencies.isNotEmpty()) {
            append("\n  Dependencies:")
            dependencies.forEach { dep ->
                append("\n    - ${dep.describe()}")
                if (dep.isOptional) append(" [optional]")
                if (!dep.isResolvable) append(" [UNRESOLVABLE]")
            }
        }
        if (errors.isNotEmpty()) {
            append("\n  Validation Errors:")
            errors.forEach { error ->
                append("\n    - ${error.message}")
            }
        }
    }
}
