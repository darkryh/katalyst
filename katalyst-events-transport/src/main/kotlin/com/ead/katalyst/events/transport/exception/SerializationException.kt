package com.ead.katalyst.events.transport.exception

import com.ead.katalyst.events.exception.EventException

/**
 * Base exception for serialization/deserialization errors.
 *
 * Indicates an error converting events to/from transport format.
 *
 * @param message Error message
 * @param cause Underlying exception (if any)
 */
open class TransportException(
    message: String,
    cause: Throwable? = null
) : EventException(message, cause)

/**
 * Thrown when event serialization to message format fails.
 *
 * Common causes:
 * - Event has non-serializable fields
 * - JSON mapper not available
 * - Serializer implementation error
 * - Event is null
 *
 * **Usage:**
 *
 * ```kotlin
 * try {
 *     val message = serializer.serialize(event)
 * } catch (e: EventSerializationException) {
 *     logger.error("Cannot serialize {}: {}", e.eventType, e.message)
 * }
 * ```
 *
 * @param message Error message
 * @param eventType The event type that failed
 * @param cause Underlying exception
 */
class EventSerializationException(
    message: String,
    val eventType: String? = null,
    cause: Throwable? = null
) : TransportException(message, cause)

/**
 * Thrown when message deserialization to event object fails.
 *
 * Common causes:
 * - Unknown event type
 * - JSON is malformed
 * - Event class not found
 * - Event constructor issues
 * - Required fields missing
 *
 * **Usage:**
 *
 * ```kotlin
 * try {
 *     val event = deserializer.deserialize(message)
 * } catch (e: EventDeserializationException) {
 *     logger.error("Cannot deserialize {} ({}): {}",
 *         e.targetType,
 *         e.contentType,
 *         e.message)
 * }
 * ```
 *
 * @param message Error message
 * @param contentType The content type of the message
 * @param targetType The event type being deserialized
 * @param cause Underlying exception
 */
class EventDeserializationException(
    message: String,
    val contentType: String? = null,
    val targetType: String? = null,
    cause: Throwable? = null
) : TransportException(message, cause)

/**
 * Thrown when event routing fails.
 *
 * Common causes:
 * - Router cannot determine destination
 * - Event type not recognized
 * - Routing configuration invalid
 * - Router implementation error
 *
 * **Usage:**
 *
 * ```kotlin
 * try {
 *     val destination = router.resolve(event)
 * } catch (e: EventRoutingException) {
 *     logger.error("Cannot route {}: {}", e.eventType, e.message)
 * }
 * ```
 *
 * @param message Error message
 * @param eventType The event type that couldn't be routed
 * @param reason Explanation of why routing failed
 * @param cause Underlying exception
 */
class EventRoutingException(
    message: String,
    val eventType: String? = null,
    val reason: String? = null,
    cause: Throwable? = null
) : TransportException(message, cause)

/**
 * Thrown when content type is not supported.
 *
 * Indicates that a serializer/deserializer was asked to handle
 * a content type it doesn't support.
 *
 * **Example:**
 * Asking JsonEventDeserializer to deserialize a message with
 * content-type: "application/protobuf"
 *
 * @param message Error message
 * @param contentType The unsupported content type
 * @param supported List of supported content types
 * @param cause Underlying exception
 */
class UnsupportedContentTypeException(
    message: String,
    val contentType: String? = null,
    val supported: List<String> = emptyList(),
    cause: Throwable? = null
) : TransportException(message, cause)

/**
 * Thrown when event type cannot be resolved.
 *
 * Common causes:
 * - Event type string is not registered
 * - Event class not found in classpath
 * - Event type format is invalid
 *
 * @param message Error message
 * @param eventType The type that couldn't be resolved
 * @param cause Underlying exception
 */
class UnknownEventTypeException(
    message: String,
    val eventType: String? = null,
    cause: Throwable? = null
) : TransportException(message, cause)

/**
 * Thrown when message format is invalid.
 *
 * Common causes:
 * - Missing required headers
 * - Payload is corrupted
 * - Message structure doesn't match expectations
 * - Headers are invalid
 *
 * @param message Error message
 * @param missingHeaders Headers that are required but missing
 * @param cause Underlying exception
 */
class InvalidMessageFormatException(
    message: String,
    val missingHeaders: List<String> = emptyList(),
    cause: Throwable? = null
) : TransportException(message, cause)

/**
 * Thrown when event transformation fails.
 *
 * Common causes:
 * - Format conversion error
 * - Mapping function error
 * - Data loss during transformation
 *
 * @param message Error message
 * @param fromFormat Source format
 * @param toFormat Target format
 * @param cause Underlying exception
 */
class EventTransformationException(
    message: String,
    val fromFormat: String? = null,
    val toFormat: String? = null,
    cause: Throwable? = null
) : TransportException(message, cause)
