package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.registry.ResettableRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks [StartupHook] implementations discovered by auto-binding.
 *
 * Koin cannot reliably represent multiple unqualified secondary bindings for
 * the same interface key. This registry preserves all discovered hooks
 * so startup can execute the complete set deterministically.
 */
object StartupHookRegistry : ResettableRegistry {
    init {
        RegistryManager.register(this)
    }

    private val hooks = CopyOnWriteArrayList<StartupHook>()

    fun register(hook: StartupHook) {
        if (hooks.none { it::class == hook::class }) {
            hooks.add(hook)
        }
    }

    fun getAll(): List<StartupHook> = hooks.toList()

    override fun reset() {
        hooks.clear()
    }
}
