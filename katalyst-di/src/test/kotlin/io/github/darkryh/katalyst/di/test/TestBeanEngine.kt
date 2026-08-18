package io.github.darkryh.katalyst.di.test

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import org.slf4j.LoggerFactory
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass
import kotlin.reflect.full.cast
import io.github.darkryh.katalyst.core.exception.BeanNotFoundException

/**
 * In-module double for [KatalystBeanEngine], held to the same contract as the engines that ship.
 *
 * `BeanEngineContractTest` in this module runs `KatalystBeanEngineContract` against this double and
 * against the real `KoinBeanEngine`, so a divergence introduced here fails immediately. That is the
 * whole point: issue #31 was a test engine that did not reproduce the production engine's eviction,
 * which is how a green suite coexisted with an application whose scheduled jobs never ran.
 *
 * A test using this double must `start()` it before registering, exactly as production must
 * bootstrap the container before wiring beans into it.
 */
class TestBeanEngine : KatalystBeanEngine {
    override val id: String = "test"
    val container = TestContainer()

    @Volatile
    private var started: Boolean = false

    override fun start(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ): KatalystContainer {
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
        // bound only to a shared marker would otherwise lose its only declared key to the next
        // bean binding that marker (#31).
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

    private fun requireStarted() {
        check(started) {
            "Test bean engine is not initialized. Start it before loading modules or registering " +
                "instances."
        }
    }
}

/**
 * Models Koin's registry, not a convenient multimap.
 *
 * Koin indexes one factory per (type, qualifier) key and the last writer wins, so a fake that
 * appends instead cannot represent an eviction. Every registration additionally holds a
 * [RegistrationKey] of its own that no other registration can take, which is what keeps a displaced
 * bean in `getAll` while `get` hands out the later writer.
 */
class TestContainer : KatalystContainer {
    private val logger = LoggerFactory.getLogger("TestBeanEngine")

    private sealed interface IndexKey

    private data class TypeKey(val type: KClass<*>, val qualifier: String?) : IndexKey

    private data class RegistrationKey(val sequence: Long) : IndexKey

    private class Registration(
        val instance: Any,
        val types: Set<KClass<*>>,
        val qualifier: String?,
        val sequence: Long,
    )

    private val beans = ConcurrentHashMap<IndexKey, Registration>()
    private val sequences = AtomicLong(0)
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
     * Closes every distinct [AutoCloseable] registration, newest first, as the real engine does at
     * shutdown. Identity comparison so an instance bound under several types is closed once, read
     * from the registration log so a displaced bean is still released, and a failure is reported at
     * WARN naming the class - the same contract `KoinBeanEngine` honours. A double is only useful
     * while it models the thing it stands in for.
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

    override fun <T : Any> getAll(type: KClass<T>): List<T> {
        val seen = IdentityHashMap<Any, Boolean>()
        return beans.values
            .filter { registration -> type in registration.types }
            .sortedBy { registration -> registration.sequence }
            .filter { registration -> seen.put(registration.instance, true) == null }
            .map { registration -> type.cast(registration.instance) }
    }

    override fun contains(type: KClass<*>, qualifier: String?): Boolean =
        getOrNull(type, qualifier) != null

    /** Orphaning first, sameness never: two beans of one class are the case that looks harmless. */
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
