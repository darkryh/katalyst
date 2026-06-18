package io.github.darkryh.katalyst.scheduler.service

import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Concurrency-correctness tests for [SchedulerService].
 *
 * These assert scheduling *invariants* (no self-overlap, every concurrently-scheduled job runs,
 * cancellation actually stops execution) under a real multi-threaded dispatcher. The assertions are
 * on counts/booleans, not wall-clock deadlines, so they are deterministic; the only time bounds are
 * generous safety timeouts that fail loudly if the scheduler wedges.
 */
class SchedulerServiceConcurrencyTest {

    private var scheduler: SchedulerService? = null

    @AfterTest
    fun tearDown() {
        scheduler?.close()
        scheduler = null
    }

    @Test
    fun `a slow fixed-rate task never overlaps itself`() = runBlocking(Dispatchers.Default) {
        val service = SchedulerService().also { scheduler = it }
        val inFlight = AtomicInteger(0)
        val maxObservedConcurrency = AtomicInteger(0)
        val completed = AtomicInteger(0)

        val handle = service.schedule(
            config = ScheduleConfig(taskName = "slow-task"),
            task = {
                val now = inFlight.incrementAndGet()
                maxObservedConcurrency.updateAndGet { prev -> maxOf(prev, now) }
                delay(25) // work that outlasts the fixed rate below
                inFlight.decrementAndGet()
                completed.incrementAndGet()
            },
            // Tiny rate: a timer-based scheduler would pile up overlapping runs; this one must not.
            fixedRate = 1.milliseconds
        )

        awaitUntil("task to execute several times") { completed.get() >= 5 }
        handle.cancelAndJoin()

        assertEquals(1, maxObservedConcurrency.get(), "scheduled task overlapped itself")
    }

    @Test
    fun `concurrently scheduled one-time jobs all run exactly once`() = runBlocking(Dispatchers.Default) {
        val service = SchedulerService().also { scheduler = it }
        val jobCount = 200
        val executions = AtomicInteger(0)

        // Register many jobs from many threads at once: no schedule may be lost to a race.
        coroutineScope {
            (0 until jobCount).map { i ->
                async(Dispatchers.Default) {
                    service.schedule(
                        config = ScheduleConfig(taskName = "one-shot-$i"),
                        task = { executions.incrementAndGet() },
                        fixedRate = kotlin.time.Duration.ZERO // one-time
                    )
                }
            }.awaitAll()
        }

        awaitUntil("all one-time jobs to run") { executions.get() == jobCount }
        assertEquals(jobCount, executions.get())
    }

    @Test
    fun `cancelling a repeating job stops further executions`() = runBlocking(Dispatchers.Default) {
        val service = SchedulerService().also { scheduler = it }
        val runs = AtomicInteger(0)

        val handle = service.schedule(
            config = ScheduleConfig(taskName = "repeating"),
            task = { runs.incrementAndGet() },
            fixedRate = 5.milliseconds
        )

        awaitUntil("repeating job to run a few times") { runs.get() >= 3 }
        handle.cancelAndJoin()

        val countAtCancel = runs.get()
        delay(60) // well over several fixed-rate periods
        assertTrue(
            runs.get() == countAtCancel,
            "job kept running after cancel: $countAtCancel -> ${runs.get()}"
        )
    }

    private suspend fun awaitUntil(what: String, condition: () -> Boolean) {
        val satisfied = withTimeoutOrNull(5_000) {
            while (!condition()) delay(5)
            true
        }
        assertTrue(satisfied == true, "timed out waiting for $what")
    }
}
