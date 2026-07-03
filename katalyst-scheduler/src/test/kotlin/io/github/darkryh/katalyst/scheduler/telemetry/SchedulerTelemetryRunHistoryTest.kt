package io.github.darkryh.katalyst.scheduler.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The per-run history ring behind the inspector's job drill-down: every outcome recorded, newest
 * first, bounded by construction, with failure detail truncated at the recording site so a caller
 * can never grow memory by passing a huge stack trace.
 */
class SchedulerTelemetryRunHistoryTest {

    @Test
    fun `records runs newest-first with bounded size and truncated error detail`() {
        val name = "run-history-test-${System.nanoTime()}"
        SchedulerTelemetry.register(name, "CRON", "0 0/1 * * * ?", emptyList(), null)

        repeat(40) { i ->
            SchedulerTelemetry.markRunning(name)
            val failing = i % 2 == 1
            SchedulerTelemetry.recordOutcome(
                name,
                if (failing) "failure" else "success",
                durationMs = i.toLong(),
                errorDetail = if (failing) "boom $i\n" + "x".repeat(5_000) else null,
            )
        }

        val stat = SchedulerTelemetry.jobs().first { it.name == name }
        val runs = stat.recentRuns()

        // Bounded: 40 recorded, only the newest 25 retained, newest first.
        assertEquals(25, runs.size)
        assertEquals(39L, runs.first().durationMs)
        assertEquals(15L, runs.last().durationMs)
        assertTrue(runs.zipWithNext().all { (a, b) -> a.startedAtEpochMs >= b.startedAtEpochMs })

        // Failure detail survives (message first) but is truncated to the memory cap.
        val failure = runs.first { it.outcome == "failure" }
        assertTrue(failure.error!!.startsWith("boom"))
        assertTrue(failure.error!!.length <= 1_500)

        // Clean runs carry no error payload at all.
        assertNull(runs.first { it.outcome == "success" }.error)
    }
}
