package com.ead.katalyst.services.service

import com.ead.katalyst.services.cron.CronExpression
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SchedulerService(
    serviceCoroutineContext: CoroutineContext = Dispatchers.Default
) : CoroutineScope, AutoCloseable {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = serviceCoroutineContext + job

    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)

    fun stop() {
        job.cancel()
    }

    /**
     * Schedules a task with fixed rate and interval.
     *
     * @param taskName Name of the task for logging
     * @param task The suspend function to execute
     * @param initialDelay Delay before first execution
     * @param interval Interval between executions (ZERO for one-time execution)
     */
    fun schedule(taskName: String, task: suspend () -> Unit, initialDelay: Duration, interval: Duration) {
        logger.debug("Scheduling task '{}' with initial delay: {}, interval: {}", taskName, initialDelay, interval)
        launch {
            delay(initialDelay)

            if (interval == Duration.ZERO) {
                // One-time execution
                try {
                    logger.debug("Starting one-time task '{}'", taskName)
                    task()
                    logger.info("Completed one-time task '{}'", taskName)
                } catch (e: Exception) {
                    logger.error("Error running one-time task '{}'", taskName, e)
                }
            } else {
                // Repeating execution
                logger.debug("Starting repeating task '{}'", taskName)
                while (isActive) {
                    try {
                        task()
                        logger.debug("Completed scheduled task '{}'", taskName)
                    } catch (e: Exception) {
                        logger.error("Error running scheduled task '{}'", taskName, e)
                    }
                    delay(interval)
                }
            }
        }
    }

    /**
     * Schedules a task with a fixed delay between executions.
     * The delay is measured from the end of one execution to the start of the next.
     *
     * This is more efficient than recursively scheduling jobs because:
     * - Only one Job per task (no accumulation)
     * - Proper loop-based execution
     * - Easy to cancel and cleanup
     *
     * **Difference from fixed rate:**
     * - Fixed Rate: delay between START of executions
     * - Fixed Delay: delay between END of one execution and START of next
     *
     * @param taskName Name of the task for logging
     * @param task The suspend function to execute
     * @param initialDelay Delay before first execution
     * @param fixedDelay Delay after completion before next execution
     */
    fun scheduleFixedDelay(
        taskName: String,
        task: suspend () -> Unit,
        initialDelay: Duration,
        fixedDelay: Duration
    ) {
        logger.info("Scheduling fixed delay task '{}' with initial delay: {}, fixed delay: {}", taskName, initialDelay, fixedDelay)
        launch {
            delay(initialDelay)

            while (isActive) {
                try {
                    logger.debug("Starting fixed delay task '{}'", taskName)
                    task()
                    logger.debug("Completed fixed delay task '{}'", taskName)
                } catch (e: Exception) {
                    logger.error("Error running fixed delay task '{}'", taskName, e)
                }

                // Delay after execution before next run (this is the key difference from fixed rate)
                if (isActive) {
                    logger.debug("Delaying fixed delay task '{}' for {}", taskName, fixedDelay)
                    delay(fixedDelay)
                }
            }
        }
    }

    /**
     * Schedules a task using a cron expression.
     * Uses a single long-running job that calculates the next execution time dynamically.
     *
     * This is more efficient than recursively scheduling jobs because:
     * - Only one Job per task (no accumulation)
     * - Dynamic next-execution calculation
     * - Easy to cancel and cleanup
     *
     * @param taskName Name of the task for logging
     * @param task The suspend function to execute
     * @param cronExpression The cron expression defining the schedule
     * @param initialDelay Delay before first execution
     */
    fun scheduleCron(
        taskName: String,
        task: suspend () -> Unit,
        cronExpression: CronExpression,
        initialDelay: Duration
    ) {
        logger.info("Scheduling cron task '{}' with expression '{}'", taskName, cronExpression)
        launch {
            delay(initialDelay)

            while (isActive) {
                try {
                    logger.debug("Starting cron task '{}'", taskName)
                    task()
                    logger.debug("Completed cron task '{}'", taskName)
                } catch (e: Exception) {
                    logger.error("Error running cron task '{}'", taskName, e)
                }

                // Calculate next execution time
                if (isActive) {
                    val now = LocalDateTime.now()
                    val nextExecution = cronExpression.nextExecutionAfter(now)
                    val delayMillis = java.time.Duration.between(now, nextExecution).toMillis()

                    if (delayMillis > 0) {
                        logger.debug("Next execution of cron task '{}' at {}", taskName, nextExecution)
                        delay(delayMillis.milliseconds)
                    }
                }
            }
        }
    }

    override fun close() {
        job.cancel()
    }
}