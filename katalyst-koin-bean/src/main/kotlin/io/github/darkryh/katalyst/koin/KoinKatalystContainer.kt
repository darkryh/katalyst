package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainer
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.qualifier.named
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

/**
 * Koin-backed implementation of [KatalystContainer].
 *
 * Add the `katalyst-koin-bean` module when your application uses Koin as the
 * active Katalyst dependency injection adapter.
 */
class KoinKatalystContainer(
    val koin: Koin,
) : KatalystContainer {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        koin.get(type as KClass<Any>, qualifier = qualifier?.let(::named))

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
        runCatching { get(type, qualifier) }.getOrNull()

    @OptIn(KoinInternalApi::class)
    override fun <T : Any> getAll(type: KClass<T>): List<T> =
        koin.scopeRegistry.rootScope.getAll<Any>(type).map { type.cast(it) }

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        getOrNull(type, qualifier) != null
}
