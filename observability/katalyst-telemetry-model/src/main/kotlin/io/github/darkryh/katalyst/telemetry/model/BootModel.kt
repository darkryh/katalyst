package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** Status of one bootstrap phase, mirroring the framework's LifecycleInfo state machine. */
@Serializable
enum class PhaseStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

/** One of the 7 bootstrap phases with its timing and message. */
@Serializable
data class BootPhase(
    val ref: String,
    val name: String,
    val status: PhaseStatus,
    val startedAtEpochMs: Long? = null,
    val endedAtEpochMs: Long? = null,
    val durationMs: Long? = null,
    val message: String? = null,
)

/** A non-fatal startup warning (degraded/opt-out state) with a remediation hint. */
@Serializable
data class StartupWarning(
    val category: String,
    val message: String,
    val severity: Severity,
    val hint: String? = null,
)

/**
 * The 7-phase bootstrap timeline plus aggregated warnings — the "won't start / came up then died"
 * screen. Fed directly from LifecycleStatusReport.snapshot(); nearly free to capture.
 */
@Serializable
data class BootTimeline(
    val phases: List<BootPhase>,
    val runningPhaseRef: String? = null,
    val elapsedInPhaseMs: Long? = null,
    val totalBootstrapTimeMs: Long? = null,
    val actualWallClockMs: Long? = null,
    val warnings: List<StartupWarning> = emptyList(),
    val criticalCount: Int = 0,
    val warningCount: Int = 0,
    val infoCount: Int = 0,
)
