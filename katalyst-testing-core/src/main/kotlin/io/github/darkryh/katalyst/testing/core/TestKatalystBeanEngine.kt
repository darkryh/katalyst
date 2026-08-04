package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

internal class TestKatalystBeanEngine : KatalystBeanEngine {
    override val id: String = "katalyst-test"
    private val container = TestKatalystContainer()

    override fun start(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ): KatalystContainer {
        KatalystContainerProvider.set(container)
        loadModules(modules, allowOverrides)
        return container
    }

    override fun loadModules(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ) {
        modules.flatMap { it.definitions }.forEach { definition ->
            val instance = definition.provider(KatalystBeanContext(container))
            registerInstance(instance, definition.type, qualifier = definition.qualifier)
        }
    }

    override fun registerInstance(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>,
        qualifier: String?,
    ) {
        // The concrete class is always indexed, exactly as KoinBeanEngine does it: a definition
        // bound only to a shared marker would otherwise lose its only key to the next bean
        // binding that marker (#31).
        val types = LinkedHashSet<KClass<*>>()
        types += primaryType
        types += secondaryTypes
        types += instance::class
        container.register(instance, types, qualifier)
    }

    override fun currentOrNull(): KatalystContainer = container

    override fun stop() {
        container.closeRegistrations()
        container.clear()
        KatalystContainerProvider.reset()
    }
}

/**
 * Models Koin's registry, not a convenient multimap.
 *
 * Koin indexes one factory per (type, qualifier) key and the last writer wins, so a fake that
 * appends instead cannot represent an eviction — which is how #31 stayed invisible to a green
 * suite. Lookups mirror Koin's: `get` reads the exact key, `getAll` scans live registrations
 * by bound type.
 */
private class TestKatalystContainer : KatalystContainer {
    private class Registration(val instance: Any, val types: Set<KClass<*>>, val qualifier: String?)

    private val beans = linkedMapOf<Pair<KClass<*>, String?>, Registration>()

    /** Every closeable registration, in registration order. See [closeRegistrations]. */
    private val managed = mutableListOf<AutoCloseable>()

    fun register(instance: Any, types: Set<KClass<*>>, qualifier: String? = null) {
        val registration = Registration(instance, types, qualifier)
        types.forEach { type -> beans[type to qualifier] = registration }
        if (instance is AutoCloseable && managed.none { it === instance }) {
            managed += instance
        }
    }

    fun clear() {
        beans.clear()
        managed.clear()
    }

    /**
     * Closes every distinct [AutoCloseable] registration, newest first, exactly as the real engine
     * does at shutdown. Compared by identity so an instance bound under several types is closed
     * once — a fake that closed per key would hide a double-close the real engine would not make.
     *
     * Read from the registration log, not from the live index: an override takes the index keys of
     * the bean it replaces (including its identity key, when both are of the same class), so an
     * index-derived list drops the displaced instance. `KoinBeanEngine` tracks at registration and
     * still closes it — a bean the container created stays the container's to release even after it
     * has been replaced.
     */
    fun closeRegistrations() {
        val pending = managed.toList()
        managed.clear()
        pending.asReversed().forEach { instance -> runCatching { instance.close() } }
    }

    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        getOrNull(type, qualifier) ?: error("No bean registered for ${type.qualifiedName}")

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
        beans[type to qualifier]?.instance?.let(type::cast)

    override fun <T : Any> getAll(type: KClass<T>): List<T> =
        beans.values
            .distinct()
            .filter { registration -> type in registration.types }
            .map { registration -> type.cast(registration.instance) }

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        beans.containsKey(type to qualifier)
}
