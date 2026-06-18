package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.core.di.KatalystContainer
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

internal class TestKatalystContainer(
    private val beans: Map<Key, Any>,
) : KatalystContainer {
    data class Key(val type: KClass<*>, val qualifier: String? = null)

    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        getOrNull(type, qualifier) ?: error("No bean registered for ${type.qualifiedName}")

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
        beans[Key(type, qualifier)]?.let(type::cast)

    override fun <T : Any> getAll(type: KClass<T>): List<T> =
        beans.filterKeys { it.type == type }.values.map(type::cast)

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        beans.containsKey(Key(type, qualifier))
}
