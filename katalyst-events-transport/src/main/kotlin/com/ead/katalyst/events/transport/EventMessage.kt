package com.ead.katalyst.events.transport

/**
 * Transport message for events.
 *
 * Represents an event in a serialized, transportable format.
 * Can be sent over messaging systems (RabbitMQ, Kafka, etc.).
 *
 * **Difference from katalyst-messaging.Message:**
 * - EventMessage: Domain-specific, carries event metadata
 * - katalyst-messaging.Message: Generic transport message (key/payload/headers)
 *
 * EventMessage gets converted to katalyst-messaging.Message for actual transport.
 *
 * @param contentType MIME type (e.g., "application/json", "application/protobuf")
 * @param payload Serialized event data (bytes)
 * @param headers Metadata headers (event-type, correlation-id, etc.)
 * @param timestamp When the message was created
 * @param eventId Event instance ID (for deduplication)
 * @param eventType Event type identifier (for routing)
 */
data class EventMessage(
    val contentType: String,
    val payload: ByteArray,
    val headers: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventId: String? = null,
    val eventType: String? = null
) {
    /**
     * Get a header value by name.
     *
     * @param name Header name
     * @param defaultValue Value if header not found
     * @return Header value or default
     */
    fun getHeader(name: String, defaultValue: String? = null): String? =
        headers[name] ?: defaultValue

    /**
     * Check if a header exists.
     *
     * @param name Header name
     * @return True if header exists
     */
    fun hasHeader(name: String): Boolean = name in headers

    /**
     * Convert to builder for modification.
     *
     * @return EventMessageBuilder with current values
     */
    fun toBuilder(): EventMessageBuilder =
        EventMessageBuilder(contentType, payload)
            .headers(headers)
            .timestamp(timestamp)
            .eventId(eventId)
            .eventType(eventType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventMessage

        if (contentType != other.contentType) return false
        if (!payload.contentEquals(other.payload)) return false
        if (headers != other.headers) return false
        // Don't compare timestamp - messages may be equal but created at different times
        if (eventId != other.eventId) return false
        if (eventType != other.eventType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (eventId?.hashCode() ?: 0)
        result = 31 * result + (eventType?.hashCode() ?: 0)
        return result
    }
}
