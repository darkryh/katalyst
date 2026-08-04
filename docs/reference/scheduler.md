# Scheduler

The `katalyst-scheduler` module runs recurring and one-off jobs inside your application. You
declare jobs from a `Service` using the jobs DSL; the scheduler invokes the declaring function
at startup and manages the coroutines. For a walkthrough, see
[Schedule background jobs](../how-to/schedule-jobs.md).

Enable it with `features { enableScheduler() }`.

## requireScheduler and the jobs DSL

Inside a `Service`, `requireScheduler()` returns a `ServiceScheduler`. Its `jobs { … }` method
registers a group of jobs and returns a `SchedulerJobHandle`. Returning that handle from a public
function is the discovery signal.

```kotlin
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import kotlin.time.Duration.Companion.seconds

class MaintenanceJobs(private val service: MaintenanceService) : Service {
    private val scheduler = requireScheduler()

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

### The discovery contract

When the application is ready, the framework scans every registered `Service` and invokes each
method that matches all of:

- it is declared on a class implementing `Service`;
- it is **public** — a `private` method returning `SchedulerJobHandle` is never registered;
- it returns `SchedulerJobHandle`;
- it reaches the jobs DSL, either in its own body or through a helper on the same class hierarchy
  (up to three calls deep).

The method may be inherited from a base service class, may be `suspend`, and may declare
parameters — they are resolved from the container, and Kotlin default values are honoured.

A method that returns `SchedulerJobHandle` but fails the contract (it is private, or it never
reaches the DSL) is **skipped, not failed**: the application still starts, and the scheduler logs
the service, the method and the reason at `WARN`, then counts it in its completion summary —
`Scheduler initialization completed: N registration(s), N rejection(s), N failure(s)`. A method
that throws *while registering* is a failure, not a rejection, and does stop startup with
`SchedulerInvocationException`.

## Job types

The `SchedulerJobsBuilder` (the receiver of `jobs { … }`) offers four builders. Each accepts
either a name + schedule, or a full `ScheduleConfig`.

| Builder | Signature (name form) | First run | Next run |
|---------|-----------------------|-----------|----------|
| `cron` | `cron(name, expression) { … }` | The next instant matching the expression — **never at registration** | Each subsequent matching instant, evaluated in `timeZone` |
| `fixedDelay` | `fixedDelay(name, delay) { … }` | Immediately, after `initialDelay` | `delay` after the previous run **finishes** |
| `fixedRate` | `fixedRate(name, period) { … }` | Immediately, after `initialDelay` | Every `period` from the previous run's **start** |
| `oneTime` | `oneTime(name) { … }` | Once, after `initialDelay` | Never |

`cron` also accepts a `CronExpression` instance instead of a string.

A job never runs concurrently with itself. Each job is one coroutine that runs its task to
completion before considering the next trigger, so a slow run delays the schedule instead of
piling up overlapping executions.

### fixedRate, overruns, and catching up

`fixedRate` measures the period from the start of one run to the start of the next, so the time
the task itself takes is not added to the period. The next start is an anchor that accumulates one
period per tick and is never re-anchored to "now" — the same rule as the JDK's
`ScheduledThreadPoolExecutor`.

When a run overruns its period, the ticks it covered are still due. They fire back-to-back
(sequentially, never concurrently) as soon as the slow run finishes, until the schedule is back on
its original grid. With a 100 ms period and one run that takes 350 ms, the runs for +100 ms,
+200 ms and +300 ms all start the moment the slow run ends, and the schedule resumes at +400 ms.

If you would rather an overrun push the whole schedule out — no catch-up burst, always a fixed
gap of idle time between runs — use `fixedDelay` instead.

### cron and time zones

A cron job waits for the next matching instant before its first run; registering one at startup
does not run it. `cron("nightly", "0 0 2 * * ?")` runs at 02:00 and only at 02:00.

The expression is evaluated against the wall clock of `ScheduleConfig.timeZone` (defaulting to the
JVM's zone), not the machine's zone, so a job configured for `Europe/Madrid` fires at Madrid's
02:00 wherever the process runs. Across a daylight-saving transition, a matching local time that
falls inside a *gap* is shifted forward to the first valid instant after the gap, and one that
falls inside an *overlap* uses the earlier of the two offsets.

If a run overruns past one or more matching instants, those instants are skipped: cron catches up
to the next future match rather than replaying missed ones.

## ScheduleConfig

Pass `ScheduleConfig` instead of a bare name for full control.

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `taskName` | `String` | — | Unique job name. |
| `tags` | `Set<String>` | empty | Grouping/filtering tags. |
| `initialDelay` | `Duration` | none | Delay before the first run (before the first cron evaluation). |
| `timeZone` | `ZoneId` | system | Time zone the cron expression is evaluated in. |
| `maxExecutionTime` | `Duration?` | none | Cancel a run that exceeds this. |
| `onSuccess` | `(taskName: String, executionTime: Duration) -> Unit` | no-op | Called after each successful run, with how long it took. |
| `onError` | `(taskName: String, exception: Throwable, executionCount: Long) -> Boolean` | `{ _, _, _ -> true }` | Called on a failed or timed-out run. **Return `true` to keep scheduling, `false` to stop the job.** |

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

### Supported syntax

Every field accepts `*`, a single number, a list (`1,3,5`), a range (`1-5`) and a step
(`*/5`, `0-30/5`, `0/2`). `?` is accepted in the day-of-month and day-of-week fields (Quartz
style); at least one of the two must be restricted. Day-of-week is `0-6` with `0` = Sunday.

Some syntax found in Quartz and in Unix crontabs is **not supported**. These fail at registration
(or silently do not mean what they do elsewhere), so avoid them:

| Not supported | Example | Use instead |
|---------------|---------|-------------|
| Day-of-week and month **names** | `MON-FRI`, `JAN` | Numbers: `1-5`, `1` |
| Day-of-week `7` for Sunday | `0 0 2 ? * 7` | `0` |
| Last-day / last-weekday | `L`, `LW`, `6L` | An explicit day number |
| Nth weekday of month | `FRI#3`, `6#3` | — |
| Nearest weekday | `15W` | — |
| Macros | `@daily`, `@hourly`, `@reboot` | The equivalent six-field expression |
| Five-field crontab form | `0 2 * * *` | The six-field form: `0 0 2 * * ?` |

