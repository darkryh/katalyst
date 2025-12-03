package io.github.darkryh.katalyst.events.transport.serialization

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.transport.EventMessage

/**
 * Serializes domain events into transportable messages.
 *
 * Implementations convert a DomainEvent into an EventMessage
 * that can be sent over messaging systems.
 *
 * **Responsibilities:**
 * - Convert event object to bytes
 * - Add content-type header
 * - Include event metadata in headers (eventId, correlationId, etc.)
 * - Handle any serialization errors
 *
 * **Implementations:**
 * - JsonEventSerializer: Serialize to JSON
 * - Custom implementations: Protocol Buffers, Avro, etc.
 *
 * **Usage:**
 *
 * ```kotlin
 * val serializer = JsonEventSerializer()
 * val event = UserCreatedEvent(...)
 *
 * try {
 *     val message = serializer.serialize(event)
 *     // Send message to external system
 *     producer.send(destination, message)
 * } catch (e: EventSerializationException) {
 *     logger.error("Failed to serialize event: {}", e.message)
 * }
 * ```
 *
 * **Threading:**
 * Implementations should be thread-safe for concurrent serialization.
 */
interface EventSerializer {
    /**
     * Serialize a domain event into a transport message.
     *
     * The resulting message includes:
     * - Event data in the payload
     * - Content-Type header
     * - Event metadata in headers (type, ID, correlation ID, etc.)
     * - Timestamps
     *
     * @param event The event to serialize
     * @return EventMessage ready for transport
     * @throws io.github.darkryh.katalyst.events.transport.exception.EventSerializationException if serialization fails
     */
    suspend fun serialize(event: DomainEvent): EventMessage

    /**
     * Get the content type this serializer produces.
     *
     * Examples: "application/json", "application/protobuf", "application/avro"
     *
     * @return MIME type string
     */
    fun getContentType(): String
}