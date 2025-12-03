package io.github.darkryh.katalyst.client

import io.github.darkryh.katalyst.events.DomainEvent

/**
 * Hooks for intercepting and extending EventClient operations.
 *
 * Allows cross-cutting concerns like:
 * - Logging and auditing
 * - Metrics collection
 * - Request/response transformation
 * - Security checks
 * - Performance monitoring
 *
 * **Implementation:**
 *
 * ```kotlin
 * class MetricsInterceptor : EventClientInterceptor {
 *     override suspend fun beforePublish(
 *         event: DomainEvent,
 *         context: PublishContext
 *     ): InterceptResult {
 *         metrics.increment("event.publish.attempt", event.eventType())
 *         return InterceptResult.Continue
 *     }
 *
 *     override suspend fun afterPublish(
 *         event: DomainEvent,
 *         result: PublishResult,
 *         context: PublishContext
 *     ) {
 *         when (result) {
 *             is PublishResult.Success -> metrics.increment("event.publish.success")
 *             is PublishResult.Failure -> metrics.increment("event.publish.failure")
 *             else -> {}
 *         }
 *     }
 * }
 * ```
 *
 * **Registration:**
 *
 * ```kotlin
 * val client = EventClient.builder()
 *     .addInterceptor(MetricsInterceptor())
 *     .addInterceptor(LoggingInterceptor())
 *     .build()
 * ```
 */
interface EventClientInterceptor {
    /**
     * Hook executed before event publication.
     *
     * Can inspect event, modify context, or abort publication.
     *
     * @param event The event being published
     * @param context Publication context (destination, routing, etc.)
     * @return InterceptResult.Continue to proceed, or InterceptResult.Abort to stop
     */
    suspend fun beforePublish(
        event: DomainEvent,
        context: PublishContext
    ): InterceptResult = InterceptResult.Continue

    /**
     * Hook executed after successful or failed publication.
     *
     * Useful for logging, metrics, cleanup, etc.
     *
     * @param event The event that was published
     * @param result The outcome of publication
     * @param context Publication context
     * @param durationMs Time spent on publication
     */
    suspend fun afterPublish(
        event: DomainEvent,
        result: PublishResult,
        context: PublishContext,
        durationMs: Long = 0
    ) {}

    /**
     * Hook executed when publication error occurs.
     *
     * Can log, transform error, decide on retry behavior, etc.
     *
     * @param event The event that failed to publish
     * @param exception The exception that occurred
     * @param context Publication context
     * @param attemptNumber Which attempt this was (1-based)
     * @return ErrorHandling.Retry to retry, or ErrorHandling.Stop to fail
     */
    suspend fun onPublishError(
        event: DomainEvent,
        exception: Throwable,
        context: PublishContext,
        attemptNumber: Int = 1
    ): ErrorHandling = ErrorHandling.Stop

    /**
     * Result of interceptor operation.
     */
    sealed class InterceptResult {
        /**
         * Continue with normal processing.
         */
        object Continue : InterceptResult()

        /**
         * Abort current operation and return failure.
         *
         * @param reason Explanation for abort
         */
        data class Abort(val reason: String) : InterceptResult()
    }

    /**
     * How to handle a publish error.
     */
    sealed class ErrorHandling {
        /**
         * Stop publishing and fail.
         */
        object Stop : ErrorHandling()

        /**
         * Attempt to retry the publish.
         *
         * @param delayMs Milliseconds to wait before retry (0 = immediate)
         */
        data class Retry(val delayMs: Long = 0) : ErrorHandling()

        /**
         * Skip this event and continue with next (for batch operations).
         */
        object Skip : ErrorHandling()
    }

    /**
     * Context about an event being published.
     *
     * @param eventId Unique identifier for the event
     * @param eventType Type string of the event
     * @param destination Message broker destination
     * @param retryPolicy Retry configuration in use
     * @param metadata Additional context metadata
     */
    data class PublishContext(
        val eventId: String,
        val eventType: String,
        val destination: String? = null,
        val retryPolicy: RetryPolicy? = null,
        val metadata: Map<String, String> = emptyMap()
    ) {
        /**
         * Add or update metadata.
         */
        fun withMetadata(key: String, value: String): PublishContext =
            copy(metadata = metadata + (key to value))

        /**
         * Get metadata value.
         */
        fun getMetadata(key: String): String? = metadata[key]
    }
}

/**
 * Composite interceptor that chains multiple interceptors.
 *
 * Executes beforePublish in order, afterPublish in reverse order.
 *
 * @param interceptors List of interceptors to chain
 */
class CompositeEventClientInterceptor(
    private val interceptors: List<EventClientInterceptor> = emptyList()
) : EventClientInterceptor {
    override suspend fun beforePublish(
        event: DomainEvent,
        context: EventClientInterceptor.PublishContext
    ): EventClientInterceptor.InterceptResult {
        for (interceptor in interceptors) {
            when (val result = interceptor.beforePublish(event, context)) {
                is EventClientInterceptor.InterceptResult.Abort -> return result
                else -> {} // Continue
            }
        }
        return EventClientInterceptor.InterceptResult.Continue
    }

    override suspend fun afterPublish(
        event: DomainEvent,
        result: PublishResult,
        context: EventClientInterceptor.PublishContext,
        durationMs: Long
    ) {
        // Execute in reverse order
        for (interceptor in interceptors.asReversed()) {
            interceptor.afterPublish(event, result, context, durationMs)
        }
    }

    override suspend fun onPublishError(
        event: DomainEvent,
        exception: Throwable,
        context: EventClientInterceptor.PublishContext,
        attemptNumber: Int
    ): EventClientInterceptor.ErrorHandling {
        for (interceptor in interceptors) {
            when (val handling = interceptor.onPublishError(event, exception, context, attemptNumber)) {
                is EventClientInterceptor.ErrorHandling.Stop -> return handling
                is EventClientInterceptor.ErrorHandling.Retry -> return handling
                else -> {} // Continue to next
            }
        }
        return EventClientInterceptor.ErrorHandling.Stop
    }

    /**
     * Add an interceptor to the chain.
     */
    fun add(interceptor: EventClientInterceptor): CompositeEventClientInterceptor =
        CompositeEventClientInterceptor(interceptors + interceptor)
}

/**
 * No-op interceptor that does nothing.
 *
 * Useful as default or for testing.
 */
class NoOpEventClientInterceptor : EventClientInterceptor
