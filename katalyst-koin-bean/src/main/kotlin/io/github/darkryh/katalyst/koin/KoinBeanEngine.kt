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

        val container = KoinKatalystContainer(koin).also(KatalystContainerProvider::set)
        registerDefinitions(modules, container)
        return container
    }

    override fun loadModules(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ) {
        registerDefinitions(modules, KoinKatalystContainer(currentKoin()))
    }

    @OptIn(KoinInternalApi::class)
    override fun registerInstance(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>,
        qualifier: String?,
    ) {
        trackManagedInstance(instance)
        val koin = currentKoin()
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
     * Two displacements are legitimate: the displaced definition keeps another key, or the
     * write re-declares the very same type — `BeanDefinition.equals` is primary type, qualifier
     * and scope — which is the module DSL's own replace semantics. Anything else means a
     * definition was dropped as a side effect of sharing a marker, and should be impossible
     * given the identity key from [indexedSecondaryTypes].
     */
    @OptIn(KoinInternalApi::class)
    private fun reportDisplacement(koin: Koin, key: String, factory: InstanceFactory<*>) {
        val displaced = koin.instanceRegistry.instances[key] ?: return
        if (displaced === factory) return

        val replacesSameDefinition = displaced.beanDefinition == factory.beanDefinition
        if (replacesSameDefinition || isReachableWithout(koin, displaced, key)) {
            logger.debug(
                "Rebound bean index '{}' from {} to {}",
                key,
                displaced.beanDefinition,
                factory.beanDefinition,
            )
        } else {
            logger.warn(
                "Bean index '{}' was the last key of {}; {} takes it and the displaced definition " +
                    "is no longer resolvable. Every registration must keep an index key of its own.",
                key,
                displaced.beanDefinition,
                factory.beanDefinition,
            )
        }
    }

    /** Whether [factory] still owns an index key other than [writtenKey]. */
    @OptIn(KoinInternalApi::class)
    private fun isReachableWithout(koin: Koin, factory: InstanceFactory<*>, writtenKey: String): Boolean {
        val definition = factory.beanDefinition
        return (listOf(definition.primaryType) + definition.secondaryTypes).any { type ->
            val key = indexKey(type, definition.qualifier, definition.scopeQualifier)
            key != writtenKey && koin.instanceRegistry.instances[key] === factory
        }
    }

    override fun currentOrNull(): KatalystContainer? =
        currentKoinOrNull()?.let(::KoinKatalystContainer)

    override fun stop() {
        try {
            closeManagedInstances()
        } finally {
            try {
                runCatching { stopKoin() }
            } finally {
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
