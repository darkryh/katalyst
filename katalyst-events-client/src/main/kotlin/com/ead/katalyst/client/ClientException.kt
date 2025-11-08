package com.ead.katalyst.client

import com.ead.katalyst.events.EventException

/**
 * Base exception for client-layer errors.
 *
 * Indicates an error in EventClient operations.
 *
 * @param message Error message
 * @param cause Underlying exception (if any)
 */
open class ClientException(
    message: String,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when event publication fails after retries exhausted.
 *
 * Common causes:
 * - All retry attempts failed
 * - Non-retriable error occurred
 * - Destination unreachable
 * - Serialization failure
 *
 * **Usage:**
 *
 * ```kotlin
 * try {
 *     client.publish(event)
 * } catch (e: PublishException) {
 *     logger.error("Publish failed: ${e.eventType} - ${e.reason}")
 *     metrics.increment("event.publish.failure")
 * }
 * ```
 *
 * @param message Error message
 * @param eventId The event ID that failed
 * @param eventType The event type that failed
 * @param reason Why publication failed
 * @param attemptCount Number of attempts made
 * @param cause Underlying exception
 */
class PublishException(
    message: String,
    val eventId: String? = null,
    val eventType: String? = null,
    val reason: String? = null,
    val attemptCount: Int = 1,
    cause: Throwable? = null
) : ClientException(message, cause)

/**
 * Thrown when publish operation exhausts retries.
 *
 * Indicates that a retriable error occurred but retries were exhausted.
 *
 * **Usage:**
 *
 * ```kotlin
 * try {
 *     client.publish(event)
 * } catch (e: RetryExhaustedException) {
 *     logger.warn("Retries exhausted: ${e.maxAttempts} attempts")
 *     // Queue for later retry or manual intervention
 * }
 * ```
 *
 * @param message Error message
 * @param eventType The event type being published
 * @param maxAttempts Maximum attempts configured
 * @param lastException The final exception that occurred
 */
class RetryExhaustedException(
    message: String,
    val eventType: String,
    val maxAttempts: Int,
    val lastException: Throwable? = null
) : ClientException(message, lastException)

/**
 * Thrown when EventClient is not properly configured.
 *
 * Common causes:
 * - Required dependencies not provided
 * - Invalid configuration values
 * - Conflicting settings
 * - Missing required resources
 *
 * **Usage:**
 *
 * ```kotlin
 * try {
 *     val client = EventClient.builder()
 *         .retryPolicy(invalidPolicy)
 *         .build()
 * } catch (e: ClientConfigurationException) {
 *     logger.error("Configuration error: ${e.message}")
 * }
 * ```
 *
 * @param message Error message
 * @param configKey The configuration key that has an issue
 * @param cause Underlying exception
 */
class ClientConfigurationException(
    message: String,
    val configKey: String? = null,
    cause: Throwable? = null
) : ClientException(message, cause)

/**
 * Thrown when an interceptor prevents operation.
 *
 * Indicates that an EventClientInterceptor aborted the publish operation.
 *
 * @param message Error message
 * @param eventType The event type being published
 * @param interceptorName Name or class of the interceptor that aborted
 * @param reason Reason for abort from interceptor
 */
class InterceptorAbortException(
    message: String,
    val eventType: String,
    val interceptorName: String? = null,
    val reason: String? = null
) : ClientException(message)

/**
 * Thrown when batch publish encounters issues.
 *
 * Indicates partial failure or configuration issues in batch operations.
 *
 * @param message Error message
 * @param successCount Number of successful publishes in batch
 * @param failureCount Number of failed publishes in batch
 * @param failures List of individual failures
 */
class BatchPublishException(
    message: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val failures: List<PublishResult.Failure> = emptyList()
) : ClientException(message)

/**
 * Thrown when external messaging publish fails.
 *
 * Indicates failure at the external messaging system level (after validation and bus publish).
 *
 * Common causes:
 * - Message broker unavailable
 * - Serialization failed
 * - Routing failed
 * - Connection error
 *
 * @param message Error message
 * @param destination The destination where publish was attempted
 * @param contentType Content type being sent
 * @param cause Underlying exception
 */
class ExternalPublishException(
    message: String,
    val destination: String? = null,
    val contentType: String? = null,
    cause: Throwable? = null
) : ClientException(message, cause)

/**
 * Thrown when event validation fails before publish.
 *
 * Indicates that event validation rejected the event.
 *
 * @param message Error message
 * @param eventType The event type that failed validation
 * @param validationErrors List of validation errors
 */
class EventValidationFailedException(
    message: String,
    val eventType: String,
    val validationErrors: List<String> = emptyList()
) : ClientException(message)

/**
 * Thrown when client state is invalid for the requested operation.
 *
 * Common causes:
 * - Client not initialized
 * - Client already closed
 * - Invalid state for operation
 * - Resources not available
 *
 * @param message Error message
 * @param state Current state of the client
 */
class InvalidClientStateException(
    message: String,
    val state: String? = null
) : ClientException(message)
