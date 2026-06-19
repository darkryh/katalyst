# Scheduler reference

`katalyst-scheduler` runs recurring/one-off jobs. Enable with `features { enableScheduler() }`.
Jobs are declared from a `Service` and discovered by returning a `SchedulerJobHandle`.

## requireScheduler + jobs DSL

```kotlin
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import kotlin.time.Duration.Companion.seconds

class MaintenanceJobs(private val service: MaintenanceService) : Service {
    private val scheduler = requireScheduler()   // ServiceScheduler

    @Suppress("unused")
    fun jobs() = scheduler.jobs {                // returns SchedulerJobHandle — the discovery signal
        cron("nightly", "0 0 2 * * ?") { service.runNightly() }
        fixedDelay("poll", 5.seconds) { service.poll() }
        fixedRate("heartbeat", 10.seconds) { service.heartbeat() }
        oneTime("warmup") { service.warmup() }
    }
}
```

`requireScheduler()` is an extension on `Service`. If the scheduler feature is off it throws
`SchedulerServiceNotAvailableException`. A parameterless function returning the `SchedulerJobHandle`
is what makes Katalyst invoke it at startup.

## Job builders (SchedulerJobsBuilder)

Each builder has a name form and a `ScheduleConfig` form.

| Builder | Name form | Next run |
|---------|-----------|----------|
| `cron` | `cron(name, "expr") { }` or `cron(name, CronExpression(...)) { }` | Each match of the cron expression |
| `fixedDelay` | `fixedDelay(name, delay) { }` | `delay` after the previous run **finishes** |
| `fixedRate` | `fixedRate(name, period) { }` | Every `period` from the previous run's **start** |
| `oneTime` | `oneTime(name) { }` | Once, after the configured initial delay |

Durations are `kotlin.time.Duration` (`5.seconds`, `10.minutes`).

## ScheduleConfig

`io.github.darkryh.katalyst.scheduler.config.ScheduleConfig`:

| Field | Type | Meaning |
|-------|------|---------|
| `taskName` | `String` | Unique job name. |
| `tags` | `Set<String>` | Grouping/filtering tags. |
| `initialDelay` | `Duration` | Delay before first run. |
| `timeZone` | `ZoneId` | Time zone for cron evaluation. |
| `maxExecutionTime` | `Duration?` | Cancel a run exceeding this. |
| `onSuccess` | `(taskName, result) -> Unit` | Success callback. |
| `onError` | `(taskName, attempt, error) -> Unit` | Error callback. |

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

## CronExpression / CronValidator

`io.github.darkryh.katalyst.scheduler.cron.*`. Six-field form:
`second minute hour day-of-month month day-of-week`.

```kotlin
val expr = CronExpression("0 0 2 * * ?")
val next = expr.nextExecutionAfter(java.time.LocalDateTime.now())

CronValidator.isValid("0 0 2 * * ?")   // Boolean
CronValidator.validate("bad")           // List<String> of errors
```

Invalid expressions fail at registration with `SchedulerValidationException`.

## SchedulerJobHandle / SchedulerService

`scheduler.jobs { }` returns `SchedulerJobHandle`, which is a `kotlinx.coroutines.Job`
(`cancel()`, `join()`, …). The underlying `SchedulerService` (`AutoCloseable`, `CoroutineScope`)
owns the coroutines and shuts down with the app; interact via `requireScheduler()`.

## Exceptions (all extend SchedulerException)

| Exception | When |
|-----------|------|
| `SchedulerServiceNotAvailableException` | `requireScheduler()` without the feature enabled |
| `SchedulerValidationException` | Invalid job config (e.g. cron) |
| `SchedulerConfigurationException` | Misconfigured job |
| `SchedulerDiscoveryException` | Registration cannot be discovered/invoked |
| `SchedulerInvocationException` | Job body throws |
