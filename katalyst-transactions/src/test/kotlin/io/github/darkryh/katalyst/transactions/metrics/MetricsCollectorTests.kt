package io.github.darkryh.katalyst.transactions.metrics

import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for MetricsCollector implementations.
 *
 * Verifies:
 * - Transaction lifecycle tracking
 * - Adapter execution metrics
 * - Error recording and classification
 * - Memory management and cleanup
 */
class DefaultMetricsCollectorTests {

    private lateinit var collector: DefaultMetricsCollector

    @BeforeEach
    fun setUp() {
        collector = DefaultMetricsCollector()
    }

    @Test
    @Timeout(5)
    fun `start transaction creates metrics`() {
        val metrics = collector.startTransaction("tx-123", "wf-456")

        assertEquals("tx-123", metrics.transactionId)
        assertEquals("wf-456", metrics.workflowId)
        assertEquals(TransactionStatus.RUNNING, metrics.status)
        assertNull(metrics.endTime)
        assertEquals(0, metrics.operationCount)
        assertEquals(0, metrics.eventCount)
        assertEquals(0, metrics.errors.size)
    }

    @Test
    @Timeout(5)
    fun `get metrics returns started transaction`() {
        collector.startTransaction("tx-123")

        val metrics = collector.getMetrics("tx-123")
        assertNotNull(metrics)
        assertEquals("tx-123", metrics.transactionId)
    }

    @Test
    @Timeout(5)
    fun `get metrics returns null for non-existent transaction`() {
        val metrics = collector.getMetrics("tx-nonexistent")
        assertNull(metrics)
    }

    @Test
    @Timeout(5)
    fun `record operation increments count`() {
        collector.startTransaction("tx-123")

        collector.recordOperationExecuted("tx-123")
        collector.recordOperationExecuted("tx-123")
        collector.recordOperationExecuted("tx-123")

        val metrics = collector.getMetrics("tx-123")
        assertEquals(3, metrics!!.operationCount)
    }

    @Test
    @Timeout(5)
    fun `record event increments count`() {
        collector.startTransaction("tx-123")

        collector.recordEventPublished("tx-123")
        collector.recordEventPublished("tx-123")

        val metrics = collector.getMetrics("tx-123")
        assertEquals(2, metrics!!.eventCount)
    }

    @Test
    @Timeout(5)
    fun `record adapter execution tracks metrics`() {
        collector.startTransaction("tx-123")

        val startTime = Instant.now()
        val endTime = startTime.plus(100, ChronoUnit.MILLIS)

        collector.recordAdapterExecution(
            transactionId = "tx-123",
            adapterName = "EventsAdapter",
            phase = TransactionPhase.AFTER_COMMIT,
            startTime = startTime,
            endTime = endTime,
            success = true,
            error = null
        )

        val metrics = collector.getMetrics("tx-123")
        assertEquals(1, metrics!!.adapterExecutions.size)

        val adapter = metrics.adapterExecutions[0]
        assertEquals("EventsAdapter", adapter.adapterName)
        assertEquals(TransactionPhase.AFTER_COMMIT, adapter.phase)
        assertTrue(adapter.success)
        assertNull(adapter.error)
    }

    @Test
    @Timeout(5)
    fun `record error tracks failure details`() {
        collector.startTransaction("tx-123")

        val error = SQLException("Connection timeout")
        collector.recordError(
            transactionId = "tx-123",
            phase = TransactionPhase.BEFORE_COMMIT,
            error = error,
            isRetryable = true
        )

        val metrics = collector.getMetrics("tx-123")
        assertEquals(1, metrics!!.errors.size)

        val recordedError = metrics.errors[0]
        assertEquals("Connection timeout", recordedError.message)
        assertEquals(TransactionPhase.BEFORE_COMMIT, recordedError.phase)
        assertTrue(recordedError.isRetryable)
    }

