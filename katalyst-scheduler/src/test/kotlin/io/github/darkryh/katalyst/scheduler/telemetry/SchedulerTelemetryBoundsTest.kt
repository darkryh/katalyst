package io.github.darkryh.katalyst.scheduler.telemetry

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.Job
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The memory bounds of the process-global job registry, and what it reports when it hits them.
 *
 * The registry is a debugging side-channel, so it is bounded by construction. What matters is
 * *which* axis is bounded and whether hitting the bound is visible: capping the number of jobs
 * made the 513th job invisible while `registeredCount` kept reporting a confident 512, so a
 * large deployment silently lost the one tool built for diagnosing it. Nothing in this space
 * bounds by entity count - Spring keeps identity for every scheduled task, and where in-memory
 * history exists (Spring's HTTP exchanges, Celery Flower) it is bounded by total entries.
 *
 * So: identity and counters for every job, the expensive axis (retained error text) bounded
 * globally, and the bound reported rather than hidden.
 */
class SchedulerTelemetryBoundsTest {

    /** Comfortably above the job cap, so one pass is guaranteed to reach it. */
    private val beyondCap = 12_000

    private val telemetryLogger = LoggerFactory.getLogger("SchedulerTelemetry") as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        SchedulerTelemetry.reset()
        previousLevel = telemetryLogger.level
        telemetryLogger.level = Level.DEBUG
        appender.list.clear()
        appender.start()
        telemetryLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        telemetryLogger.detachAppender(appender)
        appender.stop()
        telemetryLogger.level = previousLevel
        SchedulerTelemetry.reset()
    }

    private fun warnings(): List<String> =
        appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun register(name: String) =
        SchedulerTelemetry.register(name, "CRON", "0 0 * * * ?", emptyList(), null)

    private fun fail(name: String, detail: String) {
        SchedulerTelemetry.markRunning(name)
        SchedulerTelemetry.recordOutcome(name, "failure", durationMs = 1L, errorDetail = detail)
    }

    private fun succeed(name: String) {
        SchedulerTelemetry.markRunning(name)
        SchedulerTelemetry.recordOutcome(name, "success", durationMs = 1L)
    }

    /** A coroutine that has already finished - the job is over, its slot is reclaimable. */
    private fun finishedJob(): Job = Job().apply { complete() }

    @Test
    fun `registeredCount reports every registration, not only the tracked ones`() {
        repeat(beyondCap) { register("truth-$it") }

        val tracked = SchedulerTelemetry.jobs().size
        assertTrue(tracked < beyondCap, "expected the cap to engage, tracked=$tracked")
        assertEquals(
            beyondCap,
            SchedulerTelemetry.registeredCount,
            "registeredCount must report what the application registered ($beyondCap), not what " +
                "the registry kept ($tracked); otherwise truncation is invisible",
        )
    }

    @Test
    fun `hitting the cap is reported once at WARN`() {
        repeat(beyondCap) { register("warn-$it") }

        val overflowWarnings = warnings().filter { it.contains("telemetry", ignoreCase = true) }
        assertEquals(
            1,
            overflowWarnings.size,
            "overflow must be reported exactly once - silent truncation is the bug, and a warning " +
                "per dropped job is its own problem. Got: $overflowWarnings",
        )
        assertTrue(
            overflowWarnings.single().contains("$beyondCap") ||
                overflowWarnings.single().contains(SchedulerTelemetry.jobs().size.toString()),
            "the warning must carry the numbers, got ${overflowWarnings.single()}",
        )
    }

    @Test
    fun `a finished job is evicted to make room rather than refusing the newcomer`() {
        repeat(beyondCap) { i ->
            val name = "done-$i"
            register(name)
            succeed(name) // give it a last-run timestamp, so eviction has a recency to order by
            SchedulerTelemetry.attachJob(name, finishedJob())
        }
        val atCap = SchedulerTelemetry.jobs().size

        register("newcomer")

        assertTrue(
            SchedulerTelemetry.jobs().any { it.name == "newcomer" },
            "a newly scheduled job must be tracked; evict a job that has finished instead of " +
                "refusing the one that is about to run",
        )
        assertEquals(atCap, SchedulerTelemetry.jobs().size, "eviction must hold the registry at the cap")
    }

    @Test
    fun `a running job is never evicted to make room`() {
        val live = mutableListOf<Job>()
        repeat(beyondCap) { i ->
            val name = "live-$i"
            register(name)
            val job = Job() // still active
            live += job
            SchedulerTelemetry.attachJob(name, job)
        }
        val atCap = SchedulerTelemetry.jobs().size

        register("newcomer")

        assertFalse(
            SchedulerTelemetry.jobs().any { it.name == "newcomer" },
            "with every tracked job still running there is nothing safe to evict, so the newcomer " +
                "is dropped - losing a live job's metrics would be worse",
        )
        assertEquals(atCap, SchedulerTelemetry.jobs().size)
        live.forEach { it.cancel() }
    }

    @Test
    fun `retained error text is bounded across all jobs, not merely per job`() {
        val stackTrace = "java.lang.IllegalStateException: boom\n" +
            "\tat com.example.Thing.method(Thing.kt:42)\n".repeat(200)

        // Enough failing jobs to exhaust any sane global budget: each holds a full run ring.
        repeat(2_000) { j ->
            val name = "burner-$j"
            register(name)
            repeat(10) { fail(name, stackTrace) }
        }

        register("late")
        fail("late", stackTrace)

        val stored = SchedulerTelemetry.jobs().single { it.name == "late" }.recentRuns().first().error
        assertNotNull(stored, "the outcome must still be recorded even when the text budget is spent")
        assertTrue(
            stored.length < 400,
            "once the global error-text budget is spent, further detail must degrade to a short " +
                "summary instead of retaining full stack traces forever; got ${stored.length} chars",
        )
        assertTrue(
            stored.contains("IllegalStateException"),
            "the degraded form must still say what failed, got: $stored",
        )
    }

    @Test
    fun `error-text budget is released when runs roll out of the ring`() {
        val stackTrace = "java.lang.IllegalStateException: boom\n" +
            "\tat com.example.Thing.method(Thing.kt:42)\n".repeat(200)

        val burners = (0 until 2_000).map { "recycle-$it" }
        burners.forEach { name ->
            register(name)
            repeat(10) { fail(name, stackTrace) }
        }

        // Push successes through every burner until the failures have rolled out of every ring.
        repeat(12) { burners.forEach { succeed(it) } }

        register("after-release")
        fail("after-release", stackTrace)

        val stored = SchedulerTelemetry.jobs().single { it.name == "after-release" }.recentRuns().first().error
        assertNotNull(stored)
        assertTrue(
            stored.length > 1_000,
            "budget freed by rolled-out runs must become available again, so a later failure gets " +
                "full detail; got ${stored.length} chars",
        )
    }
}
