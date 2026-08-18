package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.exception.BeanNotFoundException
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.qualifier.named
import java.util.IdentityHashMap
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

    /**
     * Resolves [type], failing with an `IllegalStateException` when nothing is registered.
     *
     * [KatalystContainer] is the engine-agnostic facade: `katalyst-core` does not depend on Koin,
     * so a caller could not catch `NoDefinitionFoundException` without putting Koin on its own
     * compile classpath, and the in-memory test engine throws `IllegalStateException` for the same
     * condition. Leaking the engine's own exception type through the facade means a `catch` that
     * passes the suite and misses in production. The Koin exception is kept as the cause.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        try {
            koin.get(type as KClass<Any>, qualifier = qualifier?.let(::named)) as T
        } catch (error: NoDefinitionFoundException) {
            throw BeanNotFoundException(type, qualifier, error)
        }

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
        runCatching { get(type, qualifier) }.getOrNull()

    /**
     * Every bean bound to [type], in registration order, one entry per instance.
     *
     * Koin returns the registry's `ConcurrentHashMap` values, so it is unordered, and it returns
     * one entry per *factory* - an instance rebound under a second type has two factories and would
     * otherwise be handed back twice. Both are fixed here rather than left to callers: `getAll` is
     * the only meaningful resolution for a multibinding marker, so a duplicate is a hook that runs
     * twice and an arbitrary order is a hook set that runs in a different order than it did in the
     * suite that signed it off. See [KoinRegistrationOrder].
     */
    @OptIn(KoinInternalApi::class)
    override fun <T : Any> getAll(type: KClass<T>): List<T> {
        val seen = IdentityHashMap<Any, Boolean>()
        return koin.scopeRegistry.rootScope.getAll<Any>(type)
            .sortedBy(KoinRegistrationOrder::sequenceOf)
            .filter { instance -> seen.put(instance, true) == null }
            .map { instance -> type.cast(instance) }
    }

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        getOrNull(type, qualifier) != null
}
