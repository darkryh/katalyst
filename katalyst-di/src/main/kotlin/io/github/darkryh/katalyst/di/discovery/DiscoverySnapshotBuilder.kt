package io.github.darkryh.katalyst.di.discovery

import kotlin.reflect.KClass

class DiscoverySnapshotBuilder {
    private val categories = linkedMapOf<String, Set<KClass<*>>>()

    fun category(name: String, types: Set<KClass<*>>): DiscoverySnapshotBuilder = apply {
        categories[name] = types
    }

    fun build(): DiscoverySnapshot = DiscoverySnapshot(categories.toMap())
}
