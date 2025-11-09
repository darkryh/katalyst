package com.ead.katalyst.events.transport.serialization

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.transport.EventMessage
import com.ead.katalyst.events.transport.EventTypeResolver
import com.ead.katalyst.events.transport.exception.EventDeserializationException
import java.nio.charset.StandardCharsets

/**
 * Deserializes events from JSON format.
 *
 * Uses reflection to deserialize JSON back into event objects.
 * Requires EventTypeResolver to map type strings to classes.
 *
 * **Requirements:**
 * - Event must have a no-arg constructor
 * - JSON structure must match event class properties
 *
 * **Process:**
 * 1. Get event-type from message headers
 * 2. Use resolver to find event class
 * 3. Deserialize JSON payload into event class
 * 4. Return reconstructed DomainEvent
 *
 * **Content-Type Validation:**
 * Only deserializes messages with content-type:
 * - "application/json"
 * - "application/json; charset=utf-8"
 *
 * **Usage:**
 *
 * ```kotlin
 * val resolver = InMemoryEventTypeResolver()
 * resolver.register("user.created", UserCreatedEvent::class)
 *
 * val deserializer = JsonEventDeserializer(resolver)
 *
 * val message = EventMessage(
 *     contentType = "application/json",
 *     payload = """{"userId":"123","email":"user@example.com"}""".toByteArray(),
 *     headers = mapOf("event-type" to "user.created")
 * )
 *
 * val event = deserializer.deserialize(message)
 * // event is now a UserCreatedEvent instance
 * ```
 *
 * @param typeResolver Resolver for mapping event type strings to classes
 * @param jsonParser Optional custom JSON deserialization function
 */
class JsonEventDeserializer(
    private val typeResolver: EventTypeResolver,
    private val jsonParser: (String, Class<*>) -> Any? = { json, clazz -> defaultJsonParser(json, clazz) }
) : EventDeserializer {

    override suspend fun deserialize(message: EventMessage): DomainEvent {
        // Validate content type
        if (!canHandle(message.contentType)) {
            throw EventDeserializationException(
                "Unsupported content type: ${message.contentType}. Expected application/json",
                contentType = message.contentType
            )
        }

        // Get event type from headers
        val eventType = message.getHeader("event-type")
            ?: throw EventDeserializationException(
                "Missing event-type header in message",
                contentType = message.contentType
            )

        // Resolve event class
        val eventClass = try {
            typeResolver.resolve(eventType)
        } catch (e: EventDeserializationException) {
            throw EventDeserializationException(
                "Failed to resolve event type: $eventType - ${e.message}",
                contentType = message.contentType,
                targetType = eventType,
                cause = e
            )
        }

        // Decode JSON from payload
        val json = String(message.payload, StandardCharsets.UTF_8)

        // Deserialize JSON to event
        return try {
            val result = jsonParser(json, eventClass.java)
            result as? DomainEvent
                ?: throw EventDeserializationException(
                    "Deserialized object is not a DomainEvent: ${result?.javaClass?.name}",
                    contentType = message.contentType,
                    targetType = eventType
                )
        } catch (e: EventDeserializationException) {
            throw e
        } catch (e: Exception) {
            throw EventDeserializationException(
                "Failed to deserialize JSON to $eventType: ${e.message}",
                contentType = message.contentType,
                targetType = eventType,
                cause = e
            )
        }
    }

    override fun canHandle(contentType: String): Boolean {
        return contentType.startsWith("application/json")
    }

    override fun getSupportedContentTypes(): List<String> {
        return listOf(
            "application/json",
            "application/json; charset=utf-8"
        )
    }

    companion object {
        /**
         * Default JSON parser using reflection.
         *
         * Attempts to use Jackson if available, falls back gracefully.
         *
         * @param json JSON string
         * @param clazz Target class
         * @return Deserialized object
         * @throws Exception if parsing fails
         */
        private fun defaultJsonParser(json: String, clazz: Class<*>): Any? {
            return try {
                // Try Jackson if available
                val jacksonClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper")
                val mapper = jacksonClass.getConstructor().newInstance()
                val readValue = jacksonClass.getMethod("readValue", String::class.java, Class::class.java)
                readValue.invoke(mapper, json, clazz)
            } catch (e: ClassNotFoundException) {
                throw IllegalStateException(
                    "JSON deserialization requires Jackson. " +
                            "Add com.fasterxml.jackson.core:jackson-databind to dependencies, " +
                            "or provide a custom jsonParser to JsonEventDeserializer",
                    e
                )
            }
        }
    }
}