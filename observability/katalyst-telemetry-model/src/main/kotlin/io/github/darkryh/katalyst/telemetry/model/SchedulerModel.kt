package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** How a job is scheduled. */
@Serializable
enum class JobKind { CRON, FIXED_RATE, FIXED_DELAY, ONE_TIME, UNKNOWN }

/** Liveness of a job's underlying coroutine. */
@Serializable
enum class JobLiveness { ACTIVE, WAITING_INITIAL_DELAY, COMPLETED, CANCELLED, UNKNOWN }

/**
 * One recorded execution of a scheduled job. [error] is null for successful runs; for failures it
 * carries the exception (message + truncated stack trace) and for timeouts the timeout description —
 * captured at the moment the run ended, bounded at the recording site so history can never grow.
 */
@Serializable
data class JobRunEntry(
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val outcome: String,
    val error: String? = null,
)

/**
 * One scheduled job with its identity, schedule, next-fire countdown, run tallies, and a bounded
 * ring of its most recent executions ([recentRuns], newest first) for per-run drill-down.
 */
@Serializable
data class JobEntry(
    val name: String,
    val kind: JobKind,
    val scheduleDescriptor: String,
    val tags: List<String> = emptyList(),
    val timeZone: String? = null,
    val nextFireEpochMs: Long? = null,
    val liveness: JobLiveness = JobLiveness.UNKNOWN,
    val runCount: Long = 0,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val timeoutCount: Long = 0,
    val consecutiveFailures: Int = 0,
    val lastOutcome: String? = null,
    val lastRunEpochMs: Long? = null,
    val lastDurationMs: Long? = null,
    val p50Ms: Double = 0.0,
    val p95Ms: Double = 0.0,
    val maxMs: Double = 0.0,
    val currentlyRunning: Boolean = false,
    val currentRunElapsedMs: Long? = null,
    val recentRuns: List<JobRunEntry> = emptyList(),
)

/**
 * Scheduler: the black box that only speaks through log spam, turned into a live registry — every
 * job, its next-fire countdown, run/fail/timeout tallies, per-run history, and the fire-rate that
 * names the flooder. Fed live by the scheduler service through its telemetry registry.
 */
@Serializable
data class SchedulerSnapshot(
    val jobs: List<JobEntry> = emptyList(),
    val discoveredCount: Int = 0,
    val registeredCount: Int = 0,
    val rejectedCandidates: Map<String, String> = emptyMap(),
    val dispatcherInFlight: Int = 0,
    val dispatcherParallelism: Int = 0,
)
