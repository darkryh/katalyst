package io.github.darkryh.katalyst.scheduler.service

import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.telemetry.SchedulerTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The trigger contract of [SchedulerService]: *when* each trigger fires, one named case per row of
 * the documented semantics table.
 *
 * | Trigger | First run | Cadence |
 * |---|---|---|
 * | `fixedRate` | after `initialDelay` | start-to-start; ticks accumulate on overrun; never concurrent |
 * | `fixedDelay` | after `initialDelay` | end-to-start |
 * | `oneTime` | after `initialDelay` | never again |
 * | `cron` | the next matching instant — never at registration | per the expression, in `config.timeZone` |
 *
 * Everything here runs on virtual time. `delay` is driven by [TestCoroutineScheduler] and the
 * scheduler's own [Clock] is a [VirtualClock] reading that same virtual scheduler, so the two time
 * bases agree exactly. That is what lets every assertion be an equality on a precise instant rather
 * than a wall-clock tolerance window: there are no real sleeps in this file and nothing here is
 * timing sensitive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriggerSemanticsTest {

    private var scheduler: SchedulerService? = null

    @AfterTest
    fun tearDown() {
        scheduler?.close()
        scheduler = null
    }

    /**
     * A [Clock] whose instants advance in lockstep with virtual time.
     *
     * [withZone] must keep the same virtual instant source and only swap the zone: the cron path
     * calls `clock.withZone(config.timeZone)` to read the wall clock of the job's configured zone.
     */
    private class VirtualClock(
        private val testScheduler: TestCoroutineScheduler,
        private val base: Long,
        private val zone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = VirtualClock(testScheduler, base, zone)
        override fun instant(): Instant = Instant.ofEpochMilli(base + testScheduler.currentTime)
    }

    /**
     * Runs [body] against a scheduler living entirely on this test's virtual time, anchored at
     * [base] and reading the clock in [clockZone] (deliberately *not* the job's own zone).
     *
     * The scheduler is stopped inside the test body, not in `@AfterTest`: `runTest` drains the
     * shared virtual scheduler once the body returns, and a still-repeating job would make that
     * drain run forever.
     */
    private fun schedulerTest(
        base: Long = 0L,
        clockZone: ZoneId = ZoneOffset.UTC,
        body: suspend TestScope.(SchedulerService) -> Unit,
    ): TestResult = runTest {
        val service = SchedulerService(
            StandardTestDispatcher(testScheduler),
            VirtualClock(testScheduler, base, clockZone),
        ).also { scheduler = it }
        try {
            body(service)
        } finally {
            service.stop()
        }
    }

    // ---------------------------------------------------------------- fixedRate

    @Test
    fun `fixedRate measures the period from the start of the previous run`() = schedulerTest { service ->
        val starts = mutableListOf<Long>()

        // The task eats 250 of the 300ms period. End-to-start would space the starts 550ms apart.
        service.schedule(
            config = ScheduleConfig(taskName = "trigger-semantics.rate-start-to-start"),
            task = {
                starts += testScheduler.currentTime
                delay(250)
            },
            fixedRate = 300.milliseconds,
        )

        advanceTimeBy(701)
        runCurrent()

        assertEquals(listOf(0L, 300L, 600L), starts, "fixedRate must measure the period from run START")
    }

    @Test
    fun `fixedRate overrun accumulates ticks and catches up back-to-back without overlapping`() =
        schedulerTest { service ->
            val starts = mutableListOf<Long>()
            var inFlight = 0
            var maxInFlight = 0
            var overrunPending = true

            // The first run overruns 3.5 periods: ticks 100/200/300 come due while it is running.
            service.schedule(
                config = ScheduleConfig(taskName = "trigger-semantics.rate-overrun"),
                task = {
                    inFlight++
                    maxInFlight = maxOf(maxInFlight, inFlight)
                    starts += testScheduler.currentTime
                    if (overrunPending) {
                        overrunPending = false
                        delay(350)
                    }
                    inFlight--
                },
                fixedRate = 100.milliseconds,
            )

            advanceTimeBy(501)
            runCurrent()

            // The three ticks missed during the overrun fire back-to-back the moment it finishes,
            // then the schedule is back on its original 100ms grid at 400 and 500.
            assertEquals(listOf(0L, 350L, 350L, 350L, 400L, 500L), starts)
            assertEquals(1, maxInFlight, "fixedRate runs must never overlap while catching up")
        }

    @Test
    fun `fixedRate first run happens after the initial delay`() = schedulerTest { service ->
        val starts = mutableListOf<Long>()

        service.schedule(
            config = ScheduleConfig(
                taskName = "trigger-semantics.rate-first-run",
                initialDelay = 250.milliseconds,
            ),
            task = { starts += testScheduler.currentTime },
            fixedRate = 100.milliseconds,
        )

        advanceTimeBy(250)
        assertEquals(emptyList<Long>(), starts, "fixedRate fired before its initial delay elapsed")

        runCurrent()
        assertEquals(listOf(250L), starts)
    }

    // ---------------------------------------------------------------- fixedDelay

    @Test
    fun `fixedDelay measures the delay from the end of the previous run`() = schedulerTest { service ->
        val starts = mutableListOf<Long>()

        service.scheduleFixedDelay(
            config = ScheduleConfig(taskName = "trigger-semantics.delay-end-to-start"),
            task = {
                starts += testScheduler.currentTime
                delay(250)
            },
            fixedDelay = 100.milliseconds,
        )

        advanceTimeBy(701)
        runCurrent()

        // 250ms of work + 100ms of delay = 350ms between starts, unlike fixedRate's flat 300ms.
        assertEquals(listOf(0L, 350L, 700L), starts)
    }

    @Test
    fun `fixedDelay first run happens after the initial delay`() = schedulerTest { service ->
        val starts = mutableListOf<Long>()

        service.scheduleFixedDelay(
            config = ScheduleConfig(
                taskName = "trigger-semantics.delay-first-run",
                initialDelay = 250.milliseconds,
            ),
            task = { starts += testScheduler.currentTime },
            fixedDelay = 100.milliseconds,
        )

        advanceTimeBy(250)
        assertEquals(emptyList<Long>(), starts, "fixedDelay fired before its initial delay elapsed")

        runCurrent()
        assertEquals(listOf(250L), starts)
    }

    // ---------------------------------------------------------------- oneTime

    @Test
    fun `oneTime runs once after the initial delay and never again`() = schedulerTest { service ->
        val starts = mutableListOf<Long>()

        val handle = service.schedule(
            config = ScheduleConfig(
                taskName = "trigger-semantics.one-time",
                initialDelay = 250.milliseconds,
            ),
            task = { starts += testScheduler.currentTime },
            fixedRate = Duration.ZERO,
        )

        advanceTimeBy(250)
        assertEquals(emptyList<Long>(), starts, "oneTime fired before its initial delay elapsed")

        runCurrent()
        assertEquals(listOf(250L), starts)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(250L), starts, "oneTime must never run a second time")
        assertEquals(false, handle.isActive)
    }

    // ---------------------------------------------------------------- cron

    @Test
    fun `cron does not fire at registration and fires at the first matching instant`() =
        // Virtual time starts at 2026-01-01T00:00:00Z; the expression matches 03:00 UTC.
        schedulerTest(base = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()) { service ->
            val fires = mutableListOf<Long>()

            service.scheduleCron(
                config = ScheduleConfig(
                    taskName = "trigger-semantics.cron-first-fire",
                    timeZone = ZoneOffset.UTC,
                ),
                task = { fires += testScheduler.currentTime },
                cronExpression = CronExpression("0 0 3 * * ?"),
            )

            runCurrent()
            assertEquals(emptyList<Long>(), fires, "cron must not fire at registration")

            val threeHours = 3 * 60 * 60 * 1000L
            advanceTimeBy(threeHours)
            assertEquals(emptyList<Long>(), fires, "cron fired before its first matching instant")

            runCurrent()
            assertEquals(listOf(threeHours), fires)
        }

    @Test
    fun `cron evaluates its expression in the configured time zone`() =
        // 2026-08-03T10:00:00Z is 15:45 in Kathmandu; the next local top of the hour (16:00 local)
        // is 10:15Z — 15 minutes away, not the 60 minutes a UTC evaluation would produce. The clock
        // itself reads UTC, so only `ScheduleConfig.timeZone` can produce the expected instant.
        schedulerTest(base = Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()) { service ->
            // Asia/Kathmandu is +05:45, so no whole-hour zone (including any plausible JVM default)
            // can land on the same instant by coincidence.
            val kathmandu = ZoneId.of("Asia/Kathmandu")
            val fires = mutableListOf<Long>()

            service.scheduleCron(
                config = ScheduleConfig(
                    taskName = "trigger-semantics.cron-timezone",
                    timeZone = kathmandu,
                ),
                task = { fires += testScheduler.currentTime },
                cronExpression = CronExpression("0 0 * * * ?"),
            )

            runCurrent()
            assertEquals(emptyList<Long>(), fires, "cron must not fire at registration")

            val fifteenMinutes = 15 * 60 * 1000L
            advanceTimeBy(fifteenMinutes)
            assertEquals(emptyList<Long>(), fires, "cron ignored ScheduleConfig.timeZone")

            runCurrent()
            assertEquals(listOf(fifteenMinutes), fires)
        }

    @Test
    fun `cron telemetry next fire equals the instant the job actually fires`() =
        schedulerTest(base = Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()) { service ->
            val base = Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()
            val name = "trigger-semantics.cron-telemetry"
            val fires = mutableListOf<Long>()

            service.scheduleCron(
                config = ScheduleConfig(taskName = name, timeZone = ZoneId.of("Asia/Kathmandu")),
                task = { fires += testScheduler.currentTime },
                cronExpression = CronExpression("0 0 * * * ?"),
            )

            runCurrent()
            assertEquals(emptyList<Long>(), fires, "cron must not fire at registration")

            val stat = SchedulerTelemetry.jobs().firstOrNull { it.name == name }
            assertNotNull(stat, "job '$name' is missing from SchedulerTelemetry (registry cap reached?)")
            val announced = stat.nextFireEpochMs
            assertNotNull(announced, "cron job announced no next fire time")

            advanceTimeBy(15 * 60 * 1000L)
            runCurrent()

            assertEquals(1, fires.size, "expected exactly one fire")
            assertEquals(
                base + fires.single(),
                announced,
                "telemetry's announced next fire must be the instant the job actually fires",
            )
        }
}
