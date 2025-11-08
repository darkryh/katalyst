package com.ead.katalyst.events

import java.util.UUID

/**
 * Metadata attached to domain events for tracing, versioning, and correlation.
 *
 * This data class provides:
 * - **Event Identification**: Unique ID for the event instance
 * - **Distributed Tracing**: Correlation ID to trace across systems
 * - **Causality**: Causation ID to link events to originating commands
 * - **Versioning**: Event schema version for evolution
 * - **Timestamps**: When event occurred and was created
 * - **Source Information**: Which system originated the event
 *
 * @param eventId Unique identifier for this event instance (UUID)
 * @param eventType Human-readable event type (e.g., "user.created")
 * @param timestamp When the event was created (epoch millis, server time)
 * @param version Event schema version for evolution tracking
 * @param correlationId Traces across multiple related events (same request)
 * @param causationId ID of the command/action that caused this event
 * @param source System or service that originated the event
 * @param occurredAt When the event actually happened (may differ from timestamp)
 */
data class EventMetadata(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val correlationId: String? = null,
    val causationId: String? = null,
    val source: String? = null,
    val occurredAt: Long? = null
) {
    /**
     * Creates a new EventMetadata with updated correlation ID.
     *
     * Useful for chaining events through a request.
     *
     * @param newCorrelationId The new correlation ID
     * @return New EventMetadata instance with updated correlation ID
     */
    fun withCorrelationId(newCorrelationId: String): EventMetadata =
        copy(correlationId = newCorrelationId)

    /**
     * Creates a new EventMetadata with causation information.
     *
     * Links this event to the command/request that caused it.
     *
     * @param commandId ID of the command that caused this event
     * @return New EventMetadata instance with causation ID set
     */
    fun withCausationId(commandId: String): EventMetadata =
        copy(causationId = commandId)

    /**
     * Creates a new EventMetadata with updated source.
     *
     * @param newSource The source system/service
     * @return New EventMetadata instance with updated source
     */
    fun withSource(newSource: String): EventMetadata =
        copy(source = newSource)

    /**
     * Creates a new EventMetadata with occurred timestamp.
     *
     * When the event actually occurred (may differ from when it was published).
     *
     * @param whenItOccurred Epoch millis when event actually happened
     * @return New EventMetadata instance with occurred timestamp
     */
    fun withOccurredAt(whenItOccurred: Long): EventMetadata =
        copy(occurredAt = whenItOccurred)

    companion object {
        /**
         * Create EventMetadata with minimal required fields.
         *
         * @param eventType The event type identifier
         * @return EventMetadata with defaults for other fields
         */
        fun of(eventType: String): EventMetadata =
            EventMetadata(eventType = eventType)

        /**
         * Create EventMetadata with event type and correlation ID.
         *
         * @param eventType The event type identifier
         * @param correlationId ID for tracing related events
         * @return EventMetadata with correlation ID set
         */
        fun of(eventType: String, correlationId: String): EventMetadata =
            EventMetadata(eventType = eventType, correlationId = correlationId)
    }
}
