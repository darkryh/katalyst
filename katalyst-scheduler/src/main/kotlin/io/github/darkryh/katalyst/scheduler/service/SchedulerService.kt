package io.github.darkryh.katalyst.scheduler.service

import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import io.github.darkryh.katalyst.scheduler.job.asSchedulerHandle
import io.github.darkryh.katalyst.scheduler.telemetry.SchedulerTelemetry
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SchedulerService internal constructor(
    serviceCoroutineContext: CoroutineContext,
    /**
     * Time source for every schedule computation: the fixed-rate anchor, the cron evaluation and
     * the next-fire values published to telemetry. Injected so tests can drive scheduling on
     * virtual time — `delay` advances a test dispatcher's virtual clock, but a system clock does
     * not, so timing behaviour is otherwise only observable through real sleeps.
     */
    private val clock: Clock,
    /**
     * How long [close] waits for in-flight runs after cancelling them. Injected so the bound can be
     * asserted in a test without a multi-second wall-clock wait; applications get [DEFAULT_SHUTDOWN_GRACE].
     */
    private val shutdownGrace: Duration,
) : CoroutineScope, AutoCloseable {

    /**
     * The public constructor. Applications never supply a clock; the system clock is used.
     *
     * `@JvmOverloads` preserves the generated no-argument constructor that a primary constructor
     * with all-default parameters used to emit, keeping the published binary API unchanged.
     */
    @JvmOverloads
    constructor(serviceCoroutineContext: CoroutineContext = Dispatchers.Default) :
        this(serviceCoroutineContext, Clock.systemDefaultZone())

    /**
     * Neither parameter carries a default: a defaulted *internal* constructor makes the compiler
     * emit a synthetic public bridge, which lands in the published `.api` file. The overloads are
     * spelled out instead so the binary API stays exactly what it was.
     */
    internal constructor(serviceCoroutineContext: CoroutineContext, clock: Clock) :
        this(serviceCoroutineContext, clock, DEFAULT_SHUTDOWN_GRACE)

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = serviceCoroutineContext + job

    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)

    fun stop() {
        job.cancel()
    }

    /**
     * Schedules a task at a fixed rate: the period is measured between the **starts** of
     * consecutive executions, never from the end of one run to the start of the next.
     *
     * The next start is an anchor that accumulates exactly one period per tick and is never
     * re-anchored to "now" — the same rule as `ScheduledThreadPoolExecutor`'s fixed-rate trigger.
     * A run that overruns its period therefore leaves the ticks it covered already due, and those
     * fire back-to-back until the schedule has caught up with its original grid. Runs still never
     * overlap: the loop below is a single coroutine, so a slow task delays the schedule rather than
     * forking a second copy of itself. When an overrun should push the whole schedule out instead
     * of being caught up, use [scheduleFixedDelay].
     *
     * The first run happens after [ScheduleConfig.initialDelay]. A [fixedRate] of [Duration.ZERO]
     * schedules a single execution.
     *
     * @param config Configuration for the task (name, delay, timeout, error handling, etc.)
     * @param task The suspend function to execute
     * @param fixedRate Interval between the starts of executions (ZERO for one-time execution)
     * @return SchedulerJobHandle that can be cancelled to stop scheduling
     */
    internal fun schedule(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        fixedRate: Duration
    ): SchedulerJobHandle {
        require(!fixedRate.isNegative()) { "fixedRate must be >= 0" }

        logger.debug("Scheduling task '{}' with initial delay: {}, fixed rate: {}, tags: {}",
            config.taskName, config.initialDelay, fixedRate, config.tags)

        val oneShot = fixedRate == Duration.Companion.ZERO
        SchedulerTelemetry.register(
            config.taskName,
            if (oneShot) "ONE_TIME" else "FIXED_RATE",
            if (oneShot) "one-time" else "every $fixedRate",
            config.tags.toList(),
            config.timeZone.id,
        )

        val handle = launch {
            if (oneShot) {
                // One-time execution
                delay(config.initialDelay)
                executeTaskOnce(config, task)
                return@launch
            }

            // Repeating execution
            logger.debug("Starting repeating task '{}'", config.taskName)
            val periodMillis = fixedRate.inWholeMilliseconds
            // `now` follows the clock but never runs slower than the delays already issued. Under a
            // real clock the clock always wins, so the schedule self-corrects against dispatch
            // latency; under a virtual-time test dispatcher `delay` advances virtual time while a
            // system clock does not, and an anchor trusting the clock alone would drift a whole
            // period per tick there.
            var now = clock.millis()
            var nextStart = now + config.initialDelay.inWholeMilliseconds
            var executionCount = 0L
            while (isActive) {
                val wait = (nextStart - now).coerceAtLeast(0L)
                SchedulerTelemetry.setNextFire(config.taskName, now + wait)
                if (wait > 0) delay(wait)
                now = maxOf(now + wait, clock.millis())

                val shouldContinue = executeTask(config, task, ++executionCount)
                if (!shouldContinue) {
                    logger.info("Stopping task '{}': onError requested no further runs", config.taskName)
                    break
                }
                now = maxOf(now, clock.millis())

                // Accumulate, never re-anchor to `now`: this is what makes a missed tick catch up.
                nextStart += periodMillis
            }
        }.asSchedulerHandle()
        SchedulerTelemetry.attachJob(config.taskName, handle)
        return handle
    }

    /**
     * Schedules a task with a fixed delay between executions.
     * The delay is measured from the end of one execution to the start of the next.
     *
     * **Difference from fixed rate:**
     * - Fixed Rate: period between the START of consecutive executions; an overrun is caught up.
     * - Fixed Delay: delay between the END of one execution and the START of the next; an overrun
     *   simply pushes the whole schedule out, and nothing is ever caught up.
     *
     * The first run happens after [ScheduleConfig.initialDelay].
     *
     * @param config Configuration for the task (name, delay, timeout, error handling, etc.)
     * @param task The suspend function to execute
     * @param fixedDelay Delay after completion before next execution
     * @return SchedulerJobHandle that can be cancelled to stop scheduling
     */
    internal fun scheduleFixedDelay(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        fixedDelay: Duration
    ): SchedulerJobHandle {
        require(fixedDelay > Duration.Companion.ZERO) { "fixedDelay must be > 0" }

        logger.info("Scheduling fixed delay task '{}' with initial delay: {}, fixed delay: {}, tags: {}",
            config.taskName, config.initialDelay, fixedDelay, config.tags)

        SchedulerTelemetry.register(
            config.taskName, "FIXED_DELAY", "every $fixedDelay (fixed delay)",
            config.tags.toList(), config.timeZone.id,
        )

        val handle = launch {
            delay(config.initialDelay)

            var executionCount = 0L
            logger.debug("Starting fixed delay task '{}'", config.taskName)
            while (isActive) {
                val shouldContinue = executeTask(config, task, ++executionCount)
                if (!shouldContinue) {
                    logger.info("Stopping task '{}': onError requested no further runs", config.taskName)
                    break
                }

                // Delay after execution before next run (this is the key difference from fixed rate)
                if (isActive) {
                    logger.debug("Delaying fixed delay task '{}' for {}", config.taskName, fixedDelay)
                    SchedulerTelemetry.setNextFire(
                        config.taskName, clock.millis() + fixedDelay.inWholeMilliseconds,
                    )
                    delay(fixedDelay)
                }
            }
        }.asSchedulerHandle()
        SchedulerTelemetry.attachJob(config.taskName, handle)
        return handle
    }

    /**
     * Schedules a task using a cron expression.
     * Calculates the next execution time dynamically based on the cron schedule.
     *
     * The job **waits, then runs**: the first execution is the first instant matching the
     * expression, never the moment of registration. `cron("nightly", "0 0 2 * * ?")` therefore runs
     * at 02:00 and not additionally at every application boot.
     *
     * The expression is evaluated against the wall clock of [ScheduleConfig.timeZone], not the JVM
     * default zone, so a job configured for `Europe/Madrid` fires at Madrid's 02:00 wherever the
     * process happens to run. [ScheduleConfig.initialDelay] still applies before the first
     * evaluation.
     *
     * Uses a single long-running job that is efficient and easy to cancel.
     *
     * @param config Configuration for the task (name, delay, timeout, timezone, error handling, etc.)
     * @param task The suspend function to execute
     * @param cronExpression The cron expression defining the schedule
     * @return SchedulerJobHandle that can be cancelled to stop scheduling
     */
    internal fun scheduleCron(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        cronExpression: CronExpression
    ): SchedulerJobHandle {
        logger.info("Scheduling cron task '{}' with expression '{}', timezone: {}, tags: {}",
            config.taskName, cronExpression, config.timeZone, config.tags)

        SchedulerTelemetry.register(
            config.taskName, "CRON", cronExpression.toString(),
            config.tags.toList(), config.timeZone.id,
        )

        val handle = launch {
            delay(config.initialDelay)

            var executionCount = 0L
            // The instant we last scheduled, in the job's own zone. It floors the next evaluation so
            // the search always moves forward: during a DST fall-back the same local times occur
            // twice, and evaluating from the clock alone would keep re-matching the repeated hour.
            var lastScheduled: LocalDateTime? = null
            while (isActive) {
                val zoned = ZonedDateTime.now(clock.withZone(config.timeZone))
                val evaluateAfter = lastScheduled?.takeIf { it.isAfter(zoned.toLocalDateTime()) }
                    ?: zoned.toLocalDateTime()
                val nextExecution = cronExpression.nextExecutionAfter(evaluateAfter)
                lastScheduled = nextExecution

                // `atZone` resolves the two zone rule edge cases for us: a local time that falls in
                // a DST gap is shifted forward to the first valid instant after the gap, and one
                // that falls in a DST overlap takes the earlier of the two offsets.
                val fireAt = nextExecution.atZone(config.timeZone).toInstant()
                // Telemetry reads the same instant the delay below is computed from, so the
                // announced next fire and the actual fire can never disagree.
                SchedulerTelemetry.setNextFire(config.taskName, fireAt.toEpochMilli())

                // Wait first, run second: a cron job must never fire at registration.
                val delayMillis = fireAt.toEpochMilli() - clock.millis()
                if (delayMillis > 0) {
                    logger.debug("Next execution of cron task '{}' at {}", config.taskName, nextExecution)
                    delay(delayMillis.milliseconds)
                }

                val shouldContinue = executeTask(config, task, ++executionCount)
                if (!shouldContinue) {
                    logger.info("Stopping task '{}': onError requested no further runs", config.taskName)
                    break
                }
            }
        }.asSchedulerHandle()
        SchedulerTelemetry.attachJob(config.taskName, handle)
        return handle
    }

    /**
     * Executes a single task invocation with timeout, error handling, and callbacks.
     *
     * @return `true` if the caller's repeating loop should continue scheduling further runs,
     *   `false` if [ScheduleConfig.onError] requested that scheduling stop (only consulted
     *   when the execution failed or timed out).
     */
    private suspend fun executeTask(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        executionCount: Long
    ): Boolean {
        SchedulerTelemetry.markRunning(config.taskName)
        val startTime = System.currentTimeMillis()
        try {
            logger.debug("Starting task '{}' (execution #{})", config.taskName, executionCount)

            val result = if (config.maxExecutionTime != null) {
                withTimeoutOrNull(config.maxExecutionTime) {
                    task()
                }
            } else {
                task()
                Unit
            }

            // Null result means timeout occurred
            return if (result == null && config.maxExecutionTime != null) {
                val error = Exception("Task '${config.taskName}' exceeded max execution time: ${config.maxExecutionTime}")
                SchedulerTelemetry.recordOutcome(
                    config.taskName, "timeout", System.currentTimeMillis() - startTime, error.message,
                )
                logger.error("Task '{}' timed out after {}", config.taskName, config.maxExecutionTime, error)
                config.onError(config.taskName, error, executionCount)
            } else {
                val executionTime = (System.currentTimeMillis() - startTime).milliseconds
                SchedulerTelemetry.recordOutcome(config.taskName, "success", executionTime.inWholeMilliseconds)
                logger.debug("Completed task '{}' in {} (execution #{})", config.taskName, executionTime, executionCount)
                config.onSuccess(config.taskName, executionTime)
                true
            }
        } catch (e: CancellationException) {
            // Don't log cancellations, they're expected — clear running without counting a failure.
            SchedulerTelemetry.markStopped(config.taskName)
            throw e
        } catch (e: Exception) {
            SchedulerTelemetry.recordOutcome(
                config.taskName, "failure", System.currentTimeMillis() - startTime, e.stackTraceToString(),
            )
            logger.error("Error running task '{}' (execution #{})", config.taskName, executionCount, e)
            return config.onError(config.taskName, e, executionCount)
        }
    }

    /**
     * Executes a one-time task with error handling and callbacks.
     */
    private suspend fun executeTaskOnce(
        config: ScheduleConfig,
        task: suspend () -> Unit
    ) {
        SchedulerTelemetry.markRunning(config.taskName)
        val startTime = System.currentTimeMillis()
        try {
            logger.debug("Starting one-time task '{}'", config.taskName)

            val result = if (config.maxExecutionTime != null) {
                withTimeoutOrNull(config.maxExecutionTime) {
                    task()
                }
            } else {
                task()
                Unit
            }

            if (result == null && config.maxExecutionTime != null) {
                val error = Exception("Task '${config.taskName}' exceeded max execution time: ${config.maxExecutionTime}")
                SchedulerTelemetry.recordOutcome(
                    config.taskName, "timeout", System.currentTimeMillis() - startTime, error.message,
                )
                logger.error("One-time task '{}' timed out after {}", config.taskName, config.maxExecutionTime, error)
                config.onError(config.taskName, error, 1)
            } else {
                val executionTime = (System.currentTimeMillis() - startTime).milliseconds
                SchedulerTelemetry.recordOutcome(config.taskName, "success", executionTime.inWholeMilliseconds)
                logger.info("Completed one-time task '{}' in {}", config.taskName, executionTime)
                config.onSuccess(config.taskName, executionTime)
            }
        } catch (e: CancellationException) {
            SchedulerTelemetry.markStopped(config.taskName)
            throw e
        } catch (e: Exception) {
            SchedulerTelemetry.recordOutcome(
                config.taskName, "failure", System.currentTimeMillis() - startTime, e.stackTraceToString(),
            )
            logger.error("Error running one-time task '{}'", config.taskName, e)
            config.onError(config.taskName, e, 1)
        }
    }

    /**
     * Cancels every scheduled job and then waits, for at most [shutdownGrace], for the runs already
     * in flight to unwind.
     *
     * [stop] only *signals*: cancellation is asynchronous, so on its own it returns while a run is
     * still on a dispatcher thread. The container closes beans in reverse registration order, which
     * puts `DatabaseFactory` — registered first — last, so a run that outlives this call finishes
     * against a connection pool being torn down underneath it ("HikariDataSource has been closed"):
     * shutdown noise at best, a lost final write at worst. Draining here is what keeps that last
     * write on a live pool.
     *
     * The wait is bounded because a run wedged on a blocking call — an uninterruptible JDBC
     * statement, say — would otherwise hold the process open indefinitely. Past the grace the
     * remaining runs are abandoned and shutdown continues, the same trade a server engine makes
     * with its own shutdown timeout.
     *
     * A latch rather than `runBlocking`: this is called from a plain, non-suspending shutdown
     * thread, and `runBlocking` would install an event loop on it that runs unrelated tasks.
     * [stop] deliberately keeps its signal-only semantics — it is called from inside coroutines,
     * where blocking the caller's thread would deadlock it.
     */
    override fun close() {
        stop()

        val terminated = CountDownLatch(1)
        val registration = job.invokeOnCompletion { terminated.countDown() }
        try {
            if (!terminated.await(shutdownGrace.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                logger.warn(
                    "Scheduler still had runs in flight after {}; continuing shutdown without them",
                    shutdownGrace,
                )
            }
        } catch (interrupted: InterruptedException) {
            // A shutdown path stays interruptible: swallow the wait, but hand the flag back to
            // whoever owns this thread so the rest of the shutdown can honour it.
            Thread.currentThread().interrupt()
        } finally {
            registration.dispose()
        }
    }

    private companion object {
        /**
         * Matches `ServerDeploymentConfiguration.createDefault().shutdownTimeout` (5s), the budget
         * the framework already gives a graceful shutdown. The scheduler is closed as part of that
         * same shutdown, so it must not be allowed to overrun it — and 5s stays comfortably inside
         * the 30s SIGTERM grace a container runtime gives the process by default.
         */
        val DEFAULT_SHUTDOWN_GRACE: Duration = 5.seconds
    }
}
