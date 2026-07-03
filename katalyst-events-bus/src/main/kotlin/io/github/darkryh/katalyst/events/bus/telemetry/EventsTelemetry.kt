package io.github.darkryh.katalyst.events.bus.telemetry

import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.bus.EventBusInterceptor
import io.github.darkryh.katalyst.events.bus.PublishResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/**
 * Process-global, bounded event-bus metrics. Populated by [TelemetryEventBusInterceptor] (a default
 * interceptor the framework wires onto the bus) and read by the telemetry capturer.
 * Framework-internal (`@KatalystInternalApi`), a pure read-only side-channel.
 *
 * Bounded by construction: a few atomic counters plus a per-event-type map capped at [MAX_TYPES]
 * (overflow folded into an "other" bucket), each type keeping only a fixed [RECENT] duration ring.
 */
@KatalystInternalApi
object EventsTelemetry {

    private const val MAX_TYPES = 128
    private const val RECENT = 32

    val totalPublished = AtomicLong(0)
    val handlersInvoked = AtomicLong(0)
    val handlersSucceeded = AtomicLong(0)
    val handlersFailed = AtomicLong(0)
    val deadEvents = AtomicLong(0)

    @KatalystInternalApi
    class TypeStat internal constructor(val eventType: String) {
        val count = AtomicLong(0)
        private val durations = LongArray(RECENT)
        private var index = 0
        private var filled = 0
        private val lock = Any()

        internal fun record(ms: Long) = synchronized(lock) {
            durations[index] = ms
            index = (index + 1) % RECENT
            if (filled < RECENT) filled++
        }

        /** (p50, p95) recent publish durations in ms for this event type. */
        fun percentiles(): Pair<Double, Double> = synchronized(lock) {
            if (filled == 0) return 0.0 to 0.0
            val copy = durations.copyOf(filled)
            copy.sort()
            fun pct(p: Double): Double = copy[(ceil(p * copy.size).toInt().coerceIn(1, copy.size)) - 1].toDouble()
            pct(0.50) to pct(0.95)
        }
    }

    private val types = ConcurrentHashMap<String, TypeStat>()

    fun recordPublish(eventType: String, invoked: Int, succeeded: Int, failed: Int, durationMs: Long) {
        totalPublished.incrementAndGet()
        handlersInvoked.addAndGet(invoked.toLong())
        handlersSucceeded.addAndGet(succeeded.toLong())
        handlersFailed.addAndGet(failed.toLong())
        if (invoked == 0) deadEvents.incrementAndGet()

        val key = if (types.size >= MAX_TYPES && !types.containsKey(eventType)) "other" else eventType
        types.computeIfAbsent(key) { TypeStat(it) }.also {
            it.count.incrementAndGet()
            it.record(durationMs)
        }
    }

    fun types(): List<TypeStat> = types.values.toList()
}

/**
 * Default, always-on event-bus interceptor that feeds [EventsTelemetry]. Observation-only: it never
 * aborts publishing and never throws (the bus also guards interceptor calls). Wired onto the
 * framework-provided `ApplicationEventBus` so the event firehose is captured with zero user setup.
 */
@KatalystInternalApi
object TelemetryEventBusInterceptor : EventBusInterceptor {
    override suspend fun afterPublish(event: DomainEvent, result: PublishResult) {
        runCatching {
            EventsTelemetry.recordPublish(
                eventType = event.eventType(),
                invoked = result.handlersInvoked,
                succeeded = result.handlersSucceeded,
                failed = result.handlersFailed,
                durationMs = result.durationMs,
            )
        }
    }
}
