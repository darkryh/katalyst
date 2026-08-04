package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.registry.ResettableRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Tracks which component provides each single-binding secondary type, so
 * [AutoBindingRegistrar] can reject two components claiming the same interface.
 *
 * Ownership describes one container and must not outlive it. Held process-wide it made the
 * second bootstrap in a JVM validate against the *first* application's bindings: a different
 * application booting afterwards was rejected for a collision between two types that never
 * coexisted in any container. It also pinned every discovered [KClass] for the life of the
 * process. Being a [ResettableRegistry] is what ties the lifetime to the container's —
 * [RegistryManager] clears it both when a bootstrap starts and when one is stopped.
 */
internal object SecondaryTypeOwnerRegistry : ResettableRegistry {
    init {
        RegistryManager.register(this)
    }

    private val owners = ConcurrentHashMap<KClass<*>, KClass<*>>()

    /** The component currently providing [secondaryType], or `null` if it is unclaimed. */
    fun ownerOf(secondaryType: KClass<*>): KClass<*>? = owners[secondaryType]

    fun claim(secondaryType: KClass<*>, owner: KClass<*>) {
        owners[secondaryType] = owner
    }

    override fun reset() {
        owners.clear()
    }
}
