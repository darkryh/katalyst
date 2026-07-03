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
 *    [MAX_CLOSE_CODES] distinct keys with overflow folded into "other".
 */
@KatalystInternalApi
object WebSocketTelemetry {

    private const val MAX_ROUTES = 128
    private const val MAX_TRACKED_SESSIONS = 250
    private const val MAX_CLOSE_CODES = 16

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
        if (tracked.size >= MAX_TRACKED_SESSIONS) return null
        val stat = SessionStat("ws-${ids.incrementAndGet()}", path, remote, System.currentTimeMillis())
        tracked[stat.id] = stat
        return stat
    }

    /** Record a session ending; [outcome] is "normal" or "error" (handler threw). */
    fun sessionClosed(path: String, stat: SessionStat?, outcome: String) {
        closed.incrementAndGet()
        active.updateAndGet { if (it > 0) it - 1 else 0 }
        perRoute[path]?.updateAndGet { if (it > 0) it - 1 else 0 }
        if (outcome != "normal") handlerErrors.incrementAndGet()
        val key = if (closeOutcomes.size < MAX_CLOSE_CODES || closeOutcomes.containsKey(outcome)) outcome else "other"
        closeOutcomes.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        stat?.let { tracked.remove(it.id) }
    }

    /** The currently tracked live sessions (bounded read-only copy). */
    fun sessions(): List<SessionStat> = tracked.values.toList()

    fun sessionsPerRoute(): Map<String, Int> =
        perRoute.entries.associate { it.key to it.value.get() }.filterValues { it > 0 }

    fun closeOutcomeCounts(): Map<String, Long> = closeOutcomes.mapValues { it.value.get() }
}
