package com.ead.katalyst.di.analysis

import kotlin.reflect.KClass

/**
 * Represents a single dependency that a component requires.
 *
 * A dependency can be:
 * - A constructor parameter (required or optional)
 * - A well-known property (DatabaseTransactionManager, SchedulerService)
 * - A secondary interface type that the component implements
 *
 * @param type The required type/class
 * @param parameterName The constructor parameter name or property name
 * @param isOptional Whether this dependency is nullable/optional
 * @param isResolvable Whether this type can be resolved from Koin or discovered components
 * @param source Where this dependency comes from (constructor, property, secondary type)
 */
data class Dependency(
    val type: KClass<*>,
    val parameterName: String,
    val isOptional: Boolean = false,
    val isResolvable: Boolean = false,
    val source: DependencySource = DependencySource.CONSTRUCTOR
) {
    /**
     * Get a human-readable description of this dependency.
     */
    fun describe(): String = when (source) {
        DependencySource.CONSTRUCTOR -> "constructor parameter '$parameterName' of type ${type.simpleName}"
        DependencySource.WELL_KNOWN_PROPERTY -> "well-known property '$parameterName' of type ${type.simpleName}"
        DependencySource.SECONDARY_TYPE -> "secondary type binding ${type.simpleName}"
    }
}

/**
 * Source of the dependency in the component.
 */
enum class DependencySource {
    /**
     * Dependency comes from a constructor parameter.
     */
    CONSTRUCTOR,

    /**
     * Dependency comes from a well-known mutable property
     * (DatabaseTransactionManager, SchedulerService).
     */
    WELL_KNOWN_PROPERTY,

    /**
     * Dependency is a secondary type binding the component provides.
     */
    SECONDARY_TYPE
}
