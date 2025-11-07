package com.ead.katalyst.services.service

import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Configuration for a scheduled task executed by the Catalyst Scheduler.
 *
 * @property taskName Unique identifier of the task.
 * @property tags Optional metadata for filtering, grouping or observability.
 * @property initialDelay Delay before the first execution of the task.
 * @property timeZone Timezone used for cron-based scheduling. Defaults to system timezone.
 * @property maxExecutionTime Optional timeout for task execution.
 * @property onSuccess Callback for successful task completion.
 * @property onError Callback for handling failures.
 *                   Must return true to continue scheduling or false to stop further runs.
 */
data class ScheduleConfig(
    val taskName: String,
    val tags: Set<String> = emptySet(),

    val initialDelay: Duration = ZERO,
    val timeZone: ZoneId = ZoneId.systemDefault(),

    val maxExecutionTime: Duration? = null,

    val onSuccess: (taskName: String, executionTime: Duration) -> Unit = { _, _ -> },

    val onError: (
        taskName: String,
        exception: Throwable,
        executionCount: Long
    ) -> Boolean = { _, _, _ -> true }
)