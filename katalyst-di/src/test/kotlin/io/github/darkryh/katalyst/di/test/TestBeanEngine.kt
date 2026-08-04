package io.github.darkryh.katalyst.di.test

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

class TestBeanEngine : KatalystBeanEngine {
    override val id: String = "test"
    val container = TestContainer()

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
class TestContainer : KatalystContainer {
    private class Registration(val instance: Any, val types: Set<KClass<*>>, val qualifier: String?)

    private val beans = linkedMapOf<Pair<KClass<*>, String?>, Registration>()

    fun register(instance: Any, types: Set<KClass<*>>, qualifier: String? = null) {
        val registration = Registration(instance, types, qualifier)
        types.forEach { type -> beans[type to qualifier] = registration }
    }

    fun clear() {
        beans.clear()
    }

    /**
     * Closes every distinct [AutoCloseable] registration, newest first, as the real engine does at
     * shutdown. Identity comparison so an instance bound under several types is closed once.
     */
    fun closeRegistrations() {
        val pending = mutableListOf<AutoCloseable>()
        beans.values.forEach { registration ->
            val instance = registration.instance
            if (instance is AutoCloseable && pending.none { it === instance }) {
                pending += instance
            }
        }
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
