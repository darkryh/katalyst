package com.ead.katalyst.client

import com.ead.katalyst.events.DomainEvent

/**
 * Public API for publishing domain events.
 *
 * Integrates the entire event system: validation, bus, serialization, routing.
 *
 * Provides:
 * - Type-safe event publishing
 * - Automatic serialization for external messaging
 * - Retry strategies for resilience
 * - Extensibility through interceptors
 * - Comprehensive error handling
 *
 * **Responsibilities:**
 * - Validate event before publish
 * - Publish to local EventBus for internal handlers
 * - Serialize event for external messaging systems
 * - Route to appropriate destination
 * - Handle failures and retries
 * - Execute interceptor hooks
 *
 * **Usage:**
 *
 * ```kotlin
 * val client = EventClient.builder()
 *     .retryPolicy(RetryPolicy.exponentialBackoff(maxAttempts = 3))
 *     .addInterceptor(MetricsInterceptor())
 *     .build()
 *
 * val event = UserCreatedEvent(...)
 * val result = client.publish(event)
 *
 * when (result) {
 *     is PublishResult.Success -> println("Published: ${result.eventId}")
 *     is PublishResult.Failure -> logger.error("Failed: ${result.reason}")
 * }
 * ```
 *
 * **Batch Publishing:**
 *
 * ```kotlin
 * val events = listOf(
 *     UserCreatedEvent(...),
 *     UserCreatedEvent(...),
 *     UserCreatedEvent(...)
 * )
 * val result = client.publishBatch(events)
 * // Returns PublishResult.Partial with success/failure counts
 * ```
 */
interface EventClient {
    /**
     * Publish a single domain event.
     *
     * Process:
     * 1. Execute beforePublish interceptors
     * 2. Validate event
     * 3. Publish to local EventBus (async for internal handlers)
     * 4. Serialize event
     * 5. Route to destination
     * 6. Publish to external messaging system (with retries)
     * 7. Execute afterPublish interceptors
     *
     * @param event The event to publish
     * @return PublishResult indicating success or failure
     * @throws com.ead.katalyst.client.exception.ClientException for unrecoverable errors
     */
    suspend fun publish(event: DomainEvent): PublishResult

    /**
     * Publish multiple events in a batch.
     *
     * Attempts to publish each event, collecting results.
     * Failure of one event does not affect others.
     *
     * @param events List of events to publish
     * @return PublishResult.Partial with aggregate results
     */
    suspend fun publishBatch(events: List<DomainEvent>): PublishResult.Partial

    /**
     * Publish event and get metadata about internal delivery.
     *
     * Waits for internal EventBus handlers to complete.
     *
     * @param event The event to publish
     * @return Result with handler execution details
     */
    suspend fun publishWithDeliveryInfo(event: DomainEvent): DeliveryInfo

    /**
     * Information about event delivery to handlers.
     *
     * @param publishResult Result of external publish (null if local only)
     * @param handlerCount Number of handlers executed
     * @param handlerErrors List of handler execution errors
     * @param totalDurationMs Total time for entire publish operation
     * @param busPublishMs Time to publish to internal bus
     * @param externalPublishMs Time to publish externally (if applicable)
     */
    data class DeliveryInfo(
        val publishResult: PublishResult? = null,
        val handlerCount: Int = 0,
        val handlerErrors: List<Throwable> = emptyList(),
        val totalDurationMs: Long = 0,
        val busPublishMs: Long = 0,
        val externalPublishMs: Long = 0
    ) {
        /**
         * Check if handlers executed successfully.
         */
        fun handlersSuccessful(): Boolean = handlerErrors.isEmpty()

        /**
         * Overall success considering handlers and external publish.
         */
        fun isSuccessful(): Boolean =
            handlersSuccessful() && (publishResult == null || publishResult.isSuccess())
    }

    companion object {
        /**
         * Create a builder for EventClient configuration.
         *
         * @return EventClientBuilder for fluent configuration
         */
        fun builder(): EventClientBuilder = DefaultEventClientBuilder()
    }
}

