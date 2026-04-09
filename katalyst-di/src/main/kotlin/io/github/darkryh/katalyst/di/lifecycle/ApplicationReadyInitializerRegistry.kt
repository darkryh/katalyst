package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.registry.ResettableRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks [ApplicationReadyInitializer] implementations discovered by auto-binding.
 */
object ApplicationReadyInitializerRegistry : ResettableRegistry {
    init {
        RegistryManager.register(this)
    }

    private val initializers = CopyOnWriteArrayList<ApplicationReadyInitializer>()

    fun register(initializer: ApplicationReadyInitializer) {
        if (initializers.none { it::class == initializer::class }) {
            initializers.add(initializer)
        }
    }

    fun getAll(): List<ApplicationReadyInitializer> = initializers.toList()

    override fun reset() {
        initializers.clear()
    }
}
