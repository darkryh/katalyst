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
        container.register(primaryType, qualifier, instance)
        secondaryTypes.forEach { container.register(it, qualifier, instance) }
    }

    override fun currentOrNull(): KatalystContainer = container

    override fun stop() {
        container.clear()
        KatalystContainerProvider.reset()
    }
}

class TestContainer : KatalystContainer {
    private val beans = linkedMapOf<Pair<KClass<*>, String?>, MutableList<Any>>()

    fun register(type: KClass<*>, qualifier: String? = null, instance: Any) {
        beans.getOrPut(type to qualifier) { mutableListOf() }.add(instance)
    }

    fun clear() {
        beans.clear()
    }

    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        getOrNull(type, qualifier) ?: error("No bean registered for ${type.qualifiedName}")

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
        beans[type to qualifier]?.lastOrNull()?.let(type::cast)

    override fun <T : Any> getAll(type: KClass<T>): List<T> =
        beans[type to null].orEmpty().map(type::cast)

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        beans[type to qualifier]?.isNotEmpty() == true
}