/**
 * Builder for configuring EventClient instances.
 *
 * **Example:**
 *
 * ```kotlin
 * val client = EventClient.builder()
 *     .retryPolicy(RetryPolicy.exponentialBackoff(
 *         initialDelayMs = 100,
 *         maxDelayMs = 30000,
 *         maxAttempts = 5
 *     ))
 *     .addInterceptor(LoggingInterceptor())
 *     .addInterceptor(MetricsInterceptor())
 *     .build()
 * ```
 */
interface EventClientBuilder {
    /**
     * Set the retry policy for failed publishes.
     *
     * @param policy RetryPolicy to use (default: no retries)
     * @return This builder for chaining
     */
    fun retryPolicy(policy: RetryPolicy): EventClientBuilder

    /**
     * Add an interceptor for extending client behavior.
     *
     * @param interceptor EventClientInterceptor to add
     * @return This builder for chaining
     */
    fun addInterceptor(interceptor: EventClientInterceptor): EventClientBuilder

    /**
     * Add multiple interceptors at once.
     *
     * @param interceptors List of interceptors to add
     * @return This builder for chaining
     */
    fun addInterceptors(interceptors: List<EventClientInterceptor>): EventClientBuilder {
        interceptors.forEach { addInterceptor(it) }
        return this
    }

    /**
     * Enable or disable local event bus publishing.
     *
     * When enabled (default), events are published to EventBus for local handlers.
     * Can be disabled for external-only publishing.
     *
     * @param enabled Whether to publish to local bus
     * @return This builder for chaining
     */
    fun publishToLocalBus(enabled: Boolean): EventClientBuilder

    /**
     * Enable or disable external messaging publishing.
     *
     * When enabled (default), events are serialized and routed to external destinations.
     * Can be disabled for local-only publishing.
     *
     * @param enabled Whether to publish externally
     * @return This builder for chaining
     */
    fun publishToExternal(enabled: Boolean): EventClientBuilder

    /**
     * Set correlation ID for request tracing.
     *
     * Applied to all published events to trace request flow.
     *
     * @param correlationId ID to use for correlation
     * @return This builder for chaining
     */
    fun correlationId(correlationId: String): EventClientBuilder

    /**
     * Set batch processing mode.
     *
     * @param maxBatchSize Maximum events per batch
     * @param flushIntervalMs Milliseconds between batch flushes
     * @return This builder for chaining
     */
    fun batchConfiguration(maxBatchSize: Int, flushIntervalMs: Long): EventClientBuilder

    /**
     * Build the configured EventClient.
     *
     * @return Ready-to-use EventClient instance
     * @throws com.ead.katalyst.client.exception.ClientConfigurationException if configuration is invalid
     */
    fun build(): EventClient
}

/**
 * Default implementation of EventClientBuilder.
 *
 * Internal use only - use EventClient.builder() to create instances.
 */
private class DefaultEventClientBuilder : EventClientBuilder {
    private var retryPolicy: RetryPolicy = RetryPolicy.noRetry()
    private val interceptors = mutableListOf<EventClientInterceptor>()
    private var publishToLocalBus = true
    private var publishToExternal = true
    private var correlationId: String? = null
    private var maxBatchSize: Int = 100
    private var flushIntervalMs: Long = 1000

    override fun retryPolicy(policy: RetryPolicy): EventClientBuilder {
        this.retryPolicy = policy
        return this
    }

    override fun addInterceptor(interceptor: EventClientInterceptor): EventClientBuilder {
        interceptors.add(interceptor)
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
        return DefaultEventClient(
            retryPolicy = retryPolicy,
            interceptors = interceptors.toList(),
            publishToLocalBus = publishToLocalBus,
            publishToExternal = publishToExternal,
            correlationId = correlationId,
            maxBatchSize = maxBatchSize,
            flushIntervalMs = flushIntervalMs
        )
    }
}
