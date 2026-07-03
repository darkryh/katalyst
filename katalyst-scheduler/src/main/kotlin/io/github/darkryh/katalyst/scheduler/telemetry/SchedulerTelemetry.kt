package io.github.darkryh.katalyst.scheduler.telemetry

import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/**
 * Process-global, bounded registry of scheduled jobs and their live run metrics.
 *
 * The scheduler is otherwise a black box that only speaks through log spam: every run counter,
 * duration and outcome is computed inside a job coroutine, logged, and thrown away. This holder
 * retains a bounded view of it so the telemetry layer (and the terminal UI) can show what is
 * scheduled, when it fires next, and how each job is performing — replacing the log flood that is the
 * framework's #1 debugging pain.
 *
 * Framework-internal (`@KatalystInternalApi`): written only by [io.github.darkryh.katalyst.scheduler.service.SchedulerService]
 * and read only by the telemetry capturer. It is a pure side-channel — recording never affects task
 * execution. Memory is bounded by construction: at most [MAX_JOBS] job entries (app-controlled
 * cardinality, capped as a backstop), and each keeps only a fixed [RECENT_DURATIONS]-slot ring of
 * recent durations plus a fixed [RECENT_RUNS]-slot ring of run records (error text capped at
 * [MAX_ERROR_CHARS]), never a growing history.
 */
@KatalystInternalApi
object SchedulerTelemetry {

    private const val MAX_JOBS = 512
    private const val RECENT_DURATIONS = 64
    private const val RECENT_RUNS = 25
    private const val MAX_ERROR_CHARS = 1500

    /** One finished execution: when it started, how long it ran, how it ended, and why it failed. */
    @KatalystInternalApi
    class JobRun internal constructor(
        val startedAtEpochMs: Long,
        val durationMs: Long,
        val outcome: String,
        val error: String?,
    )

    /** Live metrics for one scheduled job, keyed by task name. All fields are read-model only. */
    @KatalystInternalApi
    class JobStat internal constructor(val name: String) {
        @Volatile var kind: String = "UNKNOWN"
        @Volatile var scheduleDescriptor: String = ""
        @Volatile var tags: List<String> = emptyList()
        @Volatile var timeZone: String? = null

        val runCount = AtomicLong(0)
        val successCount = AtomicLong(0)
        val failureCount = AtomicLong(0)
        val timeoutCount = AtomicLong(0)

        @Volatile var consecutiveFailures: Int = 0
        @Volatile var lastOutcome: String? = null
        @Volatile var lastRunEpochMs: Long? = null
        @Volatile var lastDurationMs: Long? = null
        @Volatile var nextFireEpochMs: Long? = null
        @Volatile var currentlyRunning: Boolean = false
        @Volatile var currentRunStartMs: Long = 0L

        /** The launched coroutine, for read-only liveness (isActive/isCompleted/isCancelled). */
        @Volatile var job: Job? = null

        private val durations = LongArray(RECENT_DURATIONS)
        private var durIndex = 0
        private var durFilled = 0
        private val durLock = Any()

        private val runs = ArrayDeque<JobRun>(RECENT_RUNS)
        private val runsLock = Any()

        internal fun recordDuration(ms: Long) = synchronized(durLock) {
            durations[durIndex] = ms
            durIndex = (durIndex + 1) % RECENT_DURATIONS
            if (durFilled < RECENT_DURATIONS) durFilled++
        }

        internal fun recordRun(run: JobRun) = synchronized(runsLock) {
            if (runs.size >= RECENT_RUNS) runs.removeFirst()
            runs.addLast(run)
        }

        /** The most recent finished runs, newest first (bounded read-only copy). */
        fun recentRuns(): List<JobRun> = synchronized(runsLock) { runs.reversed() }

        /** (p50, p95, max) over the recent-durations ring, in ms; zeros when no runs yet. */
        fun durationStats(): Triple<Double, Double, Double> = synchronized(durLock) {
            if (durFilled == 0) return Triple(0.0, 0.0, 0.0)
            val copy = durations.copyOf(durFilled)
            copy.sort()
            fun pct(p: Double): Double {
                val idx = (ceil(p * copy.size).toInt().coerceIn(1, copy.size)) - 1
                return copy[idx].toDouble()
            }
            Triple(pct(0.50), pct(0.95), copy.last().toDouble())
        }
    }

    private val jobs = ConcurrentHashMap<String, JobStat>()

    @Volatile
    var registeredCount: Int = 0
        private set

    /** Approximation of the Dispatchers.Default parallelism the scheduler runs on. */
    val dispatcherParallelism: Int = Runtime.getRuntime().availableProcessors()

    /** Snapshot of all registered jobs (read-only copy). */
    fun jobs(): List<JobStat> = jobs.values.toList()

    /** Register (or update) a job's identity + schedule. Capped at [MAX_JOBS] as a memory backstop. */
    fun register(
        name: String,
        kind: String,
        scheduleDescriptor: String,
        tags: List<String>,
        timeZone: String?,
    ): JobStat {
        jobs[name]?.let { existing ->
            existing.kind = kind
            existing.scheduleDescriptor = scheduleDescriptor
            existing.tags = tags
            existing.timeZone = timeZone
            return existing
        }
        // Backstop: never grow the registry past the cap (returns an unregistered, detached stat).
        if (jobs.size >= MAX_JOBS) {
            return JobStat(name).also {
                it.kind = kind; it.scheduleDescriptor = scheduleDescriptor; it.tags = tags; it.timeZone = timeZone
            }
        }
        val stat = JobStat(name).also {
            it.kind = kind; it.scheduleDescriptor = scheduleDescriptor; it.tags = tags; it.timeZone = timeZone
        }
        jobs[name] = stat
        registeredCount = jobs.size
        return stat
    }

    fun attachJob(name: String, job: Job) {
        jobs[name]?.job = job
    }

    fun setNextFire(name: String, epochMs: Long) {
        jobs[name]?.nextFireEpochMs = epochMs
    }

    /** Clear the running flag without counting an outcome (e.g. the job coroutine was cancelled). */
    fun markStopped(name: String) {
        jobs[name]?.currentlyRunning = false
    }

    /** Mark a run as started (increments the run count). */
    fun markRunning(name: String) {
        val stat = jobs[name] ?: return
        stat.currentlyRunning = true
        stat.currentRunStartMs = System.currentTimeMillis()
        stat.runCount.incrementAndGet()
    }

    /**
     * Record a run outcome: one of "success", "failure", "timeout". [errorDetail] (failure message +
     * stack trace, or the timeout description) is truncated to [MAX_ERROR_CHARS] here so the run
     * ring's memory bound holds no matter what the caller passes.
     */
    fun recordOutcome(name: String, outcome: String, durationMs: Long, errorDetail: String? = null) {
        val stat = jobs[name] ?: return
        when (outcome) {
            "success" -> { stat.successCount.incrementAndGet(); stat.consecutiveFailures = 0 }
            "timeout" -> { stat.timeoutCount.incrementAndGet(); stat.consecutiveFailures += 1 }
            else -> { stat.failureCount.incrementAndGet(); stat.consecutiveFailures += 1 }
        }
        val endedAt = System.currentTimeMillis()
        stat.lastOutcome = outcome
        stat.lastRunEpochMs = endedAt
        stat.lastDurationMs = durationMs
        stat.recordDuration(durationMs)
        val startedAt = if (stat.currentRunStartMs > 0) stat.currentRunStartMs else endedAt - durationMs
        stat.recordRun(JobRun(startedAt, durationMs, outcome, errorDetail?.take(MAX_ERROR_CHARS)))
        stat.currentlyRunning = false
    }
}
