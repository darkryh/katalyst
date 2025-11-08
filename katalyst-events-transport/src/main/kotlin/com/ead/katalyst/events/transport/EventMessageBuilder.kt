package com.ead.katalyst.events.transport

/**
 * Fluent builder for EventMessage construction.
 *
 * **Usage:**
 *
 * ```kotlin
 * val message = EventMessageBuilder("application/json", payload)
 *     .header("event-type", "user.created")
 *     .header("correlation-id", "abc123")
 *     .eventId("evt-456")
 *     .eventType("user.created")
 *     .timestamp(System.currentTimeMillis())
 *     .build()
 * ```
 *
 * @param contentType MIME type of the payload
 * @param payload Serialized event data
 */
class EventMessageBuilder(
    private val contentType: String,
    private val payload: ByteArray
) {
    private var headers: MutableMap<String, String> = mutableMapOf()
    private var timestamp: Long = System.currentTimeMillis()
    private var eventId: String? = null
    private var eventType: String? = null

    /**
     * Add a header.
     *
     * @param name Header name
     * @param value Header value
     * @return This builder for chaining
     */
    fun header(name: String, value: String) = apply {
        headers[name] = value
    }

    /**
     * Add multiple headers.
     *
     * @param h Headers to add
     * @return This builder for chaining
     */
    fun headers(h: Map<String, String>) = apply {
        headers.putAll(h)
    }

    /**
     * Set the event ID.
     *
     * @param id The event instance ID
     * @return This builder for chaining
     */
    fun eventId(id: String?) = apply {
        this.eventId = id
    }

    /**
     * Set the event type.
     *
     * @param type The event type identifier
     * @return This builder for chaining
     */
    fun eventType(type: String?) = apply {
        this.eventType = type
    }

    /**
     * Set the timestamp.
     *
     * @param ts Epoch millis
     * @return This builder for chaining
     */
    fun timestamp(ts: Long) = apply {
        this.timestamp = ts
    }

    /**
     * Build the EventMessage.
     *
     * @return Constructed EventMessage
     */
    fun build(): EventMessage = EventMessage(
        contentType = contentType,
        payload = payload,
        headers = headers,
        timestamp = timestamp,
        eventId = eventId,
        eventType = eventType
    )
}
