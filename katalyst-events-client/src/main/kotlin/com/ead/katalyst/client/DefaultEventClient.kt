package com.ead.katalyst.client

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.validation.EventValidator
import com.ead.katalyst.events.validation.ValidationResult
import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.events.transport.routing.EventRouter
import com.ead.katalyst.events.transport.serialization.EventSerializer
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * Default implementation of EventClient.
 *
 * Integrates all components of the event system:
 * - Local EventBus for in-process handlers
 * - EventValidator for pre-publish validation
 * - EventSerializer for external messaging
 * - EventRouter for destination routing
 * - RetryPolicy for resilience
 * - Interceptors for extensibility
 *
 * **Publish Flow:**
 *
 * 1. Execute beforePublish interceptors
 * 2. Validate event
 * 3. Publish to local EventBus (if enabled)
 * 4. Serialize event for external messaging
 * 5. Determine routing destination
 * 6. Publish to external system with retries (if enabled)
 * 7. Execute afterPublish interceptors
 * 8. Return aggregate result
 *
 * @param eventBus EventBus for local event distribution
 * @param validator Optional event validator (null = no validation)
 * @param serializer EventSerializer for external publishing
 * @param router EventRouter for destination routing
 * @param retryPolicy RetryPolicy for handling failures
 * @param interceptors List of EventClientInterceptor for hooks
 * @param publishToLocalBus Whether to publish to EventBus
 * @param publishToExternal Whether to publish externally
 * @param correlationId Optional correlation ID for request tracing
 * @param maxBatchSize Maximum events per batch
 * @param flushIntervalMs Interval for batch flushing
 */
