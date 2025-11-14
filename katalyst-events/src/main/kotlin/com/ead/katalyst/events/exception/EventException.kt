package com.ead.katalyst.events.exception

/**
 * Base exception for event-related errors.
 *
 * All event system exceptions inherit from this class.
 *
 * @param message Error message
 * @param cause Underlying exception (if any)
 */
open class EventException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when event validation fails.
 *
 * Indicates that an event could not be published because it failed validation.
 *
 * **Usage in EventClient:**
 *
 * ```kotlin
 * try {
 *     eventClient.publish(userCreatedEvent)
 * } catch (e: EventValidationException) {
 *     logger.error("Event validation failed: {}", e.errors.joinToString(", "))
 * }
 * ```
 *
 * @param message Error message
 * @param errors List of validation error messages
 * @param cause Underlying exception (if any)
 */
class EventValidationException(
    message: String,
    val errors: List<String> = emptyList(),
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when event serialization fails.
 *
 * Indicates an error converting an event to a transportable format.
 *
 * @param message Error message
 * @param eventType The type of event that failed
 * @param cause Underlying exception (if any)
 */
class EventSerializationException(
    message: String,
    val eventType: String? = null,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when event deserialization fails.
 *
 * Indicates an error reconstructing an event from a transportable format.
 *
 * @param message Error message
 * @param contentType The content type that was expected
 * @param targetType The target event type
 * @param cause Underlying exception (if any)
 */
class EventDeserializationException(
    message: String,
    val contentType: String? = null,
    val targetType: String? = null,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when event handler execution fails.
 *
 * Indicates an error while invoking an event handler.
 * Note: The event bus catches these and logs them, but doesn't propagate.
 *
 * @param message Error message
 * @param handlerClass The handler that failed
 * @param eventType The event being processed
 * @param cause Underlying exception (if any)
 */
class EventHandlerException(
    message: String,
    val handlerClass: String? = null,
    val eventType: String? = null,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when event routing fails.
 *
 * Indicates an error determining the destination for an event.
 *
 * @param message Error message
 * @param eventType The event that couldn't be routed
 * @param cause Underlying exception (if any)
 */
class EventRoutingException(
    message: String,
    val eventType: String? = null,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when publishing an event fails after all retries.
 *
 * Indicates that an event could not be published to the remote system
 * even after retry attempts.
 *
 * @param message Error message
 * @param eventType The event that failed
 * @param attempts Number of attempts made
 * @param lastError The last error encountered
 * @param cause Underlying exception (if any)
 */
class EventPublishException(
    message: String,
    val eventType: String? = null,
    val attempts: Int = 0,
    val lastError: Throwable? = null,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when event handler registration fails.
 *
 * Indicates an error during handler discovery or registration.
 *
 * @param message Error message
 * @param handlerClass The handler that couldn't be registered
 * @param cause Underlying exception (if any)
 */
class EventHandlerRegistrationException(
    message: String,
    val handlerClass: String? = null,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when configuration is invalid.
 *
 * Indicates that event system was configured incorrectly.
 *
 * @param message Error message
 * @param cause Underlying exception (if any)
 */
class EventConfigurationException(
    message: String,
    cause: Throwable? = null
) : EventException(message, cause)
