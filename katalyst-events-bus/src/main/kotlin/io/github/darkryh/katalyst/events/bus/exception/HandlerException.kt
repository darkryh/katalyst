package io.github.darkryh.katalyst.events.bus.exception

import io.github.darkryh.katalyst.events.exception.EventException

/**
 * Base exception for event handler errors.
 *
 * Indicates an error during handler execution or registration.
 *
 * **Note:** Handler exceptions are caught by ApplicationEventBus.
 * They are logged but do not propagate to the caller.
 *
 * @param message Error message
 * @param cause Underlying exception (if any)
 */
open class HandlerException(
    message: String,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when a handler's handle() method fails.
 *
 * The ApplicationEventBus catches this and logs it.
 * Other handlers still execute.
 *
 * **Example:**
 * If a handler throws an IOException, ApplicationEventBus catches it,
 * logs it as HandlerExecutionException, and continues with other handlers.
 *
 * @param message Error message
 * @param handlerClass Fully qualified class name of the handler
 * @param eventType The event being handled
 * @param cause The underlying exception from the handler
 */
class HandlerExecutionException(
    message: String,
    val handlerClass: String? = null,
    val eventType: String? = null,
    cause: Throwable? = null
) : HandlerException(message, cause)

/**
 * Thrown when handler registration fails.
 *
 * This can happen if:
 * - Handler instance cannot be created
 * - Handler eventType KClass is invalid
 * - Handler state is corrupt
 *
 * @param message Error message
 * @param handlerClass The handler that failed to register
 * @param cause Underlying exception
 */
class HandlerRegistrationException(
    message: String,
    val handlerClass: String? = null,
    cause: Throwable? = null
) : HandlerException(message, cause)

/**
 * Thrown when handler metadata is invalid.
 *
 * This happens if:
 * - Handler eventType cannot be determined
 * - Handler eventType is not a DomainEvent
 * - Handler has invalid type parameters
 *
 * @param message Error message
 * @param handlerClass The handler with invalid metadata
 * @param reason Why the metadata is invalid
 * @param cause Underlying exception
 */
class InvalidHandlerMetadataException(
    message: String,
    val handlerClass: String? = null,
    val reason: String? = null,
    cause: Throwable? = null
) : HandlerException(message, cause)

/**
 * Thrown when handler discovery fails.
 *
 * This happens during application startup when scanning for handlers.
 * The application will not start if critical handlers cannot be discovered.
 *
 * @param message Error message
 * @param reason Why discovery failed
 * @param cause Underlying exception
 */
class HandlerDiscoveryException(
    message: String,
    val reason: String? = null,
    cause: Throwable? = null
) : HandlerException(message, cause)

/**
 * Thrown when handler is called with wrong event type.
 *
 * This should rarely happen in normal operation as type checking is done
 * at registration time. But it can occur if:
 * - Handler type was changed after registration
 * - Type system somehow allows mismatch
 * - Test code bypasses normal registration
 *
 * @param message Error message
 * @param handlerClass The handler that received wrong event
 * @param expectedType The event type handler expected
 * @param actualType The event type that was passed
 * @param cause Underlying exception
 */
class WrongEventTypeException(
    message: String,
    val handlerClass: String? = null,
    val expectedType: String? = null,
    val actualType: String? = null,
    cause: Throwable? = null
) : HandlerException(message, cause)
