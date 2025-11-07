package com.ead.katalyst.events

import java.time.LocalDateTime
import java.util.UUID

/**
 * Base contract for events emitted by the application or external transports.
 *
 * Events carry a unique identifier plus the timestamp when they occurred so they
 * can be correlated or replayed by downstream consumers.
 */
interface DomainEvent {
    /**
     * Human-readable event identifier, defaults to the Kotlin class name.
     */
    fun eventType(): String = this::class.qualifiedName ?: "UnknownEvent"
}
