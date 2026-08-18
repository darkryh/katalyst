package io.github.darkryh.katalyst.koin

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Kind
import org.koin.core.definition.indexKey
import org.koin.core.error.DefinitionOverrideException
import org.koin.core.instance.InstanceFactory
import org.koin.core.instance.SingleInstanceFactory
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory
import java.util.IdentityHashMap
import kotlin.reflect.KClass

/**
 * Koin bean engine entry point for the Katalyst application DSL.
 *
 * Usage:
 * ```kotlin
 * katalystApplication {
 *     beanEngine(KoinBeanEngine)
 * }
 * ```
 */
object KoinBeanEngine : KatalystBeanEngine {
    private val logger = LoggerFactory.getLogger("KoinBeanEngine")

    /** Closeable beans the container created, in registration order. See [closeManagedInstances]. */
    private val managedInstances = mutableListOf<AutoCloseable>()
    private val managedLock = Any()

    /**
     * The live container, so `currentOrNull` and `start` hand out the same object. Koin's
     * `GlobalContext` decides *whether* there is a container; this decides which wrapper represents
     * it, so that two callers comparing containers are not looking at two different wrappers of one
     * Koin.
     */
    @Volatile
    private var activeContainer: KoinKatalystContainer? = null

    override val id: String = "koin"

    override fun start(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ): KatalystContainer {
        val koin = currentKoinOrNull()?.also {
            // Existing context; new Katalyst definitions are registered below.
        } ?: startKoin {
            if (allowOverrides) {
                allowOverride(true)
            }
        }.koin

        val container = containerFor(koin).also(KatalystContainerProvider::set)
        registerDefinitions(modules, container)
        return container
    }

    override fun loadModules(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ) {
        registerDefinitions(modules, containerFor(currentKoin()))
    }

    @OptIn(KoinInternalApi::class)
    override fun registerInstance(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>,
        qualifier: String?,
    ) {
        // currentKoin() first: an engine that was never started must reject the write outright,
        // not remember the instance for a shutdown that will never come.
        val koin = currentKoin()
        trackManagedInstance(instance)
        val scopeQualifier = koin.scopeRegistry.rootScope.scopeQualifier
        val koinQualifier = qualifier?.let(::named)
        val definition = BeanDefinition(
            scopeQualifier = scopeQualifier,
            primaryType = primaryType,
            qualifier = koinQualifier,
            definition = { instance },
            kind = Kind.Singleton,
            secondaryTypes = indexedSecondaryTypes(instance, primaryType, secondaryTypes),
        )
        val factory = SingleInstanceFactory(definition)

        runCatching {
            // Written first: the private key is what makes every displacement below survivable.
            koin.instanceRegistry.saveMapping(
                true,
                privateRegistrationKey(KoinRegistrationOrder.record(instance)),
                factory,
                logWarning = false,
            )
            (listOf(primaryType) + definition.secondaryTypes).forEach { type ->
                val key = indexKey(type, definition.qualifier, scopeQualifier)
                reportDisplacement(koin, key, factory)
                koin.instanceRegistry.saveMapping(true, key, factory, logWarning = false)
            }
        }.onFailure { error ->
            if (error !is DefinitionOverrideException) {
                throw error
            }
        }
    }

    /**
     * A registry key that belongs to exactly one registration and that nothing else can take.
     *
     * The declared type keys are all shared ground: a marker key belongs to whoever wrote it last,
     * and even the concrete-class key added by [indexedSecondaryTypes] is taken by the next
     * instance *of the same class* that binds the same marker - two `SqlMigration`s bound to
     * `KatalystMigration` displace each other completely, and the first disappears with every
     * migration it carried. `BeanDefinition.equals` compares primary type, qualifier and scope and
     * ignores the instance, so that displacement did not even look unusual.
     *
     * Koin's registry is a plain `Map<String, InstanceFactory<*>>` and `getAll` filters its values
     * by [org.koin.core.definition.BeanDefinition.hasType], so a factory that holds any key at all
     * stays discoverable. The format deliberately cannot collide with a real index key, which
     * `indexKey` always builds as `<class>:<qualifier>:<scope>`.
     */
    private fun privateRegistrationKey(sequence: Long): String = "katalyst-registration#$sequence"

