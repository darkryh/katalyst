package io.github.darkryh.katalyst.scheduler.service

import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Phase 2/5 — scheduler resilience: a burst of thread-blocking tasks must only *delay* other work,
 * never permanently wedge the scheduler; and stopping the scheduler must drain (cancel) running
 * jobs. Assertions are on counts, not timings (only generous safety timeouts).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerStarvationAndDrainTest {

    private var scheduler: SchedulerService? = null

    @AfterTest
    fun tearDown() {
        scheduler?.close()
        scheduler = null
    }

    @Test
    fun `thread-blocking tasks delay but never permanently wedge other jobs`() = runBlocking {
        // Only two worker threads, so blocking tasks genuinely contend for the dispatcher.
        val service = SchedulerService(Dispatchers.Default.limitedParallelism(2))
            .also { scheduler = it }

        // Saturate both threads with blocking work...
        repeat(2) { i ->
            service.schedule(
                config = ScheduleConfig(taskName = "blocker-$i"),
                task = { Thread.sleep(300) }, // blocks the thread, not a cooperative suspend
                fixedRate = Duration.ZERO
            )
        }
        // ...then queue many quick jobs behind them.
        val quickRan = AtomicInteger(0)
        val quickJobs = 25
        repeat(quickJobs) { i ->
            service.schedule(
                config = ScheduleConfig(taskName = "quick-$i"),
                task = { quickRan.incrementAndGet() },
                fixedRate = Duration.ZERO
            )
        }

        // Despite the starvation, every quick job must eventually run (delayed, not lost).
        val drained = withTimeoutOrNull(10_000) {
            while (quickRan.get() < quickJobs) delay(10)
            true
        }
        assertTrue(drained == true, "scheduler wedged: only ${quickRan.get()}/$quickJobs quick jobs ran")
        assertEquals(quickJobs, quickRan.get())
    }

    @Test
    fun `stopping the scheduler drains running repeating jobs`() = runBlocking {
        val service = SchedulerService().also { scheduler = it }
        val runs = AtomicInteger(0)

        service.schedule(
            config = ScheduleConfig(taskName = "repeating"),
            task = { runs.incrementAndGet() },
            fixedRate = kotlin.time.Duration.parse("5ms")
        )

        val started = withTimeoutOrNull(5_000) {
            while (runs.get() < 3) delay(5)
            true
        }
        assertTrue(started == true, "job never started")

        service.stop() // drain: cancel the supervisor job and all children
        val countAtStop = runs.get()
        delay(80) // several fixed-rate periods
        assertTrue(runs.get() == countAtStop, "job kept running after stop: $countAtStop -> ${runs.get()}")
    }
}
