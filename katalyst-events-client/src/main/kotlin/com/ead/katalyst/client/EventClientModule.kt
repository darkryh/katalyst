package com.ead.katalyst.client

import org.koin.core.module.Module
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

    // Create composite interceptor from config + provided interceptors
    single<EventClientInterceptor> {
        val allInterceptors = config.interceptors + interceptors
        if (allInterceptors.isNotEmpty()) {
            CompositeEventClientInterceptor(allInterceptors)
        } else {
            NoOpEventClientInterceptor()
        }
    }

    // Register configuration as singleton
    single<EventClientConfiguration> { config }

    // Register EventClient singleton
    single<EventClient> {
        val eventBus = getOrNull<com.ead.katalyst.events.bus.EventBus>()
        val validator = getOrNull<com.ead.katalyst.events.EventValidator<com.ead.katalyst.events.DomainEvent>>()
        val serializer = getOrNull<com.ead.katalyst.events.transport.EventSerializer>()
        val router = getOrNull<com.ead.katalyst.events.transport.EventRouter>()
        val clientInterceptor = get<EventClientInterceptor>()

        // Extract interceptors from composite if applicable
        val interceptorList = when (clientInterceptor) {
            is CompositeEventClientInterceptor -> {
                // Access private list through reflection if needed, or just wrap in list
                listOf(clientInterceptor)
            }
            else -> listOf(clientInterceptor)
        }

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
        object : EventClientBuilder {
            private var retryPolicy: RetryPolicy = config.retryPolicy
            private val interceptorList = mutableListOf<EventClientInterceptor>()
            private var publishToLocalBus = config.publishToLocalBus
            private var publishToExternal = config.publishToExternal
            private var correlationId = config.correlationId
            private var maxBatchSize = config.batchSize
            private var flushIntervalMs = config.batchFlushIntervalMs

            override fun retryPolicy(policy: RetryPolicy): EventClientBuilder {
                this.retryPolicy = policy
                return this
            }

            override fun addInterceptor(interceptor: EventClientInterceptor): EventClientBuilder {
                interceptorList.add(interceptor)
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
                val eventBus = getOrNull<com.ead.katalyst.events.bus.EventBus>()
                val validator = getOrNull<com.ead.katalyst.events.EventValidator<com.ead.katalyst.events.DomainEvent>>()
                val serializer = getOrNull<com.ead.katalyst.events.transport.EventSerializer>()
                val router = getOrNull<com.ead.katalyst.events.transport.EventRouter>()

                return DefaultEventClient(
                    eventBus = eventBus,
                    validator = validator,
                    serializer = serializer,
                    router = router,
                    retryPolicy = retryPolicy,
                    interceptors = interceptorList.toList(),
                    publishToLocalBus = publishToLocalBus,
                    publishToExternal = publishToExternal,
                    correlationId = correlationId,
                    maxBatchSize = maxBatchSize,
                    flushIntervalMs = flushIntervalMs
                )
            }
        }
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
