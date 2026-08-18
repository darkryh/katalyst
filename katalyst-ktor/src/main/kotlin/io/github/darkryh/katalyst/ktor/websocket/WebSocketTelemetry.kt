package io.github.darkryh.katalyst.ktor.websocket

import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-global, bounded registry of WebSocket routes and live sessions. Written by the
 * instrumented handler wrapper in [KatalystWebSocketRoutes] and read by the telemetry capturer —
 * a pure side-channel that never alters socket behavior.
 *
 * Memory is bounded by construction, deliberately safe under a million concurrent users:
 *  - [active]/[opened]/[closed]/[handlerErrors] are plain counters — every session counts.
 *  - Detailed per-session stats ([SessionStat]) are kept for at most [MAX_TRACKED_SESSIONS]
 *    concurrent sessions; sessions beyond the cap are still COUNTED but not itemized, so the
 *    tracked map can never grow with load.
 *  - Route paths cap at [MAX_ROUTES] (app-shape cardinality), close outcomes at
 *    [MAX_CLOSE_CODES] distinct keys with overflow folded into [OVERFLOW_OUTCOME].
 *
 * Both bounded maps admit entries through an atomic slot reservation rather than by reading the
 * map's size. `map.size < CAP` is check-then-act on a ConcurrentHashMap and every socket runs it, so
 * N sessions crossing the boundary together would each be admitted. The overflow bucket also
 * occupies one of the [MAX_CLOSE_CODES] slots rather than being a free extra one, so folding cannot
 * push the map to `MAX_CLOSE_CODES + 1` keys the moment it saturates.
 */
@KatalystInternalApi
object WebSocketTelemetry {

    private const val MAX_ROUTES = 128
    private const val MAX_TRACKED_SESSIONS = 250
    private const val MAX_CLOSE_CODES = 16

    /** Reserved outcome key: every outcome past [MAX_CLOSE_CODES] folds into this one bucket. */
    private const val OVERFLOW_OUTCOME = "other"

    /** Live counters for one tracked session. Frame/byte fields are written per frame. */
    @KatalystInternalApi
    class SessionStat internal constructor(
        val id: String,
        val path: String,
        val remote: String?,
        val openedAtEpochMs: Long,
    ) {
        val framesIn = AtomicLong(0)
        val framesOut = AtomicLong(0)
        val bytesIn = AtomicLong(0)
        val bytesOut = AtomicLong(0)
    }

    private val ids = AtomicLong(0)
    private val routes = ConcurrentHashMap.newKeySet<String>()
    private val tracked = ConcurrentHashMap<String, SessionStat>()
    private val perRoute = ConcurrentHashMap<String, AtomicInteger>()
    private val closeOutcomes = ConcurrentHashMap<String, AtomicLong>()

    /** Itemized-session slots in use. Claimed by CAS on open, released on close. */
    private val trackedSlots = AtomicInteger(0)

    /** Close-outcome keys admitted so far, excluding the reserved [OVERFLOW_OUTCOME] bucket. */
    private val closeOutcomeSlots = AtomicInteger(0)

    val active = AtomicInteger(0)
    val opened = AtomicLong(0)
    val closed = AtomicLong(0)
    val handlerErrors = AtomicLong(0)

    /** Record a WebSocket route registration (capped; app-shape cardinality). */
    fun registerRoute(path: String) {
        if (routes.size < MAX_ROUTES) routes.add(path)
    }

    fun routes(): List<String> = routes.toList()

    /**
     * Record a session opening. Returns the [SessionStat] to write frame counts into, or null when
     * the tracked-session cap is reached — the session is still counted, just not itemized.
     */
    fun sessionOpened(path: String, remote: String?): SessionStat? {
        opened.incrementAndGet()
        active.incrementAndGet()
        perRoute.computeIfAbsent(path) { AtomicInteger(0) }.incrementAndGet()
        if (!claimTrackedSlot()) return null
        val stat = SessionStat("ws-${ids.incrementAndGet()}", path, remote, System.currentTimeMillis())
        tracked[stat.id] = stat
        return stat
    }

    private fun claimTrackedSlot(): Boolean {
        while (true) {
            val current = trackedSlots.get()
            if (current >= MAX_TRACKED_SESSIONS) return false
            if (trackedSlots.compareAndSet(current, current + 1)) return true
        }
    }

    /** Record a session ending; [outcome] is "normal" or "error" (handler threw). */
    fun sessionClosed(path: String, stat: SessionStat?, outcome: String) {
        closed.incrementAndGet()
        active.updateAndGet { if (it > 0) it - 1 else 0 }
        perRoute[path]?.updateAndGet { if (it > 0) it - 1 else 0 }
        if (outcome != "normal") handlerErrors.incrementAndGet()
        closeOutcomeCounter(outcome).incrementAndGet()
        // Releasing the slot only when the id was actually present keeps a double close harmless.
        stat?.let { if (tracked.remove(it.id) != null) trackedSlots.decrementAndGet() }
    }

    /**
     * The counter [outcome] belongs to, or the shared [OVERFLOW_OUTCOME] bucket once the key space
     * is spent. The bucket is one of the [MAX_CLOSE_CODES] slots, not an extra one.
     */
    private fun closeOutcomeCounter(outcome: String): AtomicLong {
        if (outcome == OVERFLOW_OUTCOME) return overflowOutcomeCounter()
        closeOutcomes[outcome]?.let { return it }
        while (true) {
            val claimed = closeOutcomeSlots.get()
            if (claimed >= MAX_CLOSE_CODES - 1) return overflowOutcomeCounter()
            if (closeOutcomeSlots.compareAndSet(claimed, claimed + 1)) break
        }
        val created = AtomicLong(0)
        val existing = closeOutcomes.putIfAbsent(outcome, created)
        if (existing == null) return created
        // Lost the race for this same outcome; hand the slot back so the cap is not eaten by it.
        closeOutcomeSlots.decrementAndGet()
        return existing
    }

    private fun overflowOutcomeCounter(): AtomicLong =
        closeOutcomes.computeIfAbsent(OVERFLOW_OUTCOME) { AtomicLong(0) }

    /** The currently tracked live sessions (bounded read-only copy). */
    fun sessions(): List<SessionStat> = tracked.values.toList()

    fun sessionsPerRoute(): Map<String, Int> =
        perRoute.entries.associate { it.key to it.value.get() }.filterValues { it > 0 }

    fun closeOutcomeCounts(): Map<String, Long> = closeOutcomes.mapValues { it.value.get() }
}
