package com.ead.katalyst.events.transport.serialization

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.transport.EventMessage

/**
 * Deserializes transport messages back into domain events.
 *
 * Reconstructs DomainEvent objects from EventMessages that came from
 * external systems or were stored in persistence.
 *
 * **Responsibilities:**
 * - Determine event type from message headers
 * - Deserialize payload into the correct event class
 * - Restore event metadata
 * - Validate deserialization
 * - Handle type resolution
 *
 * **Implementations:**
 * - JsonEventDeserializer: Deserialize from JSON
 * - Custom implementations: Protocol Buffers, Avro, etc.
 *
 * **Usage:**
 *
 * ```kotlin
 * val deserializer = JsonEventDeserializer(eventTypeResolver)
 *
 * try {
 *     val event = deserializer.deserialize(message)
 *     // event is now a DomainEvent
 *     eventBus.publish(event)
 * } catch (e: EventDeserializationException) {
 *     logger.error("Failed to deserialize event: {}", e.message)
 * }
 * ```
 *
 * **Threading:**
 * Implementations should be thread-safe for concurrent deserialization.
 */
interface EventDeserializer {
    /**
     * Deserialize a transport message back into a domain event.
     *
     * **Process:**
     * 1. Get event type from message headers
     * 2. Use EventTypeResolver to find the KClass
     * 3. Deserialize payload into that class
     * 4. Return reconstructed DomainEvent
     *
     * @param message The message to deserialize
     * @return Reconstructed DomainEvent
     * @throws com.ead.katalyst.events.transport.exception.EventDeserializationException if deserialization fails
     */
    suspend fun deserialize(message: EventMessage): DomainEvent

    /**
     * Check if this deserializer can handle the given content type.
     *
     * @param contentType MIME type to check
     * @return True if this deserializer can handle this type
     */
    fun canHandle(contentType: String): Boolean

    /**
     * Get the content types this deserializer supports.
     *
     * Examples:
     * - "application/json"
     * - "application/json;charset=utf-8"
     * - "application/protobuf"
     *
     * @return List of supported content types
     */
    fun getSupportedContentTypes(): List<String>
}