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

| Type | DSL call | When the next run starts |
|------|----------|--------------------------|
| Cron | `cron(name, expression) { … }` | At each time matching the cron expression |
| Fixed delay | `fixedDelay(name, delay) { … }` | `delay` after the previous run **finishes** |
| Fixed rate | `fixedRate(name, period) { … }` | Every `period` from the previous run's **start** |
| One-time | `oneTime(name) { … }` | Once, after the configured initial delay |

```kotlin
fun jobs() = scheduler.jobs {
    cron("nightly-report", "0 0 2 * * ?") { report.generate() }
    fixedDelay("poll-queue", 5.seconds) { queue.drain() }
    fixedRate("heartbeat", 10.seconds) { health.ping() }
    oneTime("warm-cache") { cache.warm() }
}
```

Cron expressions are validated at registration. An invalid expression fails startup with a
`SchedulerValidationException`. See the
[cron format](../reference/scheduler.md#cronexpression-and-cronvalidator) in the reference.

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
            onError = { task, attempt, error -> log.error("$task failed on $attempt", error) }
        ),
        cronExpression = "0 0/15 * * * ?"
    ) {
        billing.run()
    }
}
```

Every `ScheduleConfig` field is documented in the
[scheduler reference](../reference/scheduler.md#scheduleconfig).

## Control the lifecycle

`scheduler.jobs { … }` returns a `SchedulerJobHandle`, which is a `kotlinx.coroutines.Job`.
You can cancel a registration group if you hold the handle. The scheduler stops cleanly when
the application shuts down.

## Related

- [Scheduler reference](../reference/scheduler.md) — the jobs DSL, `ScheduleConfig`,
  `CronExpression`, and exceptions.
- [Test your application](test-applications.md) — verifying scheduled jobs run in tests.

