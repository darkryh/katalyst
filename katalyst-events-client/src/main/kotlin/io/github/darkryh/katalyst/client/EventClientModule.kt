package io.github.darkryh.katalyst.client

import io.github.darkryh.katalyst.client.config.EventClientConfiguration
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.events.transport.routing.EventRouter
import io.github.darkryh.katalyst.events.transport.serialization.EventSerializer
import io.github.darkryh.katalyst.events.validation.EventValidator
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Koin DI module for katalyst-events-client.
 *
 * Registers EventClient and all dependencies.
 * Automatically integrates with EventBus, serialization, and routing from other modules.
 *
 * **Usage:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         eventsCoreModule,
 *         eventsBusModule,
 *         eventsTransportModule,
 *         eventsClientModule()
 *     )
 * }
 *
 * // Later in the app:
 * val client: EventClient = get()
 * ```
 *
 * **With Custom Configuration:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         eventsCoreModule,
 *         eventsBusModule,
 *         eventsTransportModule,
 *         eventsClientModule(
 *             config = EventClientConfiguration.highReliability(),
 *             interceptors = listOf(MetricsInterceptor(), LoggingInterceptor())
 *         )
 *     )
 * }
 * ```
 */
fun eventsClientModule(
    config: EventClientConfiguration = EventClientConfiguration.default(),
    interceptors: List<EventClientInterceptor> = emptyList()
): Module = module {
    val logger = LoggerFactory.getLogger("EventClientModule")

    // Validate configuration
    config.validate()
    logger.info("Configuring EventClient with: retryPolicy=${config.retryPolicy::class.simpleName}, " +
            "localBus=${config.publishToLocalBus}, external=${config.publishToExternal}, " +
            "batchSize=${config.batchSize}")

    single<EventClientInterceptorRegistry> {
        DefaultEventClientInterceptorRegistry().apply {
            registerAll(GlobalEventClientInterceptorRegistry.consumeAll())
        }
    }

    // Register configuration as singleton
    single<EventClientConfiguration> { config }

    // Register EventClient singleton
    single<EventClient> {
        val eventBus = getOrNull<io.github.darkryh.katalyst.events.bus.EventBus>()
        val validator = getOrNull<EventValidator<DomainEvent>>()
        val serializer = getOrNull<EventSerializer>()
        val router = getOrNull<EventRouter>()
        val registry = get<EventClientInterceptorRegistry>()
        val discoveredInterceptors = getKoin().getAll<EventClientInterceptor>()
        if (discoveredInterceptors.isNotEmpty()) {
            registry.registerAll(discoveredInterceptors)
        }

        val configuredInterceptors = config.interceptors + interceptors
        val interceptorList = (registry.getAll() + configuredInterceptors)
            .ifEmpty { listOf(NoOpEventClientInterceptor()) }

        DefaultEventClient(
            eventBus = eventBus,
            validator = validator,
            serializer = serializer,
            router = router,
            retryPolicy = config.retryPolicy,
            interceptors = interceptorList,
            publishToLocalBus = config.publishToLocalBus,
            publishToExternal = config.publishToExternal,
            correlationId = config.correlationId,
            maxBatchSize = config.batchSize,
            flushIntervalMs = config.batchFlushIntervalMs
        )
            .also {
                logger.info("EventClient initialized successfully")
            }
    }

    // Expose builder factory for runtime creation
    factory<EventClientBuilder> {
        KoinEventClientBuilder(
            scope = this,
            baseConfig = config
        )
    }
}

/**
 * Extension function for Koin to load events client module.
 *
 * **Usage:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         // ... other modules ...
 *         eventsClientModule()
 *     )
 * }
 * ```
 */
fun eventsClientModuleDefault(): Module = eventsClientModule()

private class KoinEventClientBuilder(
    private val scope: Scope,
    baseConfig: EventClientConfiguration
) : EventClientBuilder {
    private var retryPolicy: RetryPolicy = baseConfig.retryPolicy
    private val manualInterceptors = mutableListOf<EventClientInterceptor>()
    private var publishToLocalBus = baseConfig.publishToLocalBus
    private var publishToExternal = baseConfig.publishToExternal
    private var correlationId = baseConfig.correlationId
    private var maxBatchSize = baseConfig.batchSize
    private var flushIntervalMs = baseConfig.batchFlushIntervalMs

    override fun retryPolicy(policy: RetryPolicy): EventClientBuilder {
        this.retryPolicy = policy
        return this
    }

    override fun addInterceptor(interceptor: EventClientInterceptor): EventClientBuilder {
        manualInterceptors.add(interceptor)
        return this
    }

    override fun publishToLocalBus(enabled: Boolean): EventClientBuilder {
        this.publishToLocalBus = enabled
        return this
    }

    override fun publishToExternal(enabled: Boolean): EventClientBuilder {
        this.publishToExternal = enabled
        return this
    }

    override fun correlationId(correlationId: String): EventClientBuilder {
        this.correlationId = correlationId
        return this
    }

    override fun batchConfiguration(maxBatchSize: Int, flushIntervalMs: Long): EventClientBuilder {
        this.maxBatchSize = maxBatchSize
        this.flushIntervalMs = flushIntervalMs
        return this
    }

    override fun build(): EventClient {
        val eventBus = scope.getOrNull<EventBus>()
        val validator = scope.getOrNull<EventValidator<DomainEvent>>()
        val serializer = scope.getOrNull<EventSerializer>()
        val router = scope.getOrNull<EventRouter>()
        val registry = scope.getOrNull<EventClientInterceptorRegistry>()
        val registeredInterceptors = registry?.getAll().orEmpty()
        val discoveredInterceptors = scope.getKoin().getAll<EventClientInterceptor>()
        val combined = (registeredInterceptors + discoveredInterceptors + manualInterceptors)
            .ifEmpty { listOf(NoOpEventClientInterceptor()) }

        return DefaultEventClient(
            eventBus = eventBus,
            validator = validator,
            serializer = serializer,
            router = router,
            retryPolicy = retryPolicy,
            interceptors = combined,
            publishToLocalBus = publishToLocalBus,
            publishToExternal = publishToExternal,
            correlationId = correlationId,
            maxBatchSize = maxBatchSize,
            flushIntervalMs = flushIntervalMs
        )
    }
}

/**
 * Preset configurations for common use cases.
 */
object EventClientPresets {
    /**
     * High-throughput configuration module.
     */
    fun highThroughput(): Module = eventsClientModule(
        config = EventClientConfiguration.highThroughput()
    )

    /**
     * High-reliability configuration module.
     */
    fun highReliability(): Module = eventsClientModule(
        config = EventClientConfiguration.highReliability()
    )

    /**
     * Local-only (no external messaging) configuration module.
     */
    fun localOnly(): Module = eventsClientModule(
        config = EventClientConfiguration.localOnly()
    )

    /**
     * External-only (no local bus) configuration module.
     */
    fun externalOnly(): Module = eventsClientModule(
        config = EventClientConfiguration.externalOnly()
    )
}
