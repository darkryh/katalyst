package io.github.darkryh.katalyst.di.discovery

import kotlin.reflect.KClass

/**
 * Immutable result of Katalyst discovery.
 *
 * This gives later phases one object to consume instead of passing mutable maps
 * whose category names are easy to mistype or interpret differently.
 */
data class DiscoverySnapshot(
    val categories: Map<String, Set<KClass<*>>>
) {
    val allTypes: Set<KClass<*>> = categories.values.flatten().toSet()

    fun types(category: String): Set<KClass<*>> = categories[category].orEmpty()

    fun asValidationMap(): Map<String, Set<KClass<*>>> = categories
}
