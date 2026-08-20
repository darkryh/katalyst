package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** One entry of the routing table: an event type and the handlers subscribed to it. */
@Serializable
data class SubscriptionEntry(
    val eventType: String,
    val handlers: List<String> = emptyList(),
) {
    /** Zero handlers => any publish of this type is a silently-dropped dead event. */
    val deadEventRisk: Boolean get() = handlers.isEmpty()
}

/** Per-event-type throughput/latency rollup (bounded cardinality; overflow bucketed to "other"). */
@Serializable
data class EventTypeStats(
    val eventType: String,
    val published: Long,
    val p50Ms: Double,
    val p95Ms: Double,
)

/**
 * Events: diagnose "fired but nothing happened — or happened twice". Subscription map is
 * already-free; the publish firehose, handler-failure feed and the silent-loss counters (dead
 * events, dropped emits, async-after-commit failures) arrive once the EventBusInterceptor is wired.
 */
@Serializable
data class EventsSnapshot(
    val subscriptions: List<SubscriptionEntry> = emptyList(),
    val handlerCount: Int = 0,
    /** True when dedup ships as the NoOp store (OFF by default) — a common surprise. */
    val deduplicationDisabled: Boolean = true,
    val dedupStoreType: String? = null,
    val totalPublished: Long = 0,
    val perType: List<EventTypeStats> = emptyList(),
    val handlersInvoked: Long = 0,
    val handlersSucceeded: Long = 0,
    val handlersFailed: Long = 0,
    val deadEvents: Long = 0,
    val droppedEmits: Long = 0,
    val asyncFailures: Long = 0,
    val subscriberCount: Int = 0,
)
