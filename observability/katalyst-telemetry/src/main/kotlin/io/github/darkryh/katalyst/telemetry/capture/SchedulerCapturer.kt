package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import io.github.darkryh.katalyst.scheduler.telemetry.SchedulerTelemetry
import io.github.darkryh.katalyst.telemetry.model.JobEntry
import io.github.darkryh.katalyst.telemetry.model.JobKind
import io.github.darkryh.katalyst.telemetry.model.JobLiveness
import io.github.darkryh.katalyst.telemetry.model.JobRunEntry
import io.github.darkryh.katalyst.telemetry.model.SchedulerSnapshot
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import kotlinx.coroutines.Job

/**
 * Taps the SCHEDULER subsystem — the framework's #1 debugging pain (jobs fire constantly and flood
 * the logs). Reads the live registry maintained by [SchedulerTelemetry], which the scheduler service
 * populates on registration and on every run: what is scheduled, when it fires next, and how each job
 * is performing (run/success/failure/timeout tallies, consecutive-failure streak, recent-duration
 * percentiles, liveness, and whether it is executing right now).
 *
 * When the `SchedulerService` bean is absent (`katalyst-scheduler` off the classpath or
 * `enableScheduler()` not called) the provider returns `null`, so the section reads as disabled.
 */
class SchedulerCapturer : SubsystemCapturer {

    override val id: String = "scheduler"

    override fun install(store: TelemetryStore) {
        store.schedulerProvider = provider@{
            runCatching {
                val container = KatalystContainerProvider.currentOrNull() ?: return@provider null
                container.getOrNull<SchedulerService>() ?: return@provider null

                val now = System.currentTimeMillis()
                val jobs = SchedulerTelemetry.jobs().map { s ->
                    val (p50, p95, max) = s.durationStats()
                    JobEntry(
                        name = s.name,
                        kind = kindOf(s.kind),
                        scheduleDescriptor = s.scheduleDescriptor,
                        tags = s.tags,
                        timeZone = s.timeZone,
                        nextFireEpochMs = s.nextFireEpochMs,
                        liveness = livenessOf(s.job),
                        runCount = s.runCount.get(),
                        successCount = s.successCount.get(),
                        failureCount = s.failureCount.get(),
                        timeoutCount = s.timeoutCount.get(),
                        consecutiveFailures = s.consecutiveFailures,
                        lastOutcome = s.lastOutcome,
                        lastRunEpochMs = s.lastRunEpochMs,
                        lastDurationMs = s.lastDurationMs,
                        p50Ms = p50,
                        p95Ms = p95,
                        maxMs = max,
                        currentlyRunning = s.currentlyRunning,
                        currentRunElapsedMs = if (s.currentlyRunning) now - s.currentRunStartMs else null,
                        recentRuns = s.recentRuns().map { run ->
                            JobRunEntry(run.startedAtEpochMs, run.durationMs, run.outcome, run.error)
                        },
                    )
                }

                SchedulerSnapshot(
                    jobs = jobs,
                    registeredCount = SchedulerTelemetry.registeredCount,
                    dispatcherParallelism = SchedulerTelemetry.dispatcherParallelism,
                    dispatcherInFlight = jobs.count { it.currentlyRunning },
                )
            }.getOrNull()
        }
    }

    private fun kindOf(kind: String): JobKind = when (kind) {
        "CRON" -> JobKind.CRON
        "FIXED_RATE" -> JobKind.FIXED_RATE
        "FIXED_DELAY" -> JobKind.FIXED_DELAY
        "ONE_TIME" -> JobKind.ONE_TIME
        else -> JobKind.UNKNOWN
    }

    private fun livenessOf(job: Job?): JobLiveness = when {
        job == null -> JobLiveness.UNKNOWN
        job.isCancelled -> JobLiveness.CANCELLED
        job.isCompleted -> JobLiveness.COMPLETED
        job.isActive -> JobLiveness.ACTIVE
        else -> JobLiveness.UNKNOWN
    }
}