    /**
     * The declared secondary types plus the instance's own concrete class.
     *
     * The registry is a map, and every key here is written with `override = true`: the last
     * writer of an index key owns it. A definition whose only bound type is a shared marker —
     * `single<ReadyHook> { SchedulerInitializer() }` — therefore disappeared from the registry
     * as soon as any other bean bound that marker, taking every scheduled job with it (#31).
     * The identity key is the private key that keeps such a definition alive: `getAll` still
     * returns it afterwards because Koin filters live factories by [BeanDefinition.hasType],
     * not by index key.
     */
    private fun indexedSecondaryTypes(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>,
    ): List<KClass<*>> {
        val types = LinkedHashSet(secondaryTypes)
        types += instance::class
        types -= primaryType
        return types.toList()
    }

    /**
     * Rebinding an index key is routine; losing a definition to one is the #31 failure mode.
     * Koin's own override notice is suppressed (every key is written with `override = true`,
     * so it would fire on every legitimate rebind), so the half that matters is reported here.
     *
     * **Orphaning is checked before sameness.** The previous order asked first whether the writer
     * re-declared the same definition and called that a routine replace - but `BeanDefinition`
     * equality ignores the instance, so two *different* beans of one class bound to one marker
     * looked identical, and the displaced one was dropped at DEBUG. That is the residual half of
     * #31. What matters is whether anything is lost, never whether the definitions look alike.
     *
     * Given [privateRegistrationKey] nothing can be orphaned any more, so WARN here is an alarm on
     * the registration invariant itself rather than a routine diagnostic.
     */
    @OptIn(KoinInternalApi::class)
    private fun reportDisplacement(koin: Koin, key: String, factory: InstanceFactory<*>) {
        val displaced = koin.instanceRegistry.instances[key] ?: return
        if (displaced === factory) return

        when {
            isReachableWithout(koin, displaced, key) -> logger.debug(
                "Rebound bean index '{}' from {} to {}",
                key,
                displaced.beanDefinition,
                factory.beanDefinition,
            )

            isRegisteredUnderAnyKeyBut(koin, displaced, key) -> logger.debug(
                "Rebound bean index '{}' from {} to {}; the displaced definition is now reachable " +
                    "through getAll only",
                key,
                displaced.beanDefinition,
                factory.beanDefinition,
            )

            else -> logger.warn(
                "Bean index '{}' was the last key of {}; {} takes it and the displaced definition " +
                    "is no longer resolvable. Every registration must keep an index key of its own.",
                key,
                displaced.beanDefinition,
                factory.beanDefinition,
            )
        }
    }

    /** Whether [factory] still owns a *declared* index key other than [writtenKey]. */
    @OptIn(KoinInternalApi::class)
    private fun isReachableWithout(koin: Koin, factory: InstanceFactory<*>, writtenKey: String): Boolean {
        val definition = factory.beanDefinition
        return (listOf(definition.primaryType) + definition.secondaryTypes).any { type ->
            val key = indexKey(type, definition.qualifier, definition.scopeQualifier)
            key != writtenKey && koin.instanceRegistry.instances[key] === factory
        }
    }

    /**
     * Whether [factory] is still in the registry at all - including under its private registration
     * key, which is what decides whether `getAll` can still see it. Only reached on an actual
     * collision, which is rare enough that the scan does not matter.
     */
    @OptIn(KoinInternalApi::class)
    private fun isRegisteredUnderAnyKeyBut(koin: Koin, factory: InstanceFactory<*>, writtenKey: String): Boolean =
        koin.instanceRegistry.instances.any { (key, candidate) -> key != writtenKey && candidate === factory }

    override fun currentOrNull(): KatalystContainer? = currentKoinOrNull()?.let(::containerFor)

    /** The wrapper for [koin], reused while the same Koin is live so container identity is stable. */
    private fun containerFor(koin: Koin): KoinKatalystContainer {
        activeContainer?.takeIf { it.koin === koin }?.let { return it }
        return KoinKatalystContainer(koin).also { activeContainer = it }
    }

    override fun stop() {
        try {
            closeManagedInstances()
        } finally {
            try {
                runCatching { stopKoin() }
            } finally {
                activeContainer = null
                KoinRegistrationOrder.clear()
                KatalystContainerProvider.reset()
            }
        }
    }

