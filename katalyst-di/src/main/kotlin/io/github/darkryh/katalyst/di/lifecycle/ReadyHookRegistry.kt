package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.registry.ResettableRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks [ReadyHook] implementations discovered by auto-binding.
 */
object ReadyHookRegistry : ResettableRegistry {
    init {
        RegistryManager.register(this)
    }

    private val hooks = CopyOnWriteArrayList<ReadyHook>()

    fun register(hook: ReadyHook) {
        if (hooks.none { it::class == hook::class }) {
            hooks.add(hook)
        }
    }

    fun getAll(): List<ReadyHook> = hooks.toList()

    override fun reset() {
        hooks.clear()
    }
}
