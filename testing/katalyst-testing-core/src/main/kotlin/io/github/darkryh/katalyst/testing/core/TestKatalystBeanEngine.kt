package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass
import kotlin.reflect.full.cast
import io.github.darkryh.katalyst.core.exception.BeanNotFoundException

/**
 * The in-memory engine `katalystTestEnvironment` boots.
 *
 * It exists so a test suite does not pay for a real Koin context, and it is only worth having while
 * it answers every observable question the way `KoinBeanEngine` does. Issue #31 is the record of
 * what the alternative costs: the production engine evicted the scheduler's `ReadyHook` from its
 * index, this engine did not, and a completely green suite said nothing about an application whose
 * scheduled jobs never ran.
 *
 * Both engines are held to `KatalystBeanEngineContract`. Anything changed here has to be changed
 * there too, or the contract fails - which is the point.
 */
internal class TestKatalystBeanEngine : KatalystBeanEngine {
    override val id: String = "katalyst-test"
    private val container = TestKatalystContainer()

    @Volatile
    private var started: Boolean = false

    override fun start(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ): KatalystContainer {
        // A start against a live container keeps it, exactly as `startKoin` reuses an existing
        // GlobalContext. Bootstrap runs twice on a Ktor hot reload and must not lose its beans.
        started = true
        KatalystContainerProvider.set(container)
        loadModules(modules, allowOverrides)
        return container
    }

    override fun loadModules(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ) {
        requireStarted()
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
        requireStarted()
        // The concrete class is always indexed, exactly as KoinBeanEngine does it: a definition
        // bound only to a shared marker would otherwise lose its only declared key to the next bean
        // binding that marker (#31).
        val types = LinkedHashSet<KClass<*>>()
        types += primaryType
        types += secondaryTypes
        types += instance::class
        container.register(instance, types, qualifier)
    }

    override fun currentOrNull(): KatalystContainer? = container.takeIf { started }

    override fun stop() {
        if (!started) return
        started = false
        container.closeRegistrations()
        container.clear()
        KatalystContainerProvider.reset()
    }

    /**
     * Registering into a container nobody started is a wiring bug, and Koin already refuses it.
     * A fake that accepts the write instead is the divergence that lets a broken bootstrap order
     * pass a test suite and fail on boot.
     */
    private fun requireStarted() {
        check(started) {
            "Katalyst test bean engine is not initialized. Bootstrap Katalyst before loading " +
                "modules or registering instances."
        }
    }
}

/**
 * Models Koin's registry, not a convenient multimap.
 *
 * Koin indexes one factory per (type, qualifier) key and the last writer wins, so a fake that
 * appends instead cannot represent an eviction - which is how #31 stayed invisible to a green
 * suite. Lookups mirror Koin's: `get` reads the exact key, `getAll` scans live registrations by
 * bound type, in registration order, de-duplicated by instance.
 *
 * Every registration additionally holds one [RegistrationKey] of its own, which no other
 * registration can ever take. That is the key that makes displacement survivable: an override wins
 * every *declared* key it shares, so `get` hands out the later bean, while the displaced bean stays
 * in `getAll` - which is the only meaningful resolution for a multibinding marker anyway.
 *
 * The index is concurrent because this engine is shipped: consumers boot it from their own suites,
 * where a parallel runner or a component registering from a coroutine dispatcher reaches
 * [register] from several threads at once. A plain `LinkedHashMap` silently drops entries under
 * that load, and a bean that is merely absent produces no error anywhere.
 */
internal class TestKatalystContainer : KatalystContainer {
    private val logger = LoggerFactory.getLogger("TestKatalystBeanEngine")

    private sealed interface IndexKey

    private data class TypeKey(val type: KClass<*>, val qualifier: String?) : IndexKey

    /** A key private to one registration, so no registration is ever left without an index entry. */
    private data class RegistrationKey(val sequence: Long) : IndexKey

    private class Registration(
        val instance: Any,
        val types: Set<KClass<*>>,
        val qualifier: String?,
        val sequence: Long,
    )

    private val beans = ConcurrentHashMap<IndexKey, Registration>()
    private val sequences = AtomicLong(0)

    /** Every closeable registration, in registration order. See [closeRegistrations]. */
    private val managedLock = Any()
    private val managed = mutableListOf<AutoCloseable>()

