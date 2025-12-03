package io.github.darkryh.katalyst.events.transport.serialization

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.transport.EventMessage
import io.github.darkryh.katalyst.events.transport.exception.EventSerializationException
import java.nio.charset.StandardCharsets

/**
 * Serializes events to JSON format.
 *
 * Uses reflection to serialize event objects to JSON.
 * Attempts to use Jackson if available, falls back gracefully.
 *
 * **Requirements:**
 * - Event must have a no-arg constructor for deserialization
 * - Event fields should be serialization-friendly (primitives, data classes, etc.)
 *
 * **Content-Type:**
 * Produces: "application/json; charset=utf-8"
 *
 * **Metadata in Headers:**
 * Automatically adds:
 * - event-type: From event.getMetadata().eventType
 * - event-id: From event.getMetadata().eventId
 * - event-version: From event.getMetadata().version
 * - correlation-id: From event.getMetadata().correlationId (if present)
 * - causation-id: From event.getMetadata().causationId (if present)
 * - event-timestamp: From event.getMetadata().timestamp
 * - content-type: "application/json; charset=utf-8"
 *
 * **Usage:**
 *
 * ```kotlin
 * val serializer = JsonEventSerializer()
 *
 * val event = UserCreatedEvent(
 *     userId = "123",
 *     email = "user@example.com",
 *     metadata = EventMetadata(eventType = "user.created")
 * )
 *
 * val message = serializer.serialize(event)
 * // message.payload contains: {"userId":"123","email":"user@example.com",...}
 * // message.contentType = "application/json; charset=utf-8"
 * // message.headers contains metadata
 * ```
 *
 * @param jsonMapper Optional custom JSON serialization function (defaults to toString-based)
 */
class JsonEventSerializer(
    private val jsonMapper: (Any) -> String = { obj -> defaultJsonMapper(obj) }
) : EventSerializer {

    override suspend fun serialize(event: DomainEvent): EventMessage {
        val metadata = event.getMetadata()

        // Serialize event to JSON
        val json = try {
            jsonMapper(event)
        } catch (e: Exception) {
            throw EventSerializationException(
                "Failed to serialize event to JSON: ${e.message}",
                eventType = metadata.eventType,
                cause = e
            )
        }

        val payload = json.toByteArray(StandardCharsets.UTF_8)

        // Build headers with metadata
        val headers = mutableMapOf(
            "event-type" to metadata.eventType,
            "event-id" to metadata.eventId,
            "event-version" to metadata.version.toString(),
            "content-type" to getContentType()
        )

        // Add optional metadata if present
        metadata.correlationId?.let { headers["correlation-id"] = it }
        metadata.causationId?.let { headers["causation-id"] = it }
        metadata.source?.let { headers["source"] = it }
        headers["event-timestamp"] = metadata.timestamp.toString()

        return EventMessage(
            contentType = getContentType(),
            payload = payload,
            headers = headers,
            timestamp = metadata.timestamp,
            eventId = metadata.eventId,
            eventType = metadata.eventType
        )
    }

    override fun getContentType(): String = "application/json; charset=utf-8"

    companion object {
        /**
         * Default JSON mapper using reflection.
         *
         * Attempts to use Jackson if available in classpath.
         * Falls back to toString() if Jackson not available.
         *
         * @param obj Object to serialize
         * @return JSON string
         * @throws Exception if serialization fails
         */
        private fun defaultJsonMapper(obj: Any): String {
            return try {
                // Try Jackson if available
                val jacksonClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper")
                val mapper = jacksonClass.getConstructor().newInstance()
                val writeValueAsString = jacksonClass.getMethod("writeValueAsString", Object::class.java)
                writeValueAsString.invoke(mapper, obj) as String
            } catch (e: ClassNotFoundException) {
                // Jackson not available, use toString()
                // This is a fallback - in production, you should provide a real JSON mapper
                obj.toString()
            } catch (e: Exception) {
                // Other errors, try toString as fallback
                obj.toString()
            }
        }
    }
}