## SchedulerJobHandle

The value returned by `jobs { … }`. It is a `kotlinx.coroutines.Job`, so it supports
`cancel()`, `join()`, and the standard `Job` API. The scheduler stops all jobs on application
shutdown.

## SchedulerService

The underlying service (`AutoCloseable`, `CoroutineScope`) that owns job coroutines. It is
managed for you; `stop()` and `close()` shut it down. You normally interact with it only
through `requireScheduler()`.

The two differ in whether they wait:

| Method | Behaviour |
|--------|-----------|
| `stop()` | Cancels every job and returns immediately. Cancellation is asynchronous, so a run already in flight may still be executing when it returns. Safe to call from inside a coroutine. |
| `close()` | Cancels, then waits up to **5 seconds** for the runs already in flight to unwind before returning. Past that grace they are abandoned and shutdown continues, so a job wedged on a blocking call cannot hold the process open. Blocks the calling thread. |

Shutdown closes beans in reverse registration order, which puts the `DatabaseFactory` last: the
wait in `close()` is what keeps a job's final write on a live connection pool instead of racing
`HikariDataSource has been closed`.

## Exceptions

All extend `SchedulerException`. Only one of them is thrown by the framework today; the rest are
reserved, catchable types kept for forward compatibility.

| Exception | Status |
|-----------|--------|
| `SchedulerInvocationException` | **Thrown** by the runtime-ready initializer when one or more registration methods fail. Each candidate is invoked in isolation, so one failure does not stop the others; a single aggregate exception is thrown once all have been attempted, and per-method causes are in the logs rather than chained onto it. Note this covers **registration**, not a job body that throws at run time — that goes to `onError`. |
| `SchedulerServiceNotAvailableException` | Reserved, never thrown. `requireScheduler()` raises a plain `IllegalStateException` when the feature is not enabled. |
| `SchedulerValidationException` | Reserved, never thrown. An invalid cron expression fails with `IllegalArgumentException` from `CronExpression`'s own `require` checks. |
| `SchedulerDiscoveryException` | Reserved, never thrown. Discovery failures propagate as the underlying reflection error or are aggregated into `SchedulerInvocationException`. |
| `SchedulerConfigurationException` | Reserved, never thrown. |

## See also

- [Schedule background jobs](../how-to/schedule-jobs.md)
- [Test your application](../how-to/test-applications.md) — verifying jobs run in tests.

