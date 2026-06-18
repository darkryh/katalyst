package io.github.darkryh.katalyst.scheduler

import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import kotlin.time.Duration

/**
 * Creates scheduler job declarations for service-scoped scheduler registration.
 */
fun schedulerJobs(block: SchedulerJobsBuilder.() -> Unit): SchedulerJobDefinitions =
    SchedulerJobsBuilder().apply(block).build()

/**
 * Immutable collection of scheduler job declarations.
 */
class SchedulerJobDefinitions internal constructor(
    private val jobs: List<SchedulerJobDefinition>
) : Iterable<SchedulerJobDefinition> {
    val size: Int get() = jobs.size
    val isEmpty: Boolean get() = jobs.isEmpty()

    internal fun registerWith(scheduler: SchedulerService): List<SchedulerJobHandle> =
        jobs.map { it.registerWith(scheduler) }

    override fun iterator(): Iterator<SchedulerJobDefinition> = jobs.iterator()
}

/**
 * A job declaration that can be registered with [SchedulerService].
 */
sealed interface SchedulerJobDefinition {
    val config: ScheduleConfig

    fun registerWith(scheduler: SchedulerService): SchedulerJobHandle
}

data class CronSchedulerJobDefinition(
    override val config: ScheduleConfig,
    val cronExpression: CronExpression,
    val task: suspend () -> Unit
) : SchedulerJobDefinition {
    override fun registerWith(scheduler: SchedulerService): SchedulerJobHandle =
        scheduler.scheduleCron(
            config = config,
            task = task,
            cronExpression = cronExpression
        )
}

data class FixedRateSchedulerJobDefinition(
    override val config: ScheduleConfig,
    val fixedRate: Duration,
    val task: suspend () -> Unit
) : SchedulerJobDefinition {
    init {
        require(!fixedRate.isNegative()) { "fixedRate must be >= 0" }
    }

    override fun registerWith(scheduler: SchedulerService): SchedulerJobHandle =
        scheduler.schedule(
            config = config,
            task = task,
            fixedRate = fixedRate
        )
}

data class FixedDelaySchedulerJobDefinition(
    override val config: ScheduleConfig,
    val fixedDelay: Duration,
    val task: suspend () -> Unit
) : SchedulerJobDefinition {
    init {
        require(fixedDelay > Duration.ZERO) { "fixedDelay must be > 0" }
    }

    override fun registerWith(scheduler: SchedulerService): SchedulerJobHandle =
        scheduler.scheduleFixedDelay(
            config = config,
            task = task,
            fixedDelay = fixedDelay
        )
}

@DslMarker
annotation class SchedulerJobsDsl

@SchedulerJobsDsl
class SchedulerJobsBuilder internal constructor() {
    private val jobs = mutableListOf<SchedulerJobDefinition>()

    fun cron(
        config: ScheduleConfig,
        expression: String,
        task: suspend () -> Unit
    ) {
        cron(config, CronExpression(expression), task)
    }

    fun cron(
        config: ScheduleConfig,
        expression: CronExpression,
        task: suspend () -> Unit
    ) {
        jobs += CronSchedulerJobDefinition(
            config = config,
            cronExpression = expression,
            task = task
        )
    }

    fun cron(
        taskName: String,
        expression: String,
        tags: Set<String> = emptySet(),
        initialDelay: Duration = Duration.ZERO,
        maxExecutionTime: Duration? = null,
        task: suspend () -> Unit
    ) {
        cron(
            config = ScheduleConfig(
                taskName = taskName,
                tags = tags,
                initialDelay = initialDelay,
                maxExecutionTime = maxExecutionTime
            ),
            expression = expression,
            task = task
        )
    }

    fun fixedRate(
        config: ScheduleConfig,
        every: Duration,
        task: suspend () -> Unit
    ) {
        jobs += FixedRateSchedulerJobDefinition(
            config = config,
            fixedRate = every,
            task = task
        )
    }

    fun fixedRate(
        taskName: String,
        every: Duration,
        tags: Set<String> = emptySet(),
        initialDelay: Duration = Duration.ZERO,
        maxExecutionTime: Duration? = null,
        task: suspend () -> Unit
    ) {
        fixedRate(
            config = ScheduleConfig(
                taskName = taskName,
                tags = tags,
                initialDelay = initialDelay,
                maxExecutionTime = maxExecutionTime
            ),
            every = every,
            task = task
        )
    }

    fun fixedDelay(
        config: ScheduleConfig,
        delay: Duration,
        task: suspend () -> Unit
    ) {
        jobs += FixedDelaySchedulerJobDefinition(
            config = config,
            fixedDelay = delay,
            task = task
        )
    }

    fun fixedDelay(
        taskName: String,
        delay: Duration,
        tags: Set<String> = emptySet(),
        initialDelay: Duration = Duration.ZERO,
        maxExecutionTime: Duration? = null,
        task: suspend () -> Unit
    ) {
        fixedDelay(
            config = ScheduleConfig(
                taskName = taskName,
                tags = tags,
                initialDelay = initialDelay,
                maxExecutionTime = maxExecutionTime
            ),
            delay = delay,
            task = task
        )
    }

    fun oneTime(
        config: ScheduleConfig,
        task: suspend () -> Unit
    ) {
        fixedRate(config, Duration.ZERO, task)
    }

    fun oneTime(
        taskName: String,
        tags: Set<String> = emptySet(),
        initialDelay: Duration = Duration.ZERO,
        maxExecutionTime: Duration? = null,
        task: suspend () -> Unit
    ) {
        oneTime(
            config = ScheduleConfig(
                taskName = taskName,
                tags = tags,
                initialDelay = initialDelay,
                maxExecutionTime = maxExecutionTime
            ),
            task = task
        )
    }

    internal fun build(): SchedulerJobDefinitions =
        SchedulerJobDefinitions(jobs.toList())
}
