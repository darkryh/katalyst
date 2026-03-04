package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.registry.ResettableRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks [ApplicationInitializer] implementations discovered by auto-binding.
 *
 * Koin cannot reliably represent multiple unqualified secondary bindings for
 * the same interface key. This registry preserves all discovered initializers
 * so startup can execute the complete set deterministically.
 */
object ApplicationInitializerRegistry : ResettableRegistry {
    init {
        RegistryManager.register(this)
    }

    private val initializers = CopyOnWriteArrayList<ApplicationInitializer>()

    fun register(initializer: ApplicationInitializer) {
        if (initializers.none { it::class == initializer::class }) {
            initializers.add(initializer)
        }
    }

    fun getAll(): List<ApplicationInitializer> = initializers.toList()

    override fun reset() {
        initializers.clear()
    }
}

