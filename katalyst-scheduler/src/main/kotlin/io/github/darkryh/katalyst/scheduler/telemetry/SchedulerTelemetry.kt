package io.github.darkryh.katalyst.scheduler.telemetry

import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi
import kotlinx.coroutines.Job
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
 * Framework-internal (`@KatalystInternalApi`): written only by
 * [io.github.darkryh.katalyst.scheduler.service.SchedulerService] and read only by the telemetry
 * capturer. It is a pure side-channel — recording never affects task execution.
 *
 * ## What is bounded, and why it is bounded *there*
 *
 * Memory is bounded by construction, but on the axis that actually costs bytes. Identity and
 * counters are a few hundred bytes per job; a retained stack trace is three kilobytes. Bounding the
 * *number of jobs* therefore buys almost nothing and costs a great deal: it made every job past the
 * limit invisible to the one tool built for diagnosing them, which is the opposite of what a large
 * deployment needs. Nothing else in this space bounds that way — Spring Boot keeps identity plus a
 * last-execution record for every scheduled task with no cap at all, Quartz and Quarkus retain no
 * history whatsoever and delegate to listeners and metrics, and where an in-memory history does
 * exist (Spring's HTTP exchange repository, Celery's Flower) it is bounded by *total entries*.
 *
 * So:
 * - every job keeps identity, counters, a [RECENT_DURATIONS]-slot duration ring and a
 *   [RECENT_RUNS]-slot run ring — small, fixed, and never refused;
 * - retained failure text, the expensive part, is bounded **globally** by
 *   [ERROR_TEXT_BUDGET_CHARS] across all jobs, degrading to a one-line summary rather than
 *   disappearing;
 * - [MAX_JOBS] remains only as a far-off backstop, and reaching it evicts a job that has already
 *   finished rather than refusing the job that is about to run;
 * - reaching it is reported: once at `WARN`, and permanently through [registeredCount], which
 *   counts what the application registered rather than what survived.
 */
@KatalystInternalApi
object SchedulerTelemetry {

    private val logger = LoggerFactory.getLogger("SchedulerTelemetry")

    /**
     * Backstop on tracked jobs. Deliberately far above any realistic job count: it exists so a
     * pathological application cannot grow the registry without limit, not to ration normal use.
     */
    private const val MAX_JOBS = 10_000
    private const val RECENT_DURATIONS = 16
    private const val RECENT_RUNS = 8
    private const val MAX_ERROR_CHARS = 1500

    /**
     * Ceiling on failure text retained across the whole registry (~8 MB of UTF-16). Past it,
     * detail degrades to [ERROR_SUMMARY_CHARS] of the exception's first line: an outcome is still
     * recorded and still says what failed, it just stops carrying the full trace. Without a shared
     * ceiling the worst case is every job failing at once, which is precisely when a scheduler is
     * least able to afford a few hundred megabytes of debugging text.
     */
    private const val ERROR_TEXT_BUDGET_CHARS = 4_000_000L
    private const val ERROR_SUMMARY_CHARS = 120

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

        /**
         * Appends [run] to the ring and returns whatever it displaced, so the caller can return that
         * entry's error text to the global budget. Accounting lives with the caller because the
         * budget is shared: the ring itself only knows what it dropped.
         */
        internal fun recordRun(run: JobRun): JobRun? = synchronized(runsLock) {
            val evicted = if (runs.size >= RECENT_RUNS) runs.removeFirst() else null
            runs.addLast(run)
            evicted
        }

