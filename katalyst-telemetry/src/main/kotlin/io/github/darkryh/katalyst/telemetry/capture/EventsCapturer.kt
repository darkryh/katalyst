package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.events.bus.EventHandlerRegistry
import io.github.darkryh.katalyst.events.bus.deduplication.EventDeduplicationStore
import io.github.darkryh.katalyst.events.bus.deduplication.NoOpEventDeduplicationStore
import io.github.darkryh.katalyst.events.bus.telemetry.EventsTelemetry
import io.github.darkryh.katalyst.telemetry.model.EventTypeStats
import io.github.darkryh.katalyst.telemetry.model.EventsSnapshot
import io.github.darkryh.katalyst.telemetry.model.SubscriptionEntry
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore

/**
 * Taps the in-process EVENTS subsystem (katalyst-events / katalyst-events-bus).
 *
 * Reads only already-computed, public state at capture time:
 * - The routing table (event type -> subscribed handler classes) and total handler count from the
 *   [EventHandlerRegistry] bean ([EventHandlerRegistry.getAllHandlers], [EventHandlerRegistry.size]).
 * - The deduplication posture from the (optional) [EventDeduplicationStore] bean: the framework wires
 *   no store by default, so absence — or a [NoOpEventDeduplicationStore] — means dedup is OFF.
 *
 * The publish firehose, per-type latency, handler-invoke/success/failure tallies and the silent-loss
 * counters (dead events, dropped emits, async-after-commit failures) live on the dormant
 * `EventBusInterceptor` / private `ApplicationEventBus` state and are NOT publicly readable; they stay
 * defaulted until the deepen pass wires an interceptor. See [EventsSnapshot].
 *
 * Read-only and side-effect-free: resolves beans lazily and returns `null` before boot or when the
 * events feature is disabled.
 */
class EventsCapturer : SubsystemCapturer {

    override val id: String = "events"

    override fun install(store: TelemetryStore) {
        store.eventsProvider = provider@{
            val container = KatalystContainerProvider.currentOrNull() ?: return@provider null

            val registry = container.getOrNull<EventHandlerRegistry>()
            val dedupStore = container.getOrNull<EventDeduplicationStore>()

            // Events feature not wired yet (pre-boot or disabled): report absence rather than an
            // empty-but-present section.
            if (registry == null && dedupStore == null) return@provider null

            val subscriptions = registry?.let(::buildSubscriptions) ?: emptyList()
            val handlerCount = registry?.size() ?: 0

            // No store bean, or the NoOp store, both mean deduplication is OFF (the default).
            val dedupDisabled = dedupStore == null || dedupStore is NoOpEventDeduplicationStore
            val dedupStoreType = dedupStore?.let { it::class.qualifiedName ?: it::class.simpleName }

            val perType = EventsTelemetry.types().map { t ->
                val (p50, p95) = t.percentiles()
                EventTypeStats(eventType = t.eventType, published = t.count.get(), p50Ms = p50, p95Ms = p95)
            }

            EventsSnapshot(
                subscriptions = subscriptions,
                handlerCount = handlerCount,
                deduplicationDisabled = dedupDisabled,
                dedupStoreType = dedupStoreType,
                totalPublished = EventsTelemetry.totalPublished.get(),
                perType = perType,
                handlersInvoked = EventsTelemetry.handlersInvoked.get(),
                handlersSucceeded = EventsTelemetry.handlersSucceeded.get(),
                handlersFailed = EventsTelemetry.handlersFailed.get(),
                deadEvents = EventsTelemetry.deadEvents.get(),
                // droppedEmits / asyncFailures / subscriberCount need deeper private-bus taps.
            )
        }
    }

    /**
     * Fold the flat handler list into a routing table keyed by declared event type. Bounded by the
     * application's static (compile-time) set of handlers; this rebuilds fresh each call and retains
     * nothing across invocations.
     */
    private fun buildSubscriptions(registry: EventHandlerRegistry): List<SubscriptionEntry> {
        val byType = LinkedHashMap<String, MutableList<String>>()
        for (handler in registry.getAllHandlers()) {
            val eventType = handler.eventType.qualifiedName
                ?: handler.eventType.simpleName
                ?: continue
            val handlerName = handler::class.qualifiedName
                ?: handler::class.simpleName
                ?: handler::class.java.name
            byType.getOrPut(eventType) { mutableListOf() }.add(handlerName)
        }
        return byType.map { (eventType, handlers) ->
            SubscriptionEntry(eventType = eventType, handlers = handlers.toList())
        }
    }
}
