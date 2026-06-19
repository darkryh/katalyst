# Scheduler

The `katalyst-scheduler` module runs recurring and one-off jobs inside your application. You
declare jobs from a `Service` using the jobs DSL; the scheduler invokes the declaring function
at startup and manages the coroutines. For a walkthrough, see
[Schedule background jobs](../how-to/schedule-jobs.md).

Enable it with `features { enableScheduler() }`.

## requireScheduler and the jobs DSL

Inside a `Service`, `requireScheduler()` returns a `ServiceScheduler`. Its `jobs { … }` method
registers a group of jobs and returns a `SchedulerJobHandle`. Returning that handle from a
parameterless function is the discovery signal.

```kotlin
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import kotlin.time.Duration.Companion.seconds

class MaintenanceJobs(private val service: MaintenanceService) : Service {
    private val scheduler = requireScheduler()

    @Suppress("unused")
    fun jobs() = scheduler.jobs {
        cron("nightly", "0 0 2 * * ?") { service.runNightly() }
        fixedDelay("poll", 5.seconds) { service.poll() }
        fixedRate("heartbeat", 10.seconds) { service.heartbeat() }
        oneTime("warmup") { service.warmup() }
    }
}
```

If the scheduler feature is not enabled, `requireScheduler()` throws
`SchedulerServiceNotAvailableException`.

## Job types

The `SchedulerJobsBuilder` (the receiver of `jobs { … }`) offers four builders. Each accepts
either a name + schedule, or a full `ScheduleConfig`.

| Builder | Signature (name form) | Next run |
|---------|-----------------------|----------|
| `cron` | `cron(name, expression) { … }` | Each time matching the cron expression |
| `fixedDelay` | `fixedDelay(name, delay) { … }` | `delay` after the previous run finishes |
| `fixedRate` | `fixedRate(name, period) { … }` | Every `period` from the previous run's start |
| `oneTime` | `oneTime(name) { … }` | Once, after the configured initial delay |

`cron` also accepts a `CronExpression` instance instead of a string.

## ScheduleConfig

Pass `ScheduleConfig` instead of a bare name for full control.

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `taskName` | `String` | — | Unique job name. |
| `tags` | `Set<String>` | empty | Grouping/filtering tags. |
| `initialDelay` | `Duration` | none | Delay before the first run. |
| `timeZone` | `ZoneId` | system | Time zone for cron evaluation. |
| `maxExecutionTime` | `Duration?` | none | Cancel a run that exceeds this. |
| `onSuccess` | `(taskName, result) -> Unit` | none | Success callback. |
| `onError` | `(taskName, attempt, error) -> Unit` | none | Error callback. |

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
            onError = { task, attempt, error -> log.error("$task attempt $attempt failed", error) }
        ),
        cronExpression = "0 0/15 * * * ?"
    ) { billing.run() }
}
```

## CronExpression and CronValidator

`CronExpression(expression)` parses a cron string; `nextExecutionAfter(dateTime)` computes the
next run.

```kotlin
val expr = CronExpression("0 0 2 * * ?")
val next = expr.nextExecutionAfter(LocalDateTime.now())
```

`CronValidator` validates expressions without constructing one:

```kotlin
CronValidator.isValid("0 0 2 * * ?")     // Boolean
CronValidator.validate("bad expr")        // List of error messages
```

Cron expressions use the six-field form `second minute hour day-of-month month day-of-week`.

## SchedulerJobHandle

The value returned by `jobs { … }`. It is a `kotlinx.coroutines.Job`, so it supports
`cancel()`, `join()`, and the standard `Job` API. The scheduler stops all jobs on application
shutdown.

## SchedulerService

The underlying service (`AutoCloseable`, `CoroutineScope`) that owns job coroutines. It is
managed for you; `stop()` and `close()` shut it down. You normally interact with it only
through `requireScheduler()`.

## Exceptions

All extend `SchedulerException`:

| Exception | Thrown when |
|-----------|-------------|
| `SchedulerServiceNotAvailableException` | `requireScheduler()` is called without the scheduler feature enabled. |
| `SchedulerValidationException` | A job's configuration (e.g., cron expression) is invalid. |
| `SchedulerConfigurationException` | A job is misconfigured. |
| `SchedulerDiscoveryException` | A scheduler registration cannot be discovered/invoked. |
| `SchedulerInvocationException` | A job body throws during invocation. |

## See also

- [Schedule background jobs](../how-to/schedule-jobs.md)
- [Test your application](../how-to/test-applications.md) — verifying jobs run in tests.

