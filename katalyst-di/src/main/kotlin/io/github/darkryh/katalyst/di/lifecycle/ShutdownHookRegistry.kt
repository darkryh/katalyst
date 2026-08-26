package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.registry.ResettableRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks [ShutdownHook] implementations discovered by auto-binding.
 */
object ShutdownHookRegistry : ResettableRegistry {
    init {
        RegistryManager.register(this)
    }

    private val hooks = CopyOnWriteArrayList<ShutdownHook>()

    fun register(hook: ShutdownHook) {
        if (hooks.none { it::class == hook::class }) {
            hooks.add(hook)
        }
    }

    fun getAll(): List<ShutdownHook> = hooks.toList()

    override fun reset() {
        hooks.clear()
    }
}
