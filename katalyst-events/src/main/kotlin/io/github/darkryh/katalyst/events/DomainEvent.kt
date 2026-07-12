package io.github.darkryh.katalyst.events

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Base contract for all domain events in the system.
 *
 * Domain events represent something meaningful that happened in the business domain.
 * They are immutable, past-tense descriptions of state changes.
 *
 * All events must carry metadata for:
 * - Tracing and debugging (correlation IDs)
 * - Versioning and evolution
 * - Causality tracking
 * - Timestamps
 *
 * **Usage:**
 * Implement this interface for your domain events:
 *
 * ```kotlin
 * data class UserCreatedEvent(
 *     val userId: UUID,
 *     val email: String,
 *     val name: String,
 *     override val metadata: EventMetadata = EventMetadata(
 *         eventType = "user.created"
 *     )
 * ) : DomainEvent {
 *     override fun getMetadata(): EventMetadata = metadata
 * }
 * ```
 *
 * **Sealed Hierarchies:**
 * For grouping related events, use sealed classes:
 *
 * ```kotlin
 * sealed class UserEvent : DomainEvent {
 *     data class UserCreatedEvent(...) : UserEvent()
 *     data class UserDeletedEvent(...) : UserEvent()
 *     data class UserUpdatedEvent(...) : UserEvent()
 * }
 *
 * // Single handler can listen to all UserEvents:
 * class UserAuditHandler : EventHandler<UserEvent> {
 *     override val eventType = UserEvent::class
 *     override suspend fun handle(event: UserEvent) { ... }
 * }
 * ```
 */
interface DomainEvent {
    /**
     * Unique identifier for this event instance.
     *
     * Used for deduplication: if a service retries a transaction,
     * the same event ID ensures the event is not published twice.
     *
     * Default: UUID.randomUUID().toString()
     * Can be overridden to provide specific event IDs.
     *
     * NEW - P0 Critical Fix: Event Deduplication
     */
    val eventId: String get() = getMetadata().eventId

    /**
     * Get the metadata associated with this event.
     *
     * Metadata includes:
     * - Event type (for routing)
     * - Correlation ID (for tracing)
     * - Causation ID (for causality tracking)
     * - Timestamps (when it occurred, when it was created)
     * - Version (for schema evolution)
     * - Source (which system created it)
     *
     * @return EventMetadata containing tracing and versioning information
     */
    fun getMetadata(): EventMetadata = DomainEventDefaults.metadata(this)


    /**
     * Get the event type identifier.
     *
     * Default implementation uses metadata's eventType field.
     * Override if you need custom behavior.
     *
     * @return String identifier for this event type
     */
    fun eventType(): String = getMetadata().eventType
}

private object DomainEventDefaults {
    private val collectedEvents = ReferenceQueue<DomainEvent>()
    private val metadataByEvent = ConcurrentHashMap<IdentityWeakEventRef, EventMetadata>()

    fun metadata(event: DomainEvent): EventMetadata {
        removeCollectedEvents()
        val key = IdentityWeakEventRef(event)
        return metadataByEvent.computeIfAbsent(key) {
            EventMetadata(eventType = event::class.simpleName ?: "UnknownEvent")
        }
    }

    private fun removeCollectedEvents() {
        while (true) {
            val reference = collectedEvents.poll() as? IdentityWeakEventRef ?: return
            metadataByEvent.remove(reference)
        }
    }

    private class IdentityWeakEventRef(
        event: DomainEvent,
    ) : WeakReference<DomainEvent>(event, collectedEvents) {
        private val identityHash = System.identityHashCode(event)

        override fun hashCode(): Int = identityHash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IdentityWeakEventRef) return false
            val event = get() ?: return false
            return event === other.get()
        }
    }
}
