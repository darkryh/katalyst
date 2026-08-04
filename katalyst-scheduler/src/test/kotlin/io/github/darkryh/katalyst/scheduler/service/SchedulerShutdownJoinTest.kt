package io.github.darkryh.katalyst.scheduler.service

import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * Closing the scheduler must *drain* it, not merely signal it.
 *
 * [SchedulerService.close] only cancelled the supervisor job, and cancellation is asynchronous: it
 * returned while a run was still executing. The container closes beans in reverse registration
 * order, so `DatabaseFactory` — registered first — is closed *last*; a job caught mid-transaction
 * therefore raced the pool close and finished against `HikariDataSource has been closed`. At best
 * shutdown noise, at worst a lost final write.
 *
 * The wait has to be bounded: a job wedged on a blocking call must not hold the process open.
 */
class SchedulerShutdownJoinTest {

    private val release = CountDownLatch(1)

    @AfterTest
    fun tearDown() {
        // Never leave a parked pool thread behind, whichever way the assertions went.
        release.countDown()
    }

    @Test
    fun `close does not return while a run is still finishing`() {
        val service = SchedulerService(Dispatchers.Default)
        val runStarted = CountDownLatch(1)
        val finalWriteStarted = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())

        service.schedule(
            config = ScheduleConfig(taskName = "shutdown-join.in-flight"),
            task = {
                runStarted.countDown()
                try {
                    awaitCancellation()
                } finally {
                    // The final write of a job caught by shutdown: an in-flight JDBC statement does
                    // not observe cancellation, it runs to completion on the connection it holds.
                    withContext(NonCancellable) {
                        finalWriteStarted.countDown()
                        release.await(10, TimeUnit.SECONDS)
                        events += "run-finished"
                    }
                }
            },
            fixedRate = Duration.ZERO,
        )
        assertTrue(runStarted.await(10, TimeUnit.SECONDS), "the run never started")

        // Released only once the run is inside its uncancellable section, which cannot happen
        // before close() has cancelled — so the release lands strictly inside close()'s own window.
        val releaser = thread(name = "shutdown-join-releaser") {
            finalWriteStarted.await(10, TimeUnit.SECONDS)
            release.countDown()
        }

        service.close()
        events += "close-returned"
        releaser.join()

        assertEquals(
            listOf("run-finished", "close-returned"),
            events.toList(),
            "close() must not return until the in-flight run has finished; returning first is what " +
                "lets a job outlive the DatabaseFactory that is closed after it",
        )
    }

    @Test
    fun `close gives up after the grace so a wedged run cannot hang shutdown`() {
        val grace = 300.milliseconds
        val service = SchedulerService(Dispatchers.Default, Clock.systemDefaultZone(), grace)
        val runStarted = CountDownLatch(1)

        service.schedule(
            config = ScheduleConfig(taskName = "shutdown-join.wedged"),
            task = {
                runStarted.countDown()
                try {
                    awaitCancellation()
                } finally {
                    // Wedged: this run only ends when the test releases it, never on cancellation.
                    withContext(NonCancellable) { release.await(30, TimeUnit.SECONDS) }
                }
            },
            fixedRate = Duration.ZERO,
        )
        assertTrue(runStarted.await(10, TimeUnit.SECONDS), "the run never started")

        val elapsed = measureTime { service.close() }

        assertTrue(
            elapsed >= grace,
            "close() must wait for the in-flight run, it waited only $elapsed",
        )
        assertTrue(
            elapsed < grace * 10,
            "close() must give up after the grace; a wedged run may not hold shutdown open, waited $elapsed",
        )
    }
}