    fun register(instance: Any, types: Set<KClass<*>>, qualifier: String? = null) {
        val sequence = sequences.getAndIncrement()
        val registration = Registration(instance, types, qualifier, sequence)

        beans[RegistrationKey(sequence)] = registration
        types.forEach { type ->
            val key = TypeKey(type, qualifier)
            reportDisplacement(key, beans.put(key, registration), registration)
        }

        if (instance is AutoCloseable) {
            synchronized(managedLock) {
                if (managed.none { it === instance }) managed += instance
            }
        }
    }

    fun clear() {
        beans.clear()
        synchronized(managedLock) { managed.clear() }
    }

    /**
     * Closes every distinct [AutoCloseable] registration, newest first, exactly as the real engine
     * does at shutdown. Compared by identity so an instance bound under several types is closed
     * once - a fake that closed per key would hide a double-close the real engine would not make.
     *
     * Read from the registration log, not from the live index: a bean the container created stays
     * the container's to release even after another bean took its declared index keys.
     *
     * One failing close must not strand the rest, and a failure is reported at WARN naming the
     * class, matching `KoinBeanEngine`.
     */
    fun closeRegistrations() {
        val pending = synchronized(managedLock) {
            val snapshot = managed.toList()
            managed.clear()
            snapshot
        }
        pending.asReversed().forEach { instance ->
            runCatching { instance.close() }.onFailure { error ->
                logger.warn(
                    "Failed to close {} during shutdown; continuing with the rest",
                    instance::class.qualifiedName ?: instance::class.simpleName,
                    error,
                )
            }
        }
    }

    override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
        getOrNull(type, qualifier) ?: throw BeanNotFoundException(type, qualifier)

    override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? =
        beans[TypeKey(type, qualifier)]?.instance?.let(type::cast)

    /**
     * Every live registration bound to [type], in registration order, one entry per instance.
     *
     * De-duplicated by identity rather than by equality or by hash: rebinding one instance under a
     * second type is routine during boot (the database factory does it), and it is still one bean.
     * `System.identityHashCode` is not unique, so identity comparison is done properly.
     */
    override fun <T : Any> getAll(type: KClass<T>): List<T> {
        val seen = java.util.IdentityHashMap<Any, Boolean>()
        return beans.values
            .filter { registration -> type in registration.types }
            .sortedBy { registration -> registration.sequence }
            .filter { registration -> seen.put(registration.instance, true) == null }
            .map { registration -> type.cast(registration.instance) }
    }

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        getOrNull(type, qualifier) != null

    /**
     * Rebinding an index key is routine; losing a bean to one is the #31 failure mode.
     *
     * A displaced registration always keeps its own [RegistrationKey], so it stays in `getAll` and
     * nothing is silently dropped - that is DEBUG. WARN is reserved for a displaced registration
     * that is gone from the index entirely, which the private key makes impossible: if it ever
     * fires, the registration invariant itself has broken. Orphaning is therefore checked *before*
     * asking whether the two registrations look alike - a same-class duplicate is exactly the case
     * that looks like a harmless replacement and is not one.
     */
    private fun reportDisplacement(key: TypeKey, displaced: Registration?, writer: Registration) {
        if (displaced == null || displaced.instance === writer.instance) return

        val keepsPrivateKey = beans.containsKey(RegistrationKey(displaced.sequence))
        val keepsDeclaredKey = displaced.types.any { type ->
            val other = TypeKey(type, displaced.qualifier)
            other != key && beans[other] === displaced
        }

        when {
            keepsDeclaredKey -> logger.debug(
                "Rebound bean index '{}' from {} to {}",
                key,
                displaced.instance::class.simpleName,
                writer.instance::class.simpleName,
            )

            keepsPrivateKey -> logger.debug(
                "Rebound bean index '{}' from {} to {}; the displaced bean is now reachable " +
                    "through getAll only",
                key,
                displaced.instance::class.simpleName,
                writer.instance::class.simpleName,
            )

            else -> logger.warn(
                "Bean index '{}' was the last key of {}; {} takes it and the displaced bean is no " +
                    "longer resolvable. Every registration must keep an index key of its own.",
                key,
                displaced.instance::class.simpleName,
                writer.instance::class.simpleName,
            )
        }
    }

}
