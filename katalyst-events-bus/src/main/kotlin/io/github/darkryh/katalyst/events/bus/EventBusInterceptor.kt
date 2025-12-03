package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent

/**
 * Extension point for cross-cutting concerns during event publishing.
 *
 * Interceptors allow you to hook into the event publishing pipeline to:
 * - Log events
 * - Collect metrics
 * - Perform tracing
 * - Validate events
 * - Abort publishing based on conditions
 * - Coordinate with external systems (in EventClient layer)
 *
 * **Usage:**
 *
 * ```kotlin
 * class LoggingInterceptor : EventBusInterceptor {
 *     override suspend fun beforePublish(event: DomainEvent): InterceptResult {
 *         logger.info("Publishing event: {}", event.eventType())
 *         return InterceptResult.Continue
 *     }
 *
 *     override suspend fun afterPublish(event: DomainEvent, result: PublishResult) {
 *         logger.info("Event {} published to {} handlers",
 *             event.eventType(),
 *             result.handlersInvoked)
 *     }
 * }
 * ```
 *
 * **Important:**
 * - Interceptors should not throw exceptions
 * - If an interceptor fails, others still run
 * - Interceptors are for observation, not modification
 * - Errors in interceptors are logged but not propagated
 */
interface EventBusInterceptor {
    /**
     * Called before event is published to handlers.
     *
     * Can inspect the event and decide whether to proceed with publishing.
     *
     * **Important:** This is called before handlers, not after.
     * For coordinating with external systems (like EventMessagingPublisher),
     * use afterPublish() or implement as EventClient interceptor instead.
     *
     * @param event The event about to be published
     * @return InterceptResult.Continue to proceed, or InterceptResult.Abort to stop
     */
    suspend fun beforePublish(event: DomainEvent): InterceptResult =
        InterceptResult.Continue

    /**
     * Called after event is published to all handlers.
     *
     * Receives details about handler execution for logging/metrics.
     *
     * **Common Use Cases:**
     * - Log event publishing statistics
     * - Emit metrics/telemetry
     * - Coordinate with external systems (remote publishing)
     * - Perform cleanup
     *
     * @param event The event that was published
     * @param result Detailed information about handler execution
     */
    suspend fun afterPublish(event: DomainEvent, result: PublishResult) {}

    /**
     * Called if any error occurs during publishing.
     *
     * This is the last chance to handle errors before they're logged.
     *
     * @param event The event being published
     * @param error The exception that occurred
     */
    suspend fun onPublishError(event: DomainEvent, error: Throwable) {}
}

/**
 * Decision result from beforePublish interceptor.
 *
 * Allows interceptors to control whether publishing should proceed.
 */
sealed class InterceptResult {
    /**
     * Continue with event publishing.
     *
     * All handlers will be invoked and afterPublish interceptors will run.
     */
    object Continue : InterceptResult()

    /**
     * Abort event publishing.
     *
     * Handlers will NOT be invoked, but afterPublish interceptors still run.
     * This is useful for validation or conditional publishing.
     *
     * @param reason Human-readable reason for aborting
     */
    data class Abort(val reason: String) : InterceptResult()
}

/**
 * Detailed result of publishing an event to handlers.
 *
 * Contains statistics about handler execution for logging/metrics.
 *
 * @param event The event that was published
 * @param handlersInvoked Total number of handlers invoked
 * @param handlersSucceeded Number of handlers that completed successfully
 * @param handlersFailed Number of handlers that threw exceptions
 * @param failures Details of handler failures
 * @param durationMs Time in milliseconds to execute all handlers
 */
data class PublishResult(
    val event: DomainEvent,
    val handlersInvoked: Int = 0,
    val handlersSucceeded: Int = 0,
    val handlersFailed: Int = 0,
    val failures: List<HandlerFailure> = emptyList(),
    val durationMs: Long = 0L
) {
    /**
     * Check if all handlers succeeded.
     */
    fun allSucceeded(): Boolean = handlersFailed == 0

    /**
     * Check if any handlers failed.
     */
    fun hasFailed(): Boolean = handlersFailed > 0

    /**
     * Get success rate as percentage.
     */
    fun successRate(): Double = if (handlersInvoked == 0) {
        100.0
    } else {
        (handlersSucceeded.toDouble() / handlersInvoked.toDouble()) * 100.0
    }
}

/**
 * Details of a handler execution failure.
 *
 * @param handlerClass Fully qualified class name of the handler
 * @param exception The exception that was thrown
 */
data class HandlerFailure(
    val handlerClass: String,
    val exception: Throwable
)
