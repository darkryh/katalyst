package io.github.darkryh.katalyst.testing.core.schedulerboot

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import java.util.concurrent.atomic.AtomicBoolean

/** Records what the boot actually executed, so the assertions do not rely on logs. */
object SchedulerBootLog {
    val schedulerMethodInvoked = AtomicBoolean(false)
    val applicationHookExecuted = AtomicBoolean(false)

    fun reset() {
        schedulerMethodInvoked.set(false)
        applicationHookExecuted.set(false)
    }
}

/** Task name asserted against [io.github.darkryh.katalyst.scheduler.telemetry.SchedulerTelemetry]. */
const val BOOT_TASK_NAME: String = "testing-core.boot.scheduler-survives-app-ready-hook"

/**
 * An ordinary scheduling service: the scheduler only ever sees it if `SchedulerInitializer`
 * itself survived to run.
 */
class BootScheduledService : Service {

    fun scheduledJobs(): SchedulerJobHandle {
        SchedulerBootLog.schedulerMethodInvoked.set(true)
        // 03:00 on 1 January — far enough away that nothing fires during the test.
        return requireScheduler().jobs {
            cron(taskName = BOOT_TASK_NAME, expression = "0 0 3 1 1 ?") {}
        }
    }
}

/**
 * The application's own ready hook — the whole point of the fixture. It is bound with
 * `ReadyHook` as a secondary type and used to displace the scheduler's own hook from the
 * container index.
 */
class BootApplicationReadyHook : ReadyHook {
    override val id: String = "boot-application-ready-hook"

    override suspend fun onReady() {
        SchedulerBootLog.applicationHookExecuted.set(true)
    }
}
