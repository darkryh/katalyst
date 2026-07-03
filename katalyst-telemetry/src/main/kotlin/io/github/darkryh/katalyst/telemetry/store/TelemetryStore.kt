package io.github.darkryh.katalyst.telemetry.store

import io.github.darkryh.katalyst.telemetry.model.BootTimeline
import io.github.darkryh.katalyst.telemetry.model.ConfigSnapshot
import io.github.darkryh.katalyst.telemetry.model.EventsSnapshot
import io.github.darkryh.katalyst.telemetry.model.HealthLevel
import io.github.darkryh.katalyst.telemetry.model.HealthSummary
import io.github.darkryh.katalyst.telemetry.model.HttpSnapshot
import io.github.darkryh.katalyst.telemetry.model.MigrationSnapshot
import io.github.darkryh.katalyst.telemetry.model.PersistenceSnapshot
import io.github.darkryh.katalyst.telemetry.model.SchedulerSnapshot
import io.github.darkryh.katalyst.telemetry.model.TelemetryMeta
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.telemetry.model.TransactionSnapshot
import io.github.darkryh.katalyst.telemetry.model.WebSocketSnapshot
import io.github.darkryh.katalyst.telemetry.model.WiringSnapshot
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/** Immutable identity of the running backend, supplied once by the feature at boot. */
data class TelemetryIdentity(
    val appName: String,
    val pid: Long,
    val katalystVersion: String,
    val startedAtEpochMs: Long,
    val host: String,
    val port: Int,
    val snapshotPath: String? = null,
    val memoryBudgetBytes: Long = 0,
)

/**
 * Process-global, bounded telemetry store. It is a *composition point*, not a monolith: each
 * subsystem contributes its snapshot section through a nullable provider slot (set by a
 * [io.github.darkryh.katalyst.telemetry.capture.SubsystemCapturer]), and [snapshot] assembles the
 * envelope on demand. Providers read the framework's already-computed state lazily, so an unenabled
 * subsystem simply leaves its slot null and reports nothing.
 *
 * Live-stream capture (deepen pass) uses [ring] to obtain named [RingBuffer]s whose occupancy and
 * drop counts are reported back in [TelemetryMeta] — keeping the store honest about its own bounds.
 *
 * Every provider is invoked inside a guard so one failing capturer can never crash a snapshot.
 */
class TelemetryStore(private val identity: TelemetryIdentity) {

    private val logger = LoggerFactory.getLogger("TelemetryStore")

    // --- Per-subsystem snapshot providers (set by capturers during install) ---
    @Volatile var bootProvider: (() -> BootTimeline?)? = null
    @Volatile var wiringProvider: (() -> WiringSnapshot?)? = null
    @Volatile var httpProvider: (() -> HttpSnapshot?)? = null
    @Volatile var webSocketProvider: (() -> WebSocketSnapshot?)? = null
    @Volatile var persistenceProvider: (() -> PersistenceSnapshot?)? = null
    @Volatile var transactionProvider: (() -> TransactionSnapshot?)? = null
    @Volatile var migrationProvider: (() -> MigrationSnapshot?)? = null
    @Volatile var eventsProvider: (() -> EventsSnapshot?)? = null
    @Volatile var schedulerProvider: (() -> SchedulerSnapshot?)? = null
    @Volatile var configProvider: (() -> ConfigSnapshot?)? = null

    /** Optional override for the health strip; when null a conservative summary is derived. */
    @Volatile var healthProvider: (() -> HealthSummary)? = null

    // --- Named bounded rings for live streams (deepen pass) ---
    // Ring names MUST be low-cardinality constants (one per stream), NEVER per-request/event/job ids.
    // The cap is a backstop: names beyond it fold into a single shared overflow ring, so the registry
    // itself can never grow without bound even if a future instrument keys a ring by a hot id.
    private val rings = ConcurrentHashMap<String, RingBuffer<*>>()
    private val maxNamedRings = 64
    private val overflowRingName = "__overflow__"

    /** Obtain (or create) a named, fixed-capacity ring for a live stream. */
    @Suppress("UNCHECKED_CAST")
    fun <T> ring(name: String, capacity: Int): RingBuffer<T> {
        rings[name]?.let { return it as RingBuffer<T> }
        val key = if (rings.size >= maxNamedRings && !rings.containsKey(name)) {
            logger.warn(
                "Telemetry ring registry hit its {}-name cap; folding '{}' into the overflow ring. " +
                    "Ring names must be low-cardinality constants, not per-request/event ids.",
                maxNamedRings, name,
            )
            overflowRingName
        } else {
            name
        }
        return rings.computeIfAbsent(key) { RingBuffer<Any?>(capacity) } as RingBuffer<T>
    }

    /** Assemble the current full snapshot. Never throws: a broken provider yields a null section. */
    fun snapshot(): TelemetrySnapshot {
        val jvm = runCatching { JvmMemorySampler.sample() }.getOrNull()
        val health = safe("health") { healthProvider?.invoke() }
            ?: HealthSummary(
                level = HealthLevel.OK,
                containerReady = false,
                bootComplete = false,
                jvm = jvm,
            )
        val meta = TelemetryMeta(
            appName = identity.appName,
            pid = identity.pid,
            katalystVersion = identity.katalystVersion,
            startedAtEpochMs = identity.startedAtEpochMs,
            telemetryHost = identity.host,
            telemetryPort = identity.port,
            snapshotPath = identity.snapshotPath,
            memoryBudgetBytes = identity.memoryBudgetBytes,
            droppedRecords = rings.mapValues { it.value.droppedCount() },
            ringOccupancy = rings.mapValues { it.value.size() },
        )
        return TelemetrySnapshot(
            capturedAtEpochMs = System.currentTimeMillis(),
            meta = meta,
            health = health.copy(jvm = health.jvm ?: jvm),
            boot = safe("boot") { bootProvider?.invoke() },
            wiring = safe("wiring") { wiringProvider?.invoke() },
            http = safe("http") { httpProvider?.invoke() },
            webSockets = safe("webSockets") { webSocketProvider?.invoke() },
            persistence = safe("persistence") { persistenceProvider?.invoke() },
            transactions = safe("transactions") { transactionProvider?.invoke() },
            migrations = safe("migrations") { migrationProvider?.invoke() },
            events = safe("events") { eventsProvider?.invoke() },
            scheduler = safe("scheduler") { schedulerProvider?.invoke() },
            config = safe("config") { configProvider?.invoke() },
        )
    }

    private inline fun <T> safe(section: String, block: () -> T?): T? =
        try {
            block()
        } catch (t: Throwable) {
            logger.debug("Telemetry capturer '{}' failed; section omitted: {}", section, t.message)
            null
        }

    companion object {
        /** The active store for this process, set by the telemetry feature at boot. */
        @Volatile
        var active: TelemetryStore? = null
            private set

        fun activate(store: TelemetryStore): TelemetryStore {
            active = store
            return store
        }

        fun clearActive() {
            active = null
        }
    }
}
