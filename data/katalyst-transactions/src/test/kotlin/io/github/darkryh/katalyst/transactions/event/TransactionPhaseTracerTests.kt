package io.github.darkryh.katalyst.transactions.event

import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for TransactionPhaseTracer
 *
 * Validates phase event recording and metrics collection.
 */
class TransactionPhaseTracerTests {

    private lateinit var tracer: TransactionPhaseTracer

    @BeforeEach
    fun setUp() {
        tracer = TransactionPhaseTracer()
    }

    @Test
    @Timeout(10)
    fun `records phase start and completion`() {
        val transactionId = "tx-123"

        tracer.recordPhaseStart(TransactionPhase.BEFORE_COMMIT, transactionId)
        tracer.recordPhaseComplete(TransactionPhase.BEFORE_COMMIT, eventCount = 5, durationMs = 10, transactionId)

        val events = tracer.getEvents()
        assertEquals(2, events.size)
        assertEquals(TransactionPhaseEvent.EventType.PHASE_COMPLETED, events[1].eventType)
    }

    @Test
    @Timeout(10)
    fun `records events validated`() {
        val transactionId = "tx-456"

        tracer.recordEventsValidated(TransactionPhase.BEFORE_COMMIT_VALIDATION, eventCount = 3, transactionId)

        val events = tracer.getEvents()
        assertEquals(1, events.size)
        assertEquals(TransactionPhaseEvent.EventType.EVENTS_VALIDATED, events[0].eventType)
        assertEquals(3, events[0].eventCount)
    }

    @Test
    @Timeout(10)
    fun `records events published`() {
        val transactionId = "tx-789"

        tracer.recordEventsPublished(
            TransactionPhase.BEFORE_COMMIT,
            eventCount = 5,
            handlerCount = 2,
            transactionId
        )

        val events = tracer.getEvents()
        assertEquals(1, events.size)
        assertEquals(TransactionPhaseEvent.EventType.EVENTS_PUBLISHED, events[0].eventType)
        assertEquals(5, events[0].eventCount)
        assertEquals(2, events[0].handlerCount)
    }

    @Test
    @Timeout(10)
    fun `records events failed`() {
        val transactionId = "tx-fail"

        tracer.recordEventsFailed(
            TransactionPhase.BEFORE_COMMIT,
            eventCount = 5,
            failedCount = 2,
            error = "Handler timeout",
            transactionId
        )

        val events = tracer.getEvents()
        assertEquals(1, events.size)
        assertEquals(2, events[0].failedHandlerCount)
        assertEquals("Handler timeout", events[0].error)
    }

    @Test
    @Timeout(10)
    fun `records rollback`() {
        val transactionId = "tx-rollback"

        tracer.recordRollback(
            TransactionPhase.ON_ROLLBACK,
            eventCount = 3,
            reason = "Handler exception",
            transactionId
        )

        val events = tracer.getEvents()
        assertEquals(1, events.size)
        assertEquals(TransactionPhaseEvent.EventType.ROLLBACK_TRIGGERED, events[0].eventType)
        assertEquals(3, events[0].eventCount)
    }

    @Test
    @Timeout(10)
    fun `filters events by phase`() {
        val transactionId = "tx-filter"

        tracer.recordEventsValidated(TransactionPhase.BEFORE_COMMIT_VALIDATION, 2, transactionId)
        tracer.recordEventsPublished(TransactionPhase.BEFORE_COMMIT, 5, 2, transactionId)
        tracer.recordPhaseComplete(TransactionPhase.AFTER_COMMIT, 0, 5, transactionId)

        val beforeCommitEvents = tracer.getPhaseEvents(TransactionPhase.BEFORE_COMMIT)
        assertEquals(1, beforeCommitEvents.size)
        assertEquals(TransactionPhaseEvent.EventType.EVENTS_PUBLISHED, beforeCommitEvents[0].eventType)
    }

    @Test
    @Timeout(10)
    fun `generates summary`() {
        val transactionId = "tx-summary"

        tracer.recordEventsValidated(TransactionPhase.BEFORE_COMMIT_VALIDATION, 3, transactionId)
        tracer.recordEventsPublished(TransactionPhase.BEFORE_COMMIT, 5, 2, transactionId)
        tracer.recordPhaseComplete(TransactionPhase.BEFORE_COMMIT, 5, 50, transactionId)

        val summary = tracer.getSummary()
        assertTrue(summary.contains("Transaction Summary"))
        assertTrue(summary.contains("phases="))
        assertTrue(summary.contains("totalEvents="))
    }

    @Test
    @Timeout(10)
    fun `clears recorded events`() {
        tracer.recordEventsPublished(TransactionPhase.BEFORE_COMMIT, 5, 2)

        assertEquals(1, tracer.getEvents().size)

        tracer.clear()

        assertEquals(0, tracer.getEvents().size)
    }

    @Test
    @Timeout(10)
    fun `calculates metrics`() {
        val transactionId = "tx-metrics"

        tracer.recordEventsValidated(TransactionPhase.BEFORE_COMMIT_VALIDATION, 3, transactionId)
        tracer.recordEventsPublished(TransactionPhase.BEFORE_COMMIT, 5, 2, transactionId)
        tracer.recordPhaseComplete(TransactionPhase.BEFORE_COMMIT, 5, 50, transactionId)
        tracer.recordPhaseComplete(TransactionPhase.AFTER_COMMIT, 0, 10, transactionId)

        val metrics = tracer.getMetrics()
        assertEquals(5, metrics.totalEvents)
        assertEquals(0, metrics.failedEvents)
        assertEquals(60, metrics.totalDurationMs)
    }

    @Test
    @Timeout(10)
    fun `records errors`() {
        val transactionId = "tx-error"
        val exception = RuntimeException("Test error")

        tracer.recordError(TransactionPhase.BEFORE_COMMIT, exception, transactionId)

        val events = tracer.getEvents()
        assertEquals(1, events.size)
        assertEquals(TransactionPhaseEvent.EventType.ERROR_OCCURRED, events[0].eventType)
        assertEquals("Test error", events[0].error)
    }

    @Test
    @Timeout(10)
    fun `trace phase scope function`() {
        val transactionId = "tx-scope"
        var blockExecuted = false

        tracer.tracePhase(TransactionPhase.BEFORE_COMMIT, eventCount = 5, transactionId) {
            blockExecuted = true
            assertTrue(true)
        }

        assertTrue(blockExecuted)
        val events = tracer.getEvents()
        assertTrue(events.any { it.eventType == TransactionPhaseEvent.EventType.PHASE_STARTED })
        assertTrue(events.any { it.eventType == TransactionPhaseEvent.EventType.PHASE_COMPLETED })
    }

    @Test
    @Timeout(10)
    fun `trace phase handles exceptions`() {
        val transactionId = "tx-scope-error"

        try {
            tracer.tracePhase(TransactionPhase.BEFORE_COMMIT, eventCount = 5, transactionId) {
                throw RuntimeException("Scope error")
            }
        } catch (e: RuntimeException) {
            assertEquals("Scope error", e.message)
        }

        val events = tracer.getEvents()
        assertTrue(events.any { it.eventType == TransactionPhaseEvent.EventType.ERROR_OCCURRED })
    }
}
