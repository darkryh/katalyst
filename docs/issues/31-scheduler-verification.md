# Issue #31 — verification report

> **Status: fixed.** All nine defects below are resolved, each with a regression test that was
> confirmed failing before its fix. The public API is unchanged — `./gradlew apiCheck` passes with
> no `*.api` file modified, and `samples/katalyst-example` still compiles against the published
> artifacts without an edit. See [Fixed by](#fixed-by) at the end.

**Issue:** *SchedulerInitializer is evicted from the Koin instance registry when an application
component binds `ReadyHook`, so no scheduled job is ever registered.*

**Verdict: confirmed.** The reported mechanism is accurate in every particular — the bean-module
registration, the index-key collision, the `override = true` write, the missing
`ReadyHookRegistry` fallback, and the order dependence. Reproduced against the production
`KoinBeanEngine`. Observed on `1.0.0-alpha07` / `master @ a5ffebf`.

Verification also turned up **seven further defects in the scheduler itself**, independent of #31.
They do not cause the total failure described in the issue; they cause jobs to run at the wrong
time, or to be dropped silently one at a time. Each is backed by a failing assertion below.

---

## 1. Issue #31 — the reported root cause

### What the source says

| Step | Location | Fact |
|------|----------|------|
| 1 | `katalyst-scheduler/.../SchedulerModules.kt:19` | `single<ReadyHook> { SchedulerInitializer() }` — the bean's **only** bound type is the marker. |
| 2 | `katalyst-koin-bean/.../KoinBeanEngine.kt:103-114` | `registerDefinitions` calls `registerInstance(instance, primaryType = definition.type, qualifier)` with **no secondary types**, so the only index key written is `ReadyHook:`. |
| 3 | `katalyst-di/.../AutoBindingRegistrar.kt:595-640` | `ReadyHook` has `Cardinality.MULTI`, so for a scanned application hook it lands in `multiBindingTypes` and is passed to `beanEngine.registerInstance` as a secondary type. |
| 4 | `katalyst-koin-bean/.../KoinBeanEngine.kt:77-84` | Every type — primary and secondary — is written with `saveMapping(true, key, factory, logWarning = false)`. `override = true`, warning suppressed. |
| 5 | `katalyst-koin-bean/.../KoinKatalystContainer.kt:27-28` | `getAll` iterates the registry's factories. A factory whose sole key was overwritten is no longer among them. |
| 6 | `katalyst-di/.../ReadyHookRunner.kt:17-26` | The union with `ReadyHookRegistry` cannot rescue it: `ReadyHookRegistry.register` is only reached from `AutoBindingRegistrar.registerInstance` (line 632), and the bean-module path never goes through the registrar. |

Every one of these matches the issue text. Nothing in the chain is speculative.

### Reproduction

`KoinBeanEngine` + the real `SchedulerFeature.provideBeanModules()`, then one application
component bound exactly the way `AutoBindingRegistrar` binds a scanned `ReadyHook`:

```
FAIL  SchedulerInitializer survives an application component binding ReadyHook
      KoinBeanEngine: registering an application ReadyHook must not evict
      SchedulerInitializer, got [AppReadyHook]

PASS  registering the scheduler module last keeps both ReadyHooks
```

Two things this pins down beyond the issue report:

- **`TestKatalystBeanEngine` passes the same test.** The in-memory engine used by
  `katalystTestEnvironment` does not reproduce the eviction, which is why the framework's own
  suite — including `BeanEngineContractTest`, written for issue #16 to pin the two engines to one
  lookup contract — is green while applications break. That contract test covers
  "secondary type reachable via `getAll`" but not "a marker-only definition survives a later
  writer of the same marker".
- **The order dependence is real.** Registering the scheduler module *after* the application hook
  keeps both. Last writer wins the `ReadyHook:` key; the application hook survives either way
  because it is also indexed under its own concrete class.

`samples/katalyst-example` contains no `ReadyHook` implementation, so the sample application never
collides — a third reason the defect is invisible in-repo.

### Notes on the fix (not applied)

Three independent points would each close it; the first is the smallest:

1. Register `SchedulerInitializer` under its own concrete type with `ReadyHook` as a *secondary*
   type, so it keeps a private key — the same reason application hooks survive today.
2. Have `KoinBeanEngine.registerInstance` refuse to overwrite an existing mapping for a
   multibinding marker key (or index those under a synthetic per-instance qualifier), since
   `getAll` is the only meaningful resolution for a `Cardinality.MULTI` type anyway.
3. Have the scheduler module also push the initializer into `ReadyHookRegistry`, restoring the
   fallback the runner already tries to use.

`saveMapping(..., logWarning = false)` is what makes this silent; whichever fix lands, that flag
deserves an audit — it currently suppresses every override notice in the framework.

---

## 2. Further defects found in the scheduler itself

All eight were checked by asserting the *documented* contract and observing the assertion fail.
Reproduction sources are in the appendix.

### 2.1 `fixedRate` behaves identically to `fixedDelay` — **high**

`SchedulerService.schedule` awaits the task and *then* delays the period:

```kotlin
while (isActive) {
    val shouldContinue = executeTask(config, task, ++executionCount)   // awaits completion
    ...
    delay(fixedRate)                                                   // then waits the full period
}
```

Execution time is never compensated, so the period is measured end-to-start. Its own KDoc says
the opposite ("Fixed Rate: delay between START of executions"), as does
`docs/reference/scheduler.md` ("Every `period` from the previous run's start").

```
FAIL  fixed rate measures the period from the start of the previous run
      expected ~300ms between run starts but measured 569ms   (task took 250ms)
```

A 10-second heartbeat whose body takes 8 seconds fires every 18 seconds. This is the most likely
explanation for a job that "runs, but not on schedule".

### 2.2 Cron jobs fire immediately on registration — **high**

`SchedulerService.scheduleCron` executes the task on the first loop iteration and only *then*
computes the next fire time. `initialDelay` defaults to zero, so a job declared
`cron("nightly", "0 0 2 * * ?")` runs at every application boot, then again at 02:00.

```
FAIL  cron job does not fire immediately on registration
      (expression "0 0 3 1 1 ?" — 03:00 on 1 January — fired within 500 ms of registration)
```

### 2.3 `ScheduleConfig.timeZone` is inert for scheduling — **high**

`scheduleCron` computes `LocalDateTime.now()` in the **JVM default zone** and takes the delay from
that. `config.timeZone` is used only to stamp telemetry — and it is applied to a local time that
was already computed in a different zone, so the reported next-fire is wrong by the offset
difference *on top of* the job firing in the wrong zone.

```
FAIL  cron honours the configured time zone
      next fire was 2026-08-03T16:15:00Z but the next top of the hour in
      Asia/Kathmandu is 2026-08-04T03:15:00Z
```

`docs/reference/scheduler.md` documents `timeZone` as "Time zone for cron evaluation".

### 2.4 Cron does not reset lower fields when the day rolls forward — **medium**

`CronExpression.findNextDayInMonth` advances with `current.plusDays(1)` and carries the hour,
minute and second from the candidate. (The month-advance path in `findNextMonthAndDay` *does*
reset them, so the two are inconsistent.) Any expression with a multi-valued hour or minute field
combined with a day restriction skips the early part of the first matching day:

```
FAIL  cron resets hour and minute when the day rolls forward
      "0 0 8-17 * * 1-5" from Saturday 11:30 -> Monday 12:00   (expected Monday 08:00)

FAIL  cron resets minute when the day rolls forward on a stepped minute field
      "0 0/15 * * * 1-5" from Saturday 22:47 -> Monday 23:00   (expected Monday 00:00)
```

Single-valued fields (`0 0 2 * * ?`) are unaffected, which is why the existing cron suite —
extensive as it is — does not catch this.

### 2.5 A scheduler method inherited from a base class is silently dropped — **medium**

`SchedulerInitializer.discoverCandidateMethods` uses `KClass.functions`, which *includes*
inherited members, but `validateCandidatesByBytecode` then looks the method up in
`service::class.java.declaredMethods` and bails with `?: return@filter false` when it is not
declared on the concrete class.

```
FAIL  discovery finds a scheduler method inherited from a base service class
```

### 2.6 A scheduler method that delegates registration is silently dropped — **medium**

The bytecode validator requires the call to `ServiceScheduler.jobs` to appear in the *same* method
that returns the handle. `fun jobs() = buildJobs()` with the DSL one level down is rejected.

```
FAIL  discovery finds a scheduler method that delegates to a helper
```

For 2.5 and 2.6 the only trace is `logger.debug` per candidate plus an aggregate
`logger.info("... no valid scheduler service methods after validation")`. **Nothing is logged at
WARN or ERROR, and no failure is counted** — the job simply never exists. Combined with the
undocumented "must not be `private`" rule (`function.visibility != KVisibility.PRIVATE`), this is
the mechanism by which *some* of an application's jobs work and others do not.

### 2.7 A `suspend` scheduler method aborts application startup — **medium**

`CallableInvoker.callMemberWithDefaults` uses `KFunction.callBy`, which cannot invoke a suspend
function without a continuation. The failure is counted, aggregated, and rethrown as
`SchedulerInvocationException` from a `ReadyHook`, which stops the server — with a message that
carries no cause text:

```
FAIL  discovery invokes a suspend scheduler method
      SchedulerInvocationException: Scheduler invocation encountered 1 error(s):
      SuspendJobService.suspendJob(): null
```

Either support it or reject it at discovery with a message that names the problem.

### 2.8 Well-known `SchedulerService` property injection is dead code — **low**

`AutoBindingRegistrar.kt:658-660` resolves the class by a stale package name:

```kotlin
private val schedulerServiceKClass: KClass<*>? = runCatching {
    Class.forName("io.github.darkryh.katalyst.services.service.SchedulerService").kotlin
}.getOrNull()
```

The class is `io.github.darkryh.katalyst.scheduler.service.SchedulerService`;
`io.github.darkryh.katalyst.services.*` survives only as a legacy *test* package. So
`schedulerServiceKClass` is always `null` and the injection branch at line 185 never runs.

`DependencyAnalyzer` uses the correct FQN (via `KnownPlatformTypes.schedulerServiceKClassOrNull()`)
when building the graph — so the validator records `var scheduler: SchedulerService` as a
satisfied well-known-property dependency that the registrar then never assigns. A `lateinit var`
declared that way throws `UninitializedPropertyAccessException` on first use. The documented path,
`requireScheduler()`, is unaffected.

### Not a defect

`SchedulerService` is an `AutoCloseable` that nothing ever closes — no `stop()`/`close()` call site
exists outside tests. Harmless in practice (the JVM exits at shutdown), but the job coroutines do
outlive `stopKatalystStandalone()`.

---

## 3. Priority

| # | Defect | Effect | Severity |
|---|--------|--------|----------|
| 1 | `ReadyHook:` key eviction (issue #31) | **every** job dead, silently, in any app with its own `ReadyHook` | critical |
| 2.1 | `fixedRate` ≡ `fixedDelay` | period inflated by execution time | high |
| 2.2 | cron fires at registration | nightly/weekly jobs run at every boot | high |
| 2.3 | `timeZone` inert | cron fires in the wrong zone; telemetry disagrees with reality | high |
| 2.4 | lower fields not reset on day rollover | first run of a restricted day is late | medium |
| 2.5 | inherited method dropped | that job silently never exists | medium |
| 2.6 | delegating method dropped | that job silently never exists | medium |
| 2.7 | `suspend` method | startup aborts, message says `null` | medium |
| 2.8 | stale `SchedulerService` FQN | dead injection path; validator disagrees with registrar | low |

Two cross-cutting themes are worth fixing alongside the individual bugs:

- **Silent drops.** #31, 2.5 and 2.6 all fail with no WARN, no ERROR, and no counter. The
  scheduler already reports `"{} registration(s), {} failure(s)"`; discarded candidates belong in
  that count.
- **Test-engine divergence.** `TestKatalystBeanEngine` passes the #31 reproduction that
  `KoinBeanEngine` fails. Until the two agree, a green suite is not evidence about production —
  which is exactly the failure mode `BeanEngineContractTest` was written to prevent after #16.

---

## Fixed by

Landed as four workstreams. Every test listed was written first and confirmed red for its own
reason before the corresponding fix.

| # | Defect | Fix | Guarding test |
|---|--------|-----|---------------|
| 1 | `ReadyHook:` key eviction | `KoinBeanEngine.registerInstance` adds `instance::class` to the definition's secondary types, so every registration keeps a private index key that a marker collision cannot take. Displacement that *would* orphan a definition now logs at WARN. | `BeanEngineContractTest` (3 new invariants, both engines), `SchedulerReadyHookEvictionTest`, `schedulerboot/SchedulerBootReadyHookTest` (real `KoinBeanEngine`, end-to-end) |
| 2.1 | `fixedRate` ≡ `fixedDelay` | `SchedulerService.schedule` uses an accumulating start-to-start anchor (`nextStart += period`, never re-anchored), matching `ScheduledThreadPoolExecutor`. Overruns catch up back-to-back, sequentially. | `TriggerSemanticsTest` |
| 2.2 | cron fires at registration | `scheduleCron` restructured to *evaluate → wait → execute*. | `TriggerSemanticsTest` |
| 2.3 | `timeZone` inert | Cron is evaluated via `ZonedDateTime.now(clock.withZone(config.timeZone))`, and the same instant feeds both the delay and telemetry. | `TriggerSemanticsTest` (uses `Asia/Kathmandu`, +05:45, so a whole-hour zone cannot pass by coincidence) |
| 2.4 | lower fields not reset on day rollover | `CronExpression.findNextDayInMonth` resets hour/minute/second to their first valid values when advancing a day, mirroring the month-advance path. | `CronExpressionPropertyTest` — including a **no-skipped-instant** property that generates multi-valued time fields with day restrictions |
| 2.5 | inherited method dropped | The JVM method is resolved with `KFunction.javaMethod` instead of `declaredMethods.find { name }`. Also fixes overload selection and `internal` (name-mangled) methods. | `SchedulerInitializerDiscoveryContractTest` |
| 2.6 | delegating method dropped | The bytecode validator follows calls to sibling methods within the candidate's own class hierarchy, depth-limited to 3, with a `(owner, name, descriptor)` cycle guard. | `SchedulerInitializerDiscoveryContractTest` (depth 3 accepted, depth 4 rejected, cycle terminates) |
| — | silent rejections | Every rejected candidate logs a WARN naming service, method and reason, and rejections are counted in the summary: `N registration(s), N rejection(s), N failure(s)`. A rejection never fails startup. | `SchedulerInitializerDiscoveryContractTest` (asserts via a log appender) |
| 2.7 | `suspend` method aborts startup | `SchedulerInitializer` resolves arguments through the already-public `ParameterResolver` and calls `callSuspendBy` from `onReady`'s suspend context. Failure text uses `e.message ?: e.toString()`, so it can no longer read `null`. | `SchedulerInitializerDiscoveryContractTest` |
| 2.8 | stale `SchedulerService` FQN | `AutoBindingRegistrar` resolves the class through `KnownPlatformTypes.schedulerServiceKClassOrNull()`, the same source `DependencyAnalyzer` already used. | `AutoBindingRegistrarSchedulerContractTest`, `wellknownproperties/SchedulerPropertyInjectionTest` |

### Found while fixing

**`propertyAlreadyInitialised` caught the wrong exception type.** Fixing 2.8 immediately exposed it:
the method detects an unassigned `lateinit` by reading the property and catching
`UninitializedPropertyAccessException` — but the read goes through reflection, so what actually
arrives is `InvocationTargetException` *wrapping* it. The exception escaped registration and aborted
the entire boot. Well-known injection had therefore only ever worked for nullable `var`s; the first
application to write `lateinit var scheduler: SchedulerService` would have failed to start. Fixed by
inspecting the cause chain, with `AutoBindingRegistrarLateinitPropertyTest` as the guard.

**Documentation defects corrected in the same pass.** `ScheduleConfig.onError` was documented as
`(taskName, attempt, error) -> Unit`; it is `(taskName: String, exception: Throwable,
executionCount: Long) -> Boolean`, where the return value decides whether scheduling continues.
`onSuccess` was wrong too, and both worked examples passed `cronExpression =` to a parameter named
`expression` — so neither snippet compiled. The exceptions table claimed four types are thrown that
the KDoc says are reserved and never thrown, and attributed `SchedulerInvocationException` to a
throwing job body rather than to a failed registration.

### Not fixed — pre-existing, unrelated

`samples/katalyst-example` fails 11 of its 22 tests on `master`, and did so before this work: a
worktree at `a5ffebf` with none of these changes produces the identical failing set.
`StartupHookSmokeTest` declares a `SmokeProbeStartupHook(probe: SmokeProbe)` fixture inside the
scanned package `io.github.darkryh.katalyst.example`, so component discovery picks the hook up and
fails validation on `SmokeProbe`, which is not a discoverable component:

```
Missing dependency: SmokeProbeStartupHook requires SmokeProbe (param 'probe')
```

Validation is fatal, so that one test fixture fails every sample test that calls
`scan("io.github.darkryh.katalyst.example")`. `./samples/validate-samples.sh` is therefore red on
`master` independently of the scheduler. Sample compilation against the published artifacts does
pass, which is what makes it usable as the API-freeze check.

## Appendix — reproduction sources

Both files were run against `master @ a5ffebf` and then removed; drop them back in to re-check, or
keep them as regression tests once the fixes land.

### `katalyst-testing-core/src/test/kotlin/io/github/darkryh/katalyst/testing/core/SchedulerReadyHookEvictionTest.kt`

```kotlin
package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.di.lifecycle.ReadyHook
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.scheduler.SchedulerFeature
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin

class SchedulerReadyHookEvictionTest {

    class AppReadyHook : ReadyHook {
        override val id: String = "app-ready-hook"
        override val order: Int = 60
        override suspend fun onReady() = Unit
    }

    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    private fun withEachEngine(block: (String, KatalystBeanEngine) -> Unit) {
        listOf<Pair<String, () -> KatalystBeanEngine>>(
            "TestKatalystBeanEngine" to { TestKatalystBeanEngine() },
            "KoinBeanEngine" to { KoinBeanEngine },
        ).forEach { (name, factory) ->
            KatalystContainerProvider.reset()
            runCatching { stopKoin() }
            val engine = factory()
            try {
                block(name, engine)
            } finally {
                runCatching { engine.stop() }
                KatalystContainerProvider.reset()
                runCatching { stopKoin() }
            }
        }
    }

    @Test
    fun `SchedulerInitializer survives an application component binding ReadyHook`() {
        withEachEngine { name, engine ->
            val container = engine.start(SchedulerFeature.provideBeanModules(), allowOverrides = true)

            val before = container.getAll(ReadyHook::class).map { it::class.simpleName }
            assertTrue(before.contains("SchedulerInitializer"), "$name: baseline, got $before")

            // Exactly what AutoBindingRegistrar does for a scanned component implementing ReadyHook.
            engine.registerInstance(AppReadyHook(), AppReadyHook::class, listOf(ReadyHook::class), null)

            val after = container.getAll(ReadyHook::class).map { it::class.simpleName }
            assertTrue(
                after.contains("SchedulerInitializer"),
                "$name: registering an application ReadyHook must not evict SchedulerInitializer, got $after"
            )
            assertTrue(after.contains("AppReadyHook"), "$name: got $after")
        }
    }

    @Test
    fun `registering the scheduler module last keeps both ReadyHooks`() {
        withEachEngine { name, engine ->
            val container = engine.start(emptyList(), allowOverrides = true)
            engine.registerInstance(AppReadyHook(), AppReadyHook::class, listOf(ReadyHook::class), null)
            engine.loadModules(SchedulerFeature.provideBeanModules(), allowOverrides = true)

            val hooks = container.getAll(ReadyHook::class).map { it::class.simpleName }
            assertTrue(hooks.contains("SchedulerInitializer"), "$name: got $hooks")
            assertTrue(hooks.contains("AppReadyHook"), "$name: got $hooks")
        }
    }
}
```

### `katalyst-scheduler/src/test/kotlin/io/github/darkryh/katalyst/scheduler/verification/SchedulerBehaviorVerificationTest.kt`

```kotlin
package io.github.darkryh.katalyst.scheduler.verification

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.internal.ServiceRegistry
import io.github.darkryh.katalyst.scheduler.TestSchedulerContainer
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import io.github.darkryh.katalyst.scheduler.lifecycle.SchedulerInitializer
import io.github.darkryh.katalyst.scheduler.service.SchedulerService
import io.github.darkryh.katalyst.scheduler.telemetry.SchedulerTelemetry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Asserts the *documented* scheduler contract, so divergence shows up as a failing assertion. */
class SchedulerBehaviorVerificationTest {

    @BeforeTest
    fun setUp() {
        KatalystContainerProvider.set(
            TestSchedulerContainer(mapOf(SchedulerService::class to SchedulerService()))
        )
        ServiceRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        ServiceRegistry.clear()
        KatalystContainerProvider.reset()
    }

    @Test
    fun `cron resets hour and minute when the day rolls forward`() {
        val cron = CronExpression("0 0 8-17 * * 1-5")
        val saturday = LocalDateTime.now()
            .with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            .withHour(11).withMinute(30).withSecond(0).withNano(0)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek, "fixture must start on a Saturday")

        val next = cron.nextExecutionAfter(saturday)

        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek, "next weekday run is Monday")
        assertEquals(8, next.hour, "first Monday run must be the first valid hour (08)")
        assertEquals(0, next.minute)
    }

    @Test
    fun `cron resets minute when the day rolls forward on a stepped minute field`() {
        val cron = CronExpression("0 0/15 * * * 1-5")
        val saturday = LocalDateTime.now()
            .with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            .withHour(22).withMinute(47).withSecond(0).withNano(0)

        val next = cron.nextExecutionAfter(saturday)

        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(0, next.hour, "Monday's first run is 00:00, not the Saturday hour")
        assertEquals(0, next.minute)
    }

    @Test
    fun `cron job does not fire immediately on registration`() = runBlocking {
        val fired = CompletableDeferred<Unit>()
        val service = object : Service {
            private val s = requireScheduler()
            fun jobs(): SchedulerJobHandle = s.jobs {
                cron("verify.cron.no-immediate-fire", "0 0 3 1 1 ?") { fired.complete(Unit) }
            }
        }
        val handle = service.jobs()
        delay(500)
        assertTrue(!fired.isCompleted, "a cron job must wait for its first matching time")
        handle.cancel()
    }

    @Test
    fun `fixed rate measures the period from the start of the previous run`() = runBlocking {
        val starts = CopyOnWriteArrayList<Long>()
        val done = CompletableDeferred<Unit>()
        val service = object : Service {
            private val s = requireScheduler()
            fun jobs(): SchedulerJobHandle = s.jobs {
                fixedRate(ScheduleConfig(taskName = "verify.fixed-rate.period"), every = 300.milliseconds) {
                    starts += System.currentTimeMillis()
                    if (starts.size >= 3) done.complete(Unit)
                    delay(250)
                }
            }
        }

        val handle = service.jobs()
        withTimeout(10_000) { done.await() }
        handle.cancel()

        val gap = starts[1] - starts[0]
        assertTrue(gap < 400, "expected ~300ms between run starts but measured ${gap}ms")
    }

    @Test
    fun `cron honours the configured time zone`() = runBlocking {
        // +05:45 — not a whole-hour offset, so "top of the hour" differs from the JVM default zone.
        val zone = java.time.ZoneId.of("Asia/Kathmandu")
        val ran = CompletableDeferred<Unit>()
        val service = object : Service {
            private val s = requireScheduler()
            fun jobs(): SchedulerJobHandle = s.jobs {
                cron(
                    config = ScheduleConfig(
                        taskName = "verify.cron.timezone",
                        timeZone = zone,
                        onSuccess = { _, _ -> ran.complete(Unit) },
                    ),
                    expression = "0 0 * * * ?",
                ) {}
            }
        }

        val handle = service.jobs()
        withTimeout(5_000) { ran.await() }
        delay(200)
        handle.cancel()

        val nextFire = SchedulerTelemetry.jobs().first { it.name == "verify.cron.timezone" }.nextFireEpochMs
        val expected = java.time.ZonedDateTime.now(zone)
            .plusHours(1).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()

        assertTrue(
            nextFire != null && kotlin.math.abs(nextFire - expected) < 60_000,
            "cron must be evaluated in ScheduleConfig.timeZone: next fire was " +
                "${nextFire?.let(java.time.Instant::ofEpochMilli)} but the next top of the hour in " +
                "$zone is ${java.time.Instant.ofEpochMilli(expected)}"
        )
    }

    @Test
    fun `AutoBindingRegistrar resolves the SchedulerService class it injects`() {
        val asUsedByRegistrar = runCatching {
            Class.forName("io.github.darkryh.katalyst.services.service.SchedulerService")
        }
        val actual = runCatching {
            Class.forName("io.github.darkryh.katalyst.scheduler.service.SchedulerService")
        }

        assertTrue(actual.isSuccess, "sanity: the real scheduler class must load")
        assertTrue(
            asUsedByRegistrar.isSuccess,
            "AutoBindingRegistrar looks up a class that does not exist"
        )
    }

    @Test
    fun `discovery finds a scheduler method inherited from a base service class`() = runBlocking {
        val service = InheritedJobService()
        ServiceRegistry.register(service)
        SchedulerInitializer().onReady()
        assertTrue(service.registered, "a scheduler method on a base class must be discovered")
    }

    @Test
    fun `discovery invokes a suspend scheduler method`() = runBlocking {
        val service = SuspendJobService()
        ServiceRegistry.register(service)
        SchedulerInitializer().onReady()
        assertTrue(service.registered, "a suspend scheduler method must be invocable")
    }

    @Test
    fun `discovery finds a scheduler method that delegates to a helper`() = runBlocking {
        val service = DelegatingJobService()
        ServiceRegistry.register(service)
        SchedulerInitializer().onReady()
        assertTrue(service.registered, "a delegating scheduler method must be discovered")
    }
}

private abstract class BaseJobService : Service {
    protected val scheduler = requireScheduler()
    var registered = false

    fun inheritedJob(): SchedulerJobHandle {
        registered = true
        return scheduler.jobs { cron("verify.discovery.inherited", "0 0 4 1 1 ?") {} }
    }
}

private class InheritedJobService : BaseJobService()

private class SuspendJobService : Service {
    private val scheduler = requireScheduler()
    var registered = false

    @Suppress("RedundantSuspendModifier")
    suspend fun suspendJob(): SchedulerJobHandle {
        delay(1)
        registered = true
        return scheduler.jobs { cron("verify.discovery.suspend", "0 0 6 1 1 ?") {} }
    }
}

private class DelegatingJobService : Service {
    private val scheduler = requireScheduler()
    var registered = false

    fun delegatingJob(): SchedulerJobHandle = buildJobs()

    private fun buildJobs(): SchedulerJobHandle {
        registered = true
        return scheduler.jobs { cron("verify.discovery.delegating", "0 0 5 1 1 ?") {} }
    }
}
```
