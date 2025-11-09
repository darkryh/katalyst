package com.ead.katalyst.scheduler.service

import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.cron.CronExpression
import com.ead.katalyst.scheduler.job.SchedulerJobHandle
import com.ead.katalyst.scheduler.job.asSchedulerHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
     * Schedules a task with fixed rate execution.
     * Delay is measured between START of executions.
     *
     * @param config Configuration for the task (name, delay, timeout, error handling, etc.)
     * @param task The suspend function to execute
     * @param fixedRate Interval between executions (ZERO for one-time execution)
     * @return SchedulerJobHandle that can be cancelled to stop scheduling
     */
    fun schedule(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        fixedRate: Duration
    ): SchedulerJobHandle {
        require(!fixedRate.isNegative()) { "fixedRate must be >= 0" }

        logger.debug("Scheduling task '{}' with initial delay: {}, fixed rate: {}, tags: {}",
            config.taskName, config.initialDelay, fixedRate, config.tags)

        return launch {
            delay(config.initialDelay)

            if (fixedRate == Duration.Companion.ZERO) {
                // One-time execution
                executeTaskOnce(config, task)
            } else {
                // Repeating execution
                var executionCount = 0L
                logger.debug("Starting repeating task '{}'", config.taskName)
                while (isActive) {
                    executeTask(config, task, ++executionCount)
                    delay(fixedRate)
                }
            }
        }.asSchedulerHandle()
    }

    /**
     * Schedules a task with a fixed delay between executions.
     * The delay is measured from the end of one execution to the start of the next.
     *
     * **Difference from fixed rate:**
     * - Fixed Rate: delay between START of executions
     * - Fixed Delay: delay between END of one execution and START of next
     *
     * @param config Configuration for the task (name, delay, timeout, error handling, etc.)
     * @param task The suspend function to execute
     * @param fixedDelay Delay after completion before next execution
     * @return SchedulerJobHandle that can be cancelled to stop scheduling
     */
    fun scheduleFixedDelay(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        fixedDelay: Duration
    ): SchedulerJobHandle {
        require(fixedDelay > Duration.Companion.ZERO) { "fixedDelay must be > 0" }

        logger.info("Scheduling fixed delay task '{}' with initial delay: {}, fixed delay: {}, tags: {}",
            config.taskName, config.initialDelay, fixedDelay, config.tags)

        return launch {
            delay(config.initialDelay)

            var executionCount = 0L
            logger.debug("Starting fixed delay task '{}'", config.taskName)
            while (isActive) {
                executeTask(config, task, ++executionCount)

                // Delay after execution before next run (this is the key difference from fixed rate)
                if (isActive) {
                    logger.debug("Delaying fixed delay task '{}' for {}", config.taskName, fixedDelay)
                    delay(fixedDelay)
                }
            }
        }.asSchedulerHandle()
    }

    /**
     * Schedules a task using a cron expression.
     * Calculates the next execution time dynamically based on the cron schedule.
     *
     * Uses a single long-running job that is efficient and easy to cancel.
     *
     * @param config Configuration for the task (name, delay, timeout, timezone, error handling, etc.)
     * @param task The suspend function to execute
     * @param cronExpression The cron expression defining the schedule
     * @return SchedulerJobHandle that can be cancelled to stop scheduling
     */
    fun scheduleCron(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        cronExpression: CronExpression
    ): SchedulerJobHandle {
        logger.info("Scheduling cron task '{}' with expression '{}', timezone: {}, tags: {}",
            config.taskName, cronExpression, config.timeZone, config.tags)

        return launch {
            delay(config.initialDelay)

            var executionCount = 0L
            while (isActive) {
                executeTask(config, task, ++executionCount)

                // Calculate next execution time
                if (isActive) {
                    val now = LocalDateTime.now()
                    val nextExecution = cronExpression.nextExecutionAfter(now)
                    val delayMillis = java.time.Duration.between(now, nextExecution).toMillis()

                    if (delayMillis > 0) {
                        logger.debug("Next execution of cron task '{}' at {}", config.taskName, nextExecution)
                        delay(delayMillis.milliseconds)
                    }
                }
            }
        }.asSchedulerHandle()
    }

    /**
     * Executes a single task invocation with timeout, error handling, and callbacks.
     */
    private suspend fun executeTask(
        config: ScheduleConfig,
        task: suspend () -> Unit,
        executionCount: Long
    ) {
        try {
            logger.debug("Starting task '{}' (execution #{})", config.taskName, executionCount)

            val startTime = System.currentTimeMillis()
            val result = if (config.maxExecutionTime != null) {
                withTimeoutOrNull(config.maxExecutionTime) {
                    task()
                }
            } else {
                task()
                Unit
            }

            // Null result means timeout occurred
            if (result == null && config.maxExecutionTime != null) {
                val error = Exception("Task '${config.taskName}' exceeded max execution time: ${config.maxExecutionTime}")
                logger.error("Task '{}' timed out after {}", config.taskName, config.maxExecutionTime, error)
                config.onError(config.taskName, error, executionCount)
            } else {
                val executionTime = (System.currentTimeMillis() - startTime).milliseconds
                logger.debug("Completed task '{}' in {} (execution #{})", config.taskName, executionTime, executionCount)
                config.onSuccess(config.taskName, executionTime)
            }
        } catch (e: CancellationException) {
            // Don't log cancellations, they're expected
            throw e
        } catch (e: Exception) {
            logger.error("Error running task '{}' (execution #{})", config.taskName, executionCount, e)
            config.onError(config.taskName, e, executionCount)
        }
    }

    /**
     * Executes a one-time task with error handling and callbacks.
     */
    private suspend fun executeTaskOnce(
        config: ScheduleConfig,
        task: suspend () -> Unit
    ) {
        try {
            logger.debug("Starting one-time task '{}'", config.taskName)

            val startTime = System.currentTimeMillis()
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
                logger.error("One-time task '{}' timed out after {}", config.taskName, config.maxExecutionTime, error)
                config.onError(config.taskName, error, 1)
            } else {
                val executionTime = (System.currentTimeMillis() - startTime).milliseconds
                logger.info("Completed one-time task '{}' in {}", config.taskName, executionTime)
                config.onSuccess(config.taskName, executionTime)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error running one-time task '{}'", config.taskName, e)
            config.onError(config.taskName, e, 1)
        }
    }

    override fun close() { stop() }
}