    /**
     * Closes every [AutoCloseable] the container created, newest first.
     *
     * Koin only runs `onClose` callbacks carried on a bean definition, and Katalyst registers plain
     * instance factories that carry none — so nothing released a bean's resources at shutdown.
     * `SchedulerService` is the visible casualty: it is `AutoCloseable`, closing it cancels every job
     * coroutine, and without this its jobs kept firing after the application stopped, against a
     * torn-down container, until the JVM exited.
     *
     * Reverse registration order because a dependent is registered after what it depends on, and it
     * should be shut down before its dependencies. One failure must not strand the rest, so each
     * close is isolated.
     *
     * **Every** closeable bean is closed, including one a caller-supplied module captured rather
     * than constructed (`single<HttpClient> { myClient }`). Provenance is not recoverable here — by
     * the time [registerInstance] sees it, a captured instance and a freshly constructed one are the
     * same thing — nor one level up, since caller modules arrive through
     * `bootstrapKatalystContainer(additionalModules = ...)` and through
     * `KatalystFeature.provideBeanModules()`, the same channel every framework feature uses.
     * Exempting them would silently re-open this leak for an application that registers its own
     * pool or scheduler through a custom feature; a leak is silent, whereas a shared instance
     * closed too early fails loudly on first use. The contract is therefore: the container owns the
     * lifecycle of everything registered in it. An instance that must outlive the container does
     * not belong in it.
     */
    private fun closeManagedInstances() {
        val pending = synchronized(managedLock) {
            val snapshot = managedInstances.toList()
            managedInstances.clear()
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

    /**
     * Remembers a closeable registration so [stop] can release it.
     *
     * Compared by identity, not equality: one instance is deliberately bound under several types
     * (the database factory is rebound during boot), and it must be closed exactly once.
     */
    private fun trackManagedInstance(instance: Any) {
        if (instance !is AutoCloseable) return
        synchronized(managedLock) {
            if (managedInstances.none { it === instance }) {
                managedInstances += instance
            }
        }
    }

    private fun registerDefinitions(
        modules: List<KatalystBeanModule>,
        container: KatalystContainer,
    ) {
        modules.forEach { module ->
            module.definitions.forEach { definition ->
                val instance = definition.provider(KatalystBeanContext(container))
                registerInstance(
                    instance = instance,
                    primaryType = definition.type,
                    qualifier = definition.qualifier,
                )
            }
        }
    }

    private fun currentKoin(): Koin =
        currentKoinOrNull()
            ?: error(
                "Koin bean engine is not initialized. Bootstrap Katalyst before loading modules " +
                    "or registering instances."
            )

    private fun currentKoinOrNull(): Koin? =
        runCatching { GlobalContext.get() }.getOrNull()
}

/**
 * Registration order, so `getAll` is deterministic.
 *
 * Koin's registry is a `ConcurrentHashMap`, so the order `getAll` returns is hash order: stable
 * within a run, arbitrary between one set of beans and another. Every consumer of a multibinding
 * marker sorts by a declared order (`ReadyHook.order`, `KtorModule.order`) and Kotlin's sort is
 * stable, so ties fall through to whatever `getAll` happened to produce. Arbitrary there means a
 * hook set that can run in a different order in production than it did in the suite that signed it
 * off - and the in-memory test engine returns registration order, so the two engines disagreed
 * about it outright.
 *
 * Registration order is the deterministic answer: it is what a reader expects, it is what the test
 * engine already produced, and it needs no ordering data the engine does not already have.
 *
 * Keyed by identity, and holding the first sequence seen for an instance: one instance rebound
 * under a second type is still one bean and keeps its original place. Entries live until [clear],
 * which `KoinBeanEngine.stop` calls - the registry holds the same instances for exactly as long.
 */
internal object KoinRegistrationOrder {
    private val lock = Any()
    private val firstSeen = IdentityHashMap<Any, Long>()
    private var next = 0L

    /** Allocates this registration's sequence, remembering the instance's first one. */
    fun record(instance: Any): Long = synchronized(lock) {
        val sequence = next++
        if (!firstSeen.containsKey(instance)) firstSeen[instance] = sequence
        sequence
    }

    /** An instance registered outside the engine sorts last, keeping its relative order. */
    fun sequenceOf(instance: Any): Long = synchronized(lock) { firstSeen[instance] ?: Long.MAX_VALUE }

    fun clear(): Unit = synchronized(lock) { firstSeen.clear() }
}