    @Test
    @Timeout(5)
    fun `complete transaction sets status and duration`() {
        val metrics = collector.startTransaction("tx-123")
        val startTime = metrics.startTime

        // Simulate some work
        Thread.sleep(50)

        collector.completeTransaction("tx-123", TransactionStatus.COMMITTED, retryCount = 0)

        val completed = collector.getMetrics("tx-123")!!
        assertEquals(TransactionStatus.COMMITTED, completed.status)
        assertNotNull(completed.endTime)
        assertNotNull(completed.duration)
        assertTrue(completed.duration!!.inWholeMilliseconds >= 40)
    }

    @Test
    @Timeout(5)
    fun `clear metrics removes transaction`() {
        collector.startTransaction("tx-123")

        val removed = collector.clearMetrics("tx-123")
        assertTrue(removed)

        val metrics = collector.getMetrics("tx-123")
        assertNull(metrics)
    }

    @Test
    @Timeout(5)
    fun `clear metrics returns false for non-existent transaction`() {
        val removed = collector.clearMetrics("tx-nonexistent")
        assertEquals(false, removed)
    }

    @Test
    @Timeout(5)
    fun `clear all metrics removes all transactions`() {
        collector.startTransaction("tx-1")
        collector.startTransaction("tx-2")
        collector.startTransaction("tx-3")

        collector.clearAllMetrics()

        assertNull(collector.getMetrics("tx-1"))
        assertNull(collector.getMetrics("tx-2"))
        assertNull(collector.getMetrics("tx-3"))
    }

    @Test
    @Timeout(5)
    fun `get all metrics returns all tracked transactions`() {
        collector.startTransaction("tx-1")
        collector.startTransaction("tx-2")
        collector.startTransaction("tx-3")

        val allMetrics = collector.getAllMetrics()
        assertEquals(3, allMetrics.size)
        assertTrue(allMetrics.containsKey("tx-1"))
        assertTrue(allMetrics.containsKey("tx-2"))
        assertTrue(allMetrics.containsKey("tx-3"))
    }

    @Test
    @Timeout(5)
    fun `transaction count tracks number of transactions`() {
        assertEquals(0, collector.getTransactionCount())

        collector.startTransaction("tx-1")
        assertEquals(1, collector.getTransactionCount())

        collector.startTransaction("tx-2")
        assertEquals(2, collector.getTransactionCount())

        collector.clearMetrics("tx-1")
        assertEquals(1, collector.getTransactionCount())
    }

    @Test
    @Timeout(10)
    fun `clear metrics before cleans up old metrics`() {
        val now = Instant.now()
        val oneHourAgo = now.minus(java.time.Duration.ofHours(1))

        // Create old transaction
        val oldMetrics = collector.startTransaction("tx-old")
        oldMetrics.endTime = oneHourAgo.plus(java.time.Duration.ofMinutes(1))
        oldMetrics.status = TransactionStatus.COMMITTED

        // Create recent transaction
        val recentMetrics = collector.startTransaction("tx-recent")
        recentMetrics.endTime = now.minus(java.time.Duration.ofMinutes(1))
        recentMetrics.status = TransactionStatus.COMMITTED

        // Clear metrics older than 30 minutes ago
        val thirtyMinutesAgo = now.minus(java.time.Duration.ofMinutes(30))
        val removed = collector.clearMetricsBefore(thirtyMinutesAgo)

        assertEquals(1, removed)
        assertNull(collector.getMetrics("tx-old"))
        assertNotNull(collector.getMetrics("tx-recent"))
    }

    @Test
    @Timeout(5)
    fun `multiple adapter executions tracked separately`() {
        collector.startTransaction("tx-123")

        val time = Instant.now()

        // Record multiple adapter executions
        collector.recordAdapterExecution("tx-123", "Adapter1", TransactionPhase.AFTER_BEGIN, time, time.plusSeconds(1), true)
        collector.recordAdapterExecution("tx-123", "Adapter2", TransactionPhase.BEFORE_COMMIT, time, time.plusSeconds(2), true)
        collector.recordAdapterExecution("tx-123", "Adapter3", TransactionPhase.AFTER_COMMIT, time, time.plusSeconds(3), false)

        val metrics = collector.getMetrics("tx-123")!!
        assertEquals(3, metrics.adapterExecutions.size)
        assertEquals("Adapter1", metrics.adapterExecutions[0].adapterName)
        assertEquals("Adapter2", metrics.adapterExecutions[1].adapterName)
        assertEquals("Adapter3", metrics.adapterExecutions[2].adapterName)
    }

