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
import org.koin.core.instance.SingleInstanceFactory
import org.koin.core.qualifier.named
import kotlin.reflect.KClass

class KoinKatalystBeanEngine : KatalystBeanEngine {
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
        val koin = currentKoin()
        val scopeQualifier = koin.scopeRegistry.rootScope.scopeQualifier
        val koinQualifier = qualifier?.let(::named)
        val definition = BeanDefinition(
            scopeQualifier = scopeQualifier,
            primaryType = primaryType,
            qualifier = koinQualifier,
            definition = { instance },
            kind = Kind.Singleton,
            secondaryTypes = secondaryTypes,
        )
        val factory = SingleInstanceFactory(definition)
        val primaryKey = indexKey(primaryType, definition.qualifier, scopeQualifier)

        runCatching {
            koin.instanceRegistry.saveMapping(true, primaryKey, factory, logWarning = false)
            secondaryTypes.forEach { type ->
                val key = indexKey(type, definition.qualifier, scopeQualifier)
                koin.instanceRegistry.saveMapping(true, key, factory, logWarning = false)
            }
        }.onFailure { error ->
            if (error !is DefinitionOverrideException) {
                throw error
            }
        }
    }

    override fun currentOrNull(): KatalystContainer? =
        currentKoinOrNull()?.let(::KoinKatalystContainer)

    override fun stop() {
        try {
            runCatching { stopKoin() }
        } finally {
            KatalystContainerProvider.reset()
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
    private val delegate = KoinKatalystBeanEngine()

    override val id: String = delegate.id

    override fun start(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ): KatalystContainer =
        delegate.start(modules, allowOverrides)

    override fun loadModules(
        modules: List<KatalystBeanModule>,
        allowOverrides: Boolean,
    ) {
        delegate.loadModules(modules, allowOverrides)
    }

    override fun registerInstance(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>,
        qualifier: String?,
    ) {
        delegate.registerInstance(instance, primaryType, secondaryTypes, qualifier)
    }

    override fun currentOrNull(): KatalystContainer? =
        delegate.currentOrNull()

    override fun stop() {
        delegate.stop()
    }
}
