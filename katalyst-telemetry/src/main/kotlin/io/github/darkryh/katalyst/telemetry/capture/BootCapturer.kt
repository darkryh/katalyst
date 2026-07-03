package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.di.lifecycle.LifecycleStatusReport
import io.github.darkryh.katalyst.telemetry.model.BootPhase
import io.github.darkryh.katalyst.telemetry.model.BootTimeline
import io.github.darkryh.katalyst.telemetry.model.PhaseStatus
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore

/**
 * Taps the DI bootstrap subsystem and exposes the 7-phase startup timeline plus aggregated
 * startup-warning counts — the "won't start / came up then died" view.
 *
 * The whole section is read from the framework's public, process-global read-model
 * [LifecycleStatusReport.snapshot], which is assembled from the `BootstrapProgress` and
 * `StartupWarnings` trackers. That read-model always exists (all 7 phases sit `PENDING` before
 * boot begins), needs no DI container, and performs only pure reads — so this provider is cheap,
 * side-effect-free, and valid at every point in the lifecycle.
 *
 * Deferred (only reachable through `internal` API, left at model defaults):
 * - Per-warning detail ([BootTimeline.warnings]): `StartupWarnings.get()` is `internal` and its
 *   `Warning`/`WarningSeverity` types are nested in an `internal` class, so only the public
 *   critical/warning/info counts are exposed here.
 * - Absolute phase timestamps ([BootPhase.startedAtEpochMs]/[BootPhase.endedAtEpochMs]) and the
 *   live [BootTimeline.elapsedInPhaseMs]/[BootTimeline.actualWallClockMs]: the public read-model
 *   surfaces only per-phase `durationMs` and the aggregate `totalBootstrapTimeMs`.
 */
class BootCapturer : SubsystemCapturer {

    override val id: String = "boot"

    override fun install(store: TelemetryStore) {
        store.bootProvider = { readTimeline() }
    }

    private fun readTimeline(): BootTimeline? =
        runCatching {
            val report = LifecycleStatusReport.snapshot()

            val phases = report.lifecycles.map { lifecycle ->
                BootPhase(
                    ref = lifecycle.lifecycleRef,
                    name = lifecycle.name,
                    status = toPhaseStatus(lifecycle.status),
                    durationMs = lifecycle.durationMs.takeIf { it > 0L },
                    message = lifecycle.message,
                )
            }

            BootTimeline(
                phases = phases,
                runningPhaseRef = phases.firstOrNull { it.status == PhaseStatus.RUNNING }?.ref,
                totalBootstrapTimeMs = report.totalBootstrapTimeMs.takeIf { it > 0L },
                criticalCount = report.warningCounts.critical,
                warningCount = report.warningCounts.warning,
                infoCount = report.warningCounts.info,
            )
        }.getOrNull()

    /**
     * The framework exposes phase state as the `.name` of its own `PhaseStatus` enum, whose constants
     * mirror the telemetry model one-for-one. Match by name and fall back to [PhaseStatus.PENDING]
     * so an unexpected value can never throw out of the provider.
     */
    private fun toPhaseStatus(raw: String): PhaseStatus =
        PhaseStatus.entries.firstOrNull { it.name == raw } ?: PhaseStatus.PENDING
}
