# Schedule background jobs

Katalyst's scheduler runs recurring and one-off work inside your application. You declare
jobs from a service using the jobs DSL; the scheduler invokes them at startup and manages
the underlying coroutines. This guide covers the four job types and their options.

## Enable the scheduler

```kotlin
features { enableScheduler() }
```

Add the `katalyst-scheduler` dependency if it is not already present.

## Declare jobs from a service

Inside a `Service`, call `requireScheduler()` and register jobs in a function. The function
returns a `SchedulerJobHandle`, which is the signal Katalyst uses to invoke it at startup.

```kotlin
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import kotlin.time.Duration.Companion.seconds

class CleanupJobs(private val cleanupService: CleanupService) : Service {
    private val scheduler = requireScheduler()

    fun cleanupJobs() = scheduler.jobs {
        cron("cleanup-expired", "0 0 * * * ?") {
            cleanupService.cleanupExpired()
        }

        fixedDelay("sync-users", 30.seconds) {
            cleanupService.syncUsers()
        }
    }
}
```

## Choose a job type

The jobs DSL offers four scheduling strategies:

| Type | DSL call | When the first run starts | When the next run starts |
|------|----------|---------------------------|--------------------------|
| Cron | `cron(name, expression) { … }` | At the next matching time — **not** at registration | At each subsequent matching time |
| Fixed delay | `fixedDelay(name, delay) { … }` | Immediately, after `initialDelay` | `delay` after the previous run **finishes** |
| Fixed rate | `fixedRate(name, period) { … }` | Immediately, after `initialDelay` | Every `period` from the previous run's **start** |
| One-time | `oneTime(name) { … }` | Once, after `initialDelay` | Never |

A job never overlaps itself: each is a single coroutine that finishes one run before considering
the next trigger.

Registering a cron job does not run it. `cron("nightly-report", "0 0 2 * * ?")` runs at 02:00,
not at every application boot.

### fixedRate vs fixedDelay when a run is slow

`fixedRate` measures the period from the **start** of the previous run, so the task's own duration
is not added to the period. When a run overruns its period, the ticks it covered are still due and
fire back-to-back (one at a time) until the schedule is back on its original grid.

`fixedDelay` measures from the **end** of the previous run, so a slow run simply pushes the whole
schedule out and there is always a fixed gap of idle time between runs. Choose `fixedDelay` when
an overrun should delay the schedule rather than trigger a catch-up burst.

```kotlin
fun jobs() = scheduler.jobs {
    cron("nightly-report", "0 0 2 * * ?") { report.generate() }
    fixedDelay("poll-queue", 5.seconds) { queue.drain() }
    fixedRate("heartbeat", 10.seconds) { health.ping() }
    oneTime("warm-cache") { cache.warm() }
}
```

Cron expressions are validated at registration. An invalid expression fails startup with an
`IllegalArgumentException` from `CronExpression`. Expressions use the six-field form
`second minute hour day-of-month month day-of-week`, and every field must be numeric — day-of-week
names (`MON-FRI`), `L`, `#`, `W` and `@daily`-style macros are not supported. See the
[cron format](../reference/scheduler.md#cronexpression-and-cronvalidator) in the reference for the
full list.

## Configure a job

For tags, time zone, initial delay, a maximum execution time, or success/error callbacks,
pass a `ScheduleConfig` instead of a bare name:

```kotlin
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes

fun jobs() = scheduler.jobs {
    cron(
        config = ScheduleConfig(
            taskName = "billing.run",
            tags = setOf("prod"),
            timeZone = ZoneId.of("UTC"),
            maxExecutionTime = 5.minutes,
            onError = { task, error, runs ->
                log.error("$task failed on run $runs", error)
                true // keep scheduling; return false to stop the job
            }
        ),
        expression = "0 0/15 * * * ?"
    ) {
        billing.run()
    }
}
```

`timeZone` is the zone the cron expression is evaluated in, not just a label: the job above fires
at 15-minute marks of UTC wherever the process runs. Every `ScheduleConfig` field is documented in
the [scheduler reference](../reference/scheduler.md#scheduleconfig).

## Control the lifecycle

`scheduler.jobs { … }` returns a `SchedulerJobHandle`, which is a `kotlinx.coroutines.Job`.
You can cancel a registration group if you hold the handle. The scheduler stops cleanly when
the application shuts down.

## Related

- [Scheduler reference](../reference/scheduler.md) — the jobs DSL, `ScheduleConfig`,
  `CronExpression`, and exceptions.
- [Test your application](test-applications.md) — verifying scheduled jobs run in tests.

