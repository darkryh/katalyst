package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/**
 * Wire-contract version. Bumped whenever the shape of any telemetry type changes in a way the
 * reader cannot tolerate. Both the backend feature and the TUI advertise this; the TUI refuses (or
 * degrades) if the backend's version is newer than it understands.
 */
const val TELEMETRY_SCHEMA_VERSION: Int = 1

/** Coarse traffic-light health used by the always-visible top strip. */
@Serializable
enum class HealthLevel { OK, DEGRADED, ERROR }

/** Severity shared by startup warnings, config issues, and cross-cutting alerts. */
@Serializable
enum class Severity { INFO, WARNING, CRITICAL }

/**
 * The full current state of one backend, assembled on demand from the bounded in-memory store.
 *
 * Every per-subsystem section is nullable so capture can be built out incrementally (shallow-first)
 * and so a subsystem that is not enabled in the app simply reports `null` rather than empty noise.
 */
@Serializable
data class TelemetrySnapshot(
    val schemaVersion: Int = TELEMETRY_SCHEMA_VERSION,
    val capturedAtEpochMs: Long,
    val meta: TelemetryMeta,
    val health: HealthSummary,
    val boot: BootTimeline? = null,
    val wiring: WiringSnapshot? = null,
    val http: HttpSnapshot? = null,
    val webSockets: WebSocketSnapshot? = null,
    val persistence: PersistenceSnapshot? = null,
    val transactions: TransactionSnapshot? = null,
    val migrations: MigrationSnapshot? = null,
    val events: EventsSnapshot? = null,
    val scheduler: SchedulerSnapshot? = null,
    val config: ConfigSnapshot? = null,
)

/**
 * The telemetry layer's own identity and self-observability. `droppedRecords`/`ringOccupancy` let
 * the dashboard show when the store itself is shedding load, so truncation is never silent.
 */
@Serializable
data class TelemetryMeta(
    val appName: String,
    val pid: Long,
    val katalystVersion: String,
    val startedAtEpochMs: Long,
    val telemetryHost: String,
    val telemetryPort: Int,
    val snapshotPath: String? = null,
    /** Approximate bytes the store is allowed to occupy; the enforced upper bound. */
    val memoryBudgetBytes: Long = 0,
    /** Per-stream count of records dropped by the drop-oldest / budget guard since start. */
    val droppedRecords: Map<String, Long> = emptyMap(),
    /** Per-stream current occupancy (records held) so the operator can see headroom. */
    val ringOccupancy: Map<String, Int> = emptyMap(),
)

/** JVM heap/GC snapshot powering the memory-pressure gauge (cheap, always available). */
@Serializable
data class JvmMemory(
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val heapCommittedBytes: Long,
    val nonHeapUsedBytes: Long,
    val gcCount: Long,
    val gcTimeMs: Long,
)

/** One-glance answer to "is the app up and healthy" for the top strip. */
@Serializable
data class HealthSummary(
    val level: HealthLevel,
    val containerReady: Boolean,
    val activeEngineId: String? = null,
    val bootComplete: Boolean,
    val bootFailedPhase: String? = null,
    val criticalCount: Int = 0,
    val warningCount: Int = 0,
    val jvm: JvmMemory? = null,
    /** Short human-readable reasons the level is not OK (worst-first). */
    val notes: List<String> = emptyList(),
)

/** Lifecycle state of a discovered backend, as recorded in its run descriptor file. */
@Serializable
enum class DescriptorStatus { BOOTING, READY, STOPPING, STOPPED }

/**
 * The per-run discovery descriptor the backend writes to a well-known per-user directory
 * (e.g. `$XDG_STATE_HOME/katalyst/run/<pid>.json`). The TUI enumerates these to list attachable
 * backends, connects to `ws://host:telemetryPort/stream?token=wsToken`, and falls back to reading
 * `snapshotPath` for a post-mortem view when the port is dead.
 */
@Serializable
data class RunDescriptor(
    val schemaVersion: Int = TELEMETRY_SCHEMA_VERSION,
    val appName: String,
    val pid: Long,
    val katalystVersion: String,
    val host: String,
    val telemetryPort: Int,
    val wsToken: String,
    val snapshotPath: String? = null,
    val startedAtEpochMs: Long,
    val status: DescriptorStatus,
)

/**
 * Generic envelope for an incremental live-stream delta pushed over `WS /stream`. The payload is a
 * pre-serialized JSON string of the concrete per-stream event so the transport stays free of
 * polymorphic-serialization coupling; the TUI decodes it against the named stream. Streams are
 * introduced in the deepen pass (scheduler runs, event firehose, request log).
 */
@Serializable
data class StreamMessage(
    val stream: String,
    val seq: Long,
    val capturedAtEpochMs: Long,
    val payloadJson: String,
)
