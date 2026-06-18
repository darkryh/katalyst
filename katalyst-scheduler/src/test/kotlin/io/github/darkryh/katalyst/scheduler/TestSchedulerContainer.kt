package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.core.di.KatalystContainer
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

internal class TestSchedulerContainer(
    private val beans: Map<KClass<*>, Any>,
) : KatalystContainer {
    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        getOrNull(type, qualifier) ?: error("No bean registered for ${type.qualifiedName}")

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
        beans[type]?.let(type::cast)

    override fun <T : Any> getAll(type: KClass<T>): List<T> =
        beans.filterKeys { it == type }.values.map(type::cast)

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        beans.containsKey(type)
}