        /** Empties the ring and reports how much error text it was holding, for budget release. */
        internal fun drainRetainedErrorChars(): Long = synchronized(runsLock) {
            val held = runs.sumOf { it.error?.length?.toLong() ?: 0L }
            runs.clear()
            held
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
    private val registrations = AtomicLong(0)
    private val retainedErrorChars = AtomicLong(0)
    private val backstopReported = AtomicBoolean(false)

    /**
     * How many jobs the application has registered — **not** how many are tracked.
     *
     * The two are equal until the [MAX_JOBS] backstop is reached, and diverge afterwards. That
     * divergence is the point: a reader comparing this with `jobs().size` can tell that the view is
     * partial, instead of being handed a plausible-looking list with no indication anything is
     * missing. A job that is evicted and later re-registered counts twice; the value is a
     * registration count, not a distinct-name count.
     */
    @Volatile
    var registeredCount: Int = 0
        private set

    /** Approximation of the Dispatchers.Default parallelism the scheduler runs on. */
    val dispatcherParallelism: Int = Runtime.getRuntime().availableProcessors()

    /** Snapshot of all registered jobs (read-only copy). */
    fun jobs(): List<JobStat> = jobs.values.toList()

    /**
     * Drops every tracked job and resets the counters.
     *
     * The registry is process-global, so tests that exercise its bounds have to start from a known
     * state; nothing in the framework calls this.
     */
    internal fun reset() {
        jobs.clear()
        registrations.set(0)
        retainedErrorChars.set(0)
        backstopReported.set(false)
        registeredCount = 0
    }

    /**
     * Register (or update) a job's identity + schedule.
     *
     * Re-registering a tracked name updates it in place. A genuinely new job is admitted even at
     * [MAX_JOBS], by evicting a job whose coroutine has already finished — the newest registration
     * is the one about to run, so refusing it in favour of a job that is over is exactly backwards.
     * Only when every tracked job is still live is the newcomer dropped, and that is reported.
     */
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

        val stat = JobStat(name).also {
            it.kind = kind; it.scheduleDescriptor = scheduleDescriptor; it.tags = tags; it.timeZone = timeZone
        }
        // Counted whether or not it survives: this is what makes truncation visible.
        registeredCount = registrations.incrementAndGet().toInt()

        if (jobs.size >= MAX_JOBS && !evictFinishedJob()) {
            reportBackstop()
            return stat // detached: the job runs, it is simply not tracked
        }
        jobs[name] = stat
        return stat
    }

    /**
     * Evicts the least recently active job whose coroutine has finished, returning whether one was
     * found. A job still running, or one never attached to a coroutine (so not provably over), is
     * never a candidate: losing a live job's metrics would be a worse outcome than dropping the
     * newcomer.
     */
    private fun evictFinishedJob(): Boolean {
        var victim: JobStat? = null
        for (stat in jobs.values) {
            if (stat.currentlyRunning) continue
            val job = stat.job ?: continue
            if (job.isActive) continue
            val incumbent = victim
            if (incumbent == null || (stat.lastRunEpochMs ?: 0L) < (incumbent.lastRunEpochMs ?: 0L)) {
                victim = stat
            }
        }

        val evicted = victim ?: return false
        if (!jobs.remove(evicted.name, evicted)) return false
        retainedErrorChars.addAndGet(-evicted.drainRetainedErrorChars())
        logger.debug("Scheduler telemetry evicted finished job '{}' to make room", evicted.name)
        return true
    }

    /** Reports the backstop once at WARN; afterwards at DEBUG, so a full registry cannot spam. */
    private fun reportBackstop() {
        val message = "Scheduler telemetry is tracking {} job(s), its maximum, and every tracked job is " +
            "still live; {} registration(s) have been observed. Jobs past this point run normally but " +
            "are not reported — telemetry is a debugging side-channel and never affects scheduling."
        if (backstopReported.compareAndSet(false, true)) {
            logger.warn(message, jobs.size, registeredCount)
        } else {
            logger.debug(message, jobs.size, registeredCount)
        }
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
     * stack trace, or the timeout description) is truncated to [MAX_ERROR_CHARS] and charged against
     * the shared [ERROR_TEXT_BUDGET_CHARS], so the retained total holds no matter what callers pass
     * or how many jobs fail at once.
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
        val retained = admitErrorText(errorDetail)
        val evicted = stat.recordRun(JobRun(startedAt, durationMs, outcome, retained))
        // Charge after the ring accepted it, credit whatever it displaced: the budget tracks what
        // is actually still reachable, so text that has rolled out becomes available again.
        retained?.let { retainedErrorChars.addAndGet(it.length.toLong()) }
        evicted?.error?.let { retainedErrorChars.addAndGet(-it.length.toLong()) }

        stat.currentlyRunning = false
    }

    /**
     * Full detail while the shared budget allows it, a one-line summary once it does not.
     *
     * The summary tier is deliberately *not* charged against [ERROR_TEXT_BUDGET_CHARS]. Charging it
     * would mean that once the budget is spent every subsequent failure records no reason at all —
     * and a failure storm is exactly when "which exception" is the thing worth knowing. It needs no
     * budget of its own because it is already bounded by construction: at most [MAX_JOBS] jobs, each
     * holding at most [RECENT_RUNS] entries of at most [ERROR_SUMMARY_CHARS] characters. It is still
     * *accounted*, so that text rolling out of a ring returns capacity to the full-detail tier.
     */
    private fun admitErrorText(errorDetail: String?): String? {
        if (errorDetail == null) return null

        val full = errorDetail.take(MAX_ERROR_CHARS)
        if (retainedErrorChars.get() + full.length <= ERROR_TEXT_BUDGET_CHARS) return full

        return errorDetail.lineSequence().firstOrNull()?.take(ERROR_SUMMARY_CHARS)?.ifEmpty { null }
    }
}