    @Test
    @Timeout(5)
    fun `operation on non-existent transaction does nothing`() {
        collector.recordOperationExecuted("tx-nonexistent")
        collector.recordEventPublished("tx-nonexistent")
        collector.recordError("tx-nonexistent", TransactionPhase.BEFORE_COMMIT, RuntimeException("test"), false)

        // Should not throw exception
        val metrics = collector.getMetrics("tx-nonexistent")
        assertNull(metrics)
    }
}

/**
 * Unit tests for NoOpMetricsCollector.
 *
 * Verifies that no-op implementation performs no operations.
 */
class NoOpMetricsCollectorTests {

    private lateinit var collector: NoOpMetricsCollector

    @BeforeEach
    fun setUp() {
        collector = NoOpMetricsCollector()
    }

    @Test
    @Timeout(5)
    fun `start transaction returns empty metrics`() {
        val metrics = collector.startTransaction("tx-123")
        assertEquals("tx-123", metrics.transactionId)
        assertEquals(TransactionStatus.RUNNING, metrics.status)
    }

    @Test
    @Timeout(5)
    fun `get metrics always returns null`() {
        collector.startTransaction("tx-123")
        collector.recordOperationExecuted("tx-123")

        val metrics = collector.getMetrics("tx-123")
        assertNull(metrics)
    }

    @Test
    @Timeout(5)
    fun `get all metrics returns empty map`() {
        collector.startTransaction("tx-123")

        val allMetrics = collector.getAllMetrics()
        assertTrue(allMetrics.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `clear metrics returns false`() {
        val result = collector.clearMetrics("tx-123")
        assertEquals(false, result)
    }

    @Test
    @Timeout(5)
    fun `clear all metrics is no-op`() {
        collector.clearAllMetrics()
        // Should not throw exception
    }

    @Test
    @Timeout(5)
    fun `all operations are no-op`() {
        // All these should execute without throwing
        collector.startTransaction("tx-123")
        collector.recordOperationExecuted("tx-123")
        collector.recordEventPublished("tx-123")
        collector.recordAdapterExecution("tx-123", "adapter", TransactionPhase.BEFORE_COMMIT, Instant.now(), Instant.now(), true)
        collector.recordError("tx-123", TransactionPhase.BEFORE_COMMIT, RuntimeException("test"), false)
        collector.completeTransaction("tx-123", TransactionStatus.COMMITTED)
    }
}

/**
 * Tests for AdapterMetrics and TransactionError.
 */
class MetricsDataClassTests {

    @Test
    @Timeout(5)
    fun `adapter metrics calculates duration`() {
        val startTime = Instant.now()
        val endTime = startTime.plus(java.time.Duration.ofMillis(100))

        val adapter = AdapterMetrics(
            adapterName = "TestAdapter",
            phase = TransactionPhase.AFTER_COMMIT,
            startTime = startTime,
            endTime = endTime,
            success = true
        )

        val duration = adapter.calculateDuration()
        assertNotNull(duration)
        assertTrue(duration.inWholeMilliseconds >= 90)
        assertTrue(duration.inWholeMilliseconds <= 110)
    }

    @Test
    @Timeout(5)
    fun `adapter metrics returns null duration if not ended`() {
        val adapter = AdapterMetrics(
            adapterName = "TestAdapter",
            phase = TransactionPhase.BEFORE_COMMIT,
            startTime = Instant.now(),
            success = false
        )

        val duration = adapter.calculateDuration()
        assertNull(duration)
    }

    @Test
    @Timeout(5)
    fun `transaction error stores details`() {
        val error = TransactionError(
            timestamp = Instant.now(),
            phase = TransactionPhase.BEFORE_COMMIT,
            message = "Database error",
            stackTrace = "stack trace here",
            isRetryable = true,
            exceptionClassName = "SQLException"
        )

        assertEquals("Database error", error.message)
        assertEquals(TransactionPhase.BEFORE_COMMIT, error.phase)
        assertTrue(error.isRetryable)
        assertEquals("SQLException", error.exceptionClassName)
    }
}