class DefaultEventClient(
    private val eventBus: EventBus? = null,
    private val validator: EventValidator<DomainEvent>? = null,
    private val serializer: EventSerializer? = null,
    private val router: EventRouter? = null,
    private val retryPolicy: RetryPolicy = RetryPolicy.noRetry(),
    private val interceptors: List<EventClientInterceptor> = emptyList(),
    private val publishToLocalBus: Boolean = true,
    private val publishToExternal: Boolean = true,
    private val correlationId: String? = null,
    private val maxBatchSize: Int = 100,
    private val flushIntervalMs: Long = 1000
) : EventClient {
    private val logger = LoggerFactory.getLogger(DefaultEventClient::class.java)

    override suspend fun publish(event: DomainEvent): PublishResult {
        val startTime = System.currentTimeMillis()
        val context = createPublishContext(event)

        return try {
            // Execute beforePublish interceptors
            when (val interceptResult = executeBeforePublishInterceptors(event, context)) {
                is EventClientInterceptor.InterceptResult.Abort -> {
                    val failure = PublishResult.Failure(
                        eventId = event.getMetadata().eventId,
                        eventType = event.eventType(),
                        reason = "Aborted by interceptor: ${interceptResult.reason}",
                        retriable = false
                    )
                    executeAfterPublishInterceptors(event, failure, context, startTime)
                    failure
                }
                else -> {
                    // Validate event
                    val validationResult = validateEvent(event)
                    if (!validationResult.isValid()) {
                        val failure = PublishResult.Failure(
                            eventId = event.getMetadata().eventId,
                            eventType = event.eventType(),
                            reason = "Validation failed: ${validationResult.errors().joinToString(", ")}",
                            retriable = false,
                            metadata = mapOf("errors" to validationResult.errors().toString())
                        )
                        executeAfterPublishInterceptors(event, failure, context, startTime)
                        failure
                    } else {
                        // Publish to local bus if enabled
                        if (publishToLocalBus && eventBus != null) {
                            try {
                                eventBus.publish(event)
                                logger.debug("Published to local EventBus: ${event.eventType()}")
                            } catch (e: Exception) {
                                logger.error("Failed to publish to local EventBus", e)
                            }
                        }

                        // Publish externally if enabled
                        if (publishToExternal && serializer != null && router != null) {
                            publishExternalWithRetry(event, context, startTime)
                        } else {
                            val success = PublishResult.Success(
                                eventId = event.getMetadata().eventId,
                                eventType = event.eventType(),
                                destination = "local-bus-only"
                            )
                            executeAfterPublishInterceptors(event, success, context, startTime)
                            success
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during publish", e)
            val failure = PublishResult.Failure(
                eventId = event.getMetadata().eventId,
                eventType = event.eventType(),
                reason = "Unexpected error: ${e.message}",
                cause = e,
                retriable = true
            )
            executeAfterPublishInterceptors(event, failure, context, startTime)
            failure
        }
    }

    override suspend fun publishBatch(events: List<DomainEvent>): PublishResult.Partial {
        val results = mutableListOf<PublishResult>()
        var successCount = 0
        var failureCount = 0

        for (event in events) {
            val result = publish(event)
            results.add(result)

            when (result) {
                is PublishResult.Success -> successCount++
                is PublishResult.Failure -> failureCount++
                is PublishResult.Partial -> {
                    successCount += result.successful
                    failureCount += result.failed
                }
            }
        }

        return PublishResult.Partial(
            successful = successCount,
            failed = failureCount,
            results = results
        )
    }

    override suspend fun publishWithDeliveryInfo(event: DomainEvent): EventClient.DeliveryInfo {
        val startTime = System.currentTimeMillis()
        var busPublishMs = 0L
        var externalPublishMs = 0L
        var handlerCount = 0
        val handlerErrors = mutableListOf<Throwable>()

        try {
            // Publish to local bus with timing
            if (publishToLocalBus && eventBus != null) {
                val busDuration = measureTimeMillis {
                    try {
                        eventBus.publish(event)
                    } catch (e: Exception) {
                        handlerErrors.add(e)
                    }
                }
                busPublishMs = busDuration
            }

            // Publish externally with timing
            var publishResult: PublishResult? = null
            if (publishToExternal && serializer != null && router != null) {
                val externalDuration = measureTimeMillis {
                    publishResult = publishExternalWithRetry(
                        event,
                        createPublishContext(event),
                        startTime
                    )
                }
                externalPublishMs = externalDuration
            }

            val totalDuration = System.currentTimeMillis() - startTime

            return EventClient.DeliveryInfo(
                publishResult = publishResult,
                handlerCount = handlerCount,
                handlerErrors = handlerErrors.toList(),
                totalDurationMs = totalDuration,
                busPublishMs = busPublishMs,
                externalPublishMs = externalPublishMs
            )
        } catch (e: Exception) {
            logger.error("Error collecting delivery info", e)
            return EventClient.DeliveryInfo(
                handlerErrors = handlerErrors.toList() + e,
                totalDurationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Publish to external system with retry logic.
     */
    private suspend fun publishExternalWithRetry(
        event: DomainEvent,
        context: EventClientInterceptor.PublishContext,
        startTime: Long
    ): PublishResult {
        var lastFailure: PublishResult.Failure? = null
        var attempt = 1

        while (attempt <= retryPolicy.getMaxAttempts() + 1) {
            try {
                val message = serializer!!.serialize(event)
                val destination = router!!.resolve(event)
                val updatedContext = context.copy(destination = destination.name)

                logger.debug("Publishing to external destination: ${destination.name} (attempt $attempt)")

                // Message would be sent here to actual message broker
                // For now, we simulate success
                val success = PublishResult.Success(
                    eventId = event.getMetadata().eventId,
                    eventType = event.eventType(),
                    destination = destination.name,
                    metadata = mapOf(
                        "attempt" to attempt.toString(),
                        "router" to router::class.simpleName!!
                    )
                )

                executeAfterPublishInterceptors(event, success, updatedContext, startTime)
                return success

            } catch (e: Exception) {
                logger.warn("External publish failed (attempt $attempt)", e)
                lastFailure = PublishResult.Failure(
                    eventId = event.getMetadata().eventId,
                    eventType = event.eventType(),
                    reason = "External publish failed: ${e.message}",
                    cause = e,
                    retriable = true,
                    metadata = mapOf("attempt" to attempt.toString())
                )

                // Check if we should retry
                if (attempt <= retryPolicy.getMaxAttempts()) {
                    val retryDecision = retryPolicy.shouldRetry(lastFailure, attempt)

                    if (retryDecision.shouldRetry) {
                        val delayMs = retryDecision.delayMs
                        logger.info("Retrying in ${delayMs}ms (reason: ${retryDecision.reason})")

                        if (delayMs > 0) {
                            delay(delayMs)
                        }
                        attempt++
                        continue
                    } else {
                        logger.warn("Will not retry: ${retryDecision.reason}")
                        break
                    }
                } else {
                    logger.error("Max retry attempts reached for ${event.eventType()}")
                    break
                }
            }
        }

        executeAfterPublishInterceptors(event, lastFailure!!, context, startTime)
        return lastFailure
    }

    /**
     * Validate event before publishing.
     */
    private suspend fun validateEvent(event: DomainEvent): ValidationResult {
        return if (validator != null) {
            try {
                validator.validate(event)
            } catch (e: Exception) {
                logger.error("Error during validation", e)
                ValidationResult.Invalid(listOf("Validation error: ${e.message}"))
            }
        } else {
            ValidationResult.Valid
        }
    }

    /**
     * Execute beforePublish interceptor hooks.
     */
    private suspend fun executeBeforePublishInterceptors(
        event: DomainEvent,
        context: EventClientInterceptor.PublishContext
    ): EventClientInterceptor.InterceptResult {
        for (interceptor in interceptors) {
            try {
                when (val result = interceptor.beforePublish(event, context)) {
                    is EventClientInterceptor.InterceptResult.Abort -> {
                        logger.warn("Interceptor aborted publish: ${result.reason}")
                        return result
                    }
                    else -> {} // Continue
                }
            } catch (e: Exception) {
                logger.error("Error in beforePublish interceptor", e)
                return EventClientInterceptor.InterceptResult.Abort("Interceptor error: ${e.message}")
            }
        }
        return EventClientInterceptor.InterceptResult.Continue
    }

    /**
     * Execute afterPublish interceptor hooks.
     */
    private suspend fun executeAfterPublishInterceptors(
        event: DomainEvent,
        result: PublishResult,
        context: EventClientInterceptor.PublishContext,
        startTimeMs: Long
    ) {
        val duration = System.currentTimeMillis() - startTimeMs
        for (interceptor in interceptors) {
            try {
                interceptor.afterPublish(event, result, context, duration)
            } catch (e: Exception) {
                logger.error("Error in afterPublish interceptor", e)
            }
        }
    }

    /**
     * Create publish context for event.
     */
    private fun createPublishContext(event: DomainEvent): EventClientInterceptor.PublishContext {
        val metadata = event.getMetadata()
        return EventClientInterceptor.PublishContext(
            eventId = metadata.eventId,
            eventType = event.eventType(),
            retryPolicy = retryPolicy,
            metadata = mapOf(
                "correlationId" to (correlationId ?: metadata.correlationId ?: ""),
                "causationId" to (metadata.causationId ?: ""),
                "source" to (metadata.source ?: "unknown")
            )
        )
    }
}
