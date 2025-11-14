package com.ead.katalyst.transactions.adapter

import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for TransactionAdapter interface and related execution result classes.
 *
 * Tests cover:
 * - TransactionAdapter interface default methods
 * - TransactionAdapter custom implementations
 * - AdapterExecutionResult creation and access
 * - PhaseExecutionResults aggregation and querying
 * - Critical vs non-critical adapter failures
 * - Adapter priority ordering
 * - Edge cases
 */
class TransactionAdapterTest {

    // ========== TRANSACTION ADAPTER INTERFACE TESTS ==========

    @Test
    fun `adapter priority should default to 0`() {
        // Given
        val adapter = SimpleAdapter("test")

        // When
        val priority = adapter.priority()

        // Then
        assertEquals(0, priority)
    }

    @Test
    fun `adapter isCritical should default to false`() {
        // Given
        val adapter = SimpleAdapter("test")

        // When
        val isCritical = adapter.isCritical()

        // Then
        assertFalse(isCritical)
    }

    @Test
    fun `adapter should support custom priority`() {
        // Given
        val adapter = PriorityAdapter("test", priority = 50)

        // When
        val priority = adapter.priority()

        // Then
        assertEquals(50, priority)
    }

    @Test
    fun `adapter should support critical flag`() {
        // Given
        val adapter = CriticalAdapter("test")

        // When
        val isCritical = adapter.isCritical()

        // Then
        assertTrue(isCritical)
    }

    @Test
    fun `adapter should execute onPhase for all phases`() = runTest {
        // Given
        val adapter = TrackingAdapter("test")
        val context = TransactionEventContext()

        // When - Execute for all phases
        TransactionPhase.entries.forEach { phase ->
            adapter.onPhase(phase, context)
        }

        // Then
        assertEquals(TransactionPhase.entries.size, adapter.invokedPhases.size)
        assertTrue(adapter.invokedPhases.containsAll(TransactionPhase.entries))
    }

    @Test
    fun `adapter name should be accessible`() {
        // Given
        val name = "EventsAdapter"
        val adapter = SimpleAdapter(name)

        // When
        val retrievedName = adapter.name()

        // Then
        assertEquals(name, retrievedName)
    }

    // ========== ADAPTER EXECUTION RESULT TESTS ==========

    @Test
    fun `AdapterExecutionResult should store all fields correctly`() {
        // Given
        val adapter = SimpleAdapter("test")
        val phase = TransactionPhase.AFTER_COMMIT
        val success = true
        val error: Exception? = null
        val duration = 150L

        // When
        val result = AdapterExecutionResult(
            adapter = adapter,
            phase = phase,
            success = success,
            error = error,
            duration = duration
        )

        // Then
        assertEquals(adapter, result.adapter)
        assertEquals(phase, result.phase)
        assertEquals(success, result.success)
        assertNull(result.error)
        assertEquals(duration, result.duration)
    }

    @Test
    fun `AdapterExecutionResult should handle failure with exception`() {
        // Given
        val adapter = SimpleAdapter("test")
        val phase = TransactionPhase.BEFORE_COMMIT
        val error = RuntimeException("Test error")

        // When
        val result = AdapterExecutionResult(
            adapter = adapter,
            phase = phase,
            success = false,
            error = error,
            duration = 100L
        )

        // Then
        assertFalse(result.success)
        assertEquals(error, result.error)
    }

    @Test
    fun `AdapterExecutionResult should support data class equality`() {
        // Given
        val adapter = SimpleAdapter("test")
        val result1 = AdapterExecutionResult(adapter, TransactionPhase.AFTER_COMMIT, true, null, 100L)
        val result2 = AdapterExecutionResult(adapter, TransactionPhase.AFTER_COMMIT, true, null, 100L)

        // Then
        assertEquals(result1, result2)
    }

    // ========== PHASE EXECUTION RESULTS TESTS ==========

    @Test
    fun `PhaseExecutionResults should aggregate adapter results`() {
        // Given
        val adapter1 = SimpleAdapter("adapter1")
        val adapter2 = SimpleAdapter("adapter2")
        val phase = TransactionPhase.AFTER_COMMIT

        val results = listOf(
            AdapterExecutionResult(adapter1, phase, true, null, 100L),
            AdapterExecutionResult(adapter2, phase, true, null, 150L)
        )

        // When
        val phaseResults = PhaseExecutionResults(phase, results)

        // Then
        assertEquals(phase, phaseResults.phase)
        assertEquals(2, phaseResults.results.size)
    }

    @Test
    fun `hasCriticalFailures should return false when no critical adapters failed`() {
        // Given
        val nonCriticalAdapter = SimpleAdapter("non-critical")
        val results = listOf(
            AdapterExecutionResult(nonCriticalAdapter, TransactionPhase.AFTER_COMMIT, false, RuntimeException(), 100L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.AFTER_COMMIT, results)

        // Then
        assertFalse(phaseResults.hasCriticalFailures())
    }

    @Test
    fun `hasCriticalFailures should return true when critical adapter failed`() {
        // Given
        val criticalAdapter = CriticalAdapter("critical")
        val results = listOf(
            AdapterExecutionResult(criticalAdapter, TransactionPhase.BEFORE_COMMIT, false, RuntimeException(), 100L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.BEFORE_COMMIT, results)

        // Then
        assertTrue(phaseResults.hasCriticalFailures())
    }

    @Test
    fun `getCriticalFailures should return only critical adapter failures`() {
        // Given
        val criticalAdapter = CriticalAdapter("critical")
        val nonCriticalAdapter = SimpleAdapter("non-critical")

        val results = listOf(
            AdapterExecutionResult(criticalAdapter, TransactionPhase.BEFORE_COMMIT, false, RuntimeException("Critical error"), 100L),
            AdapterExecutionResult(nonCriticalAdapter, TransactionPhase.BEFORE_COMMIT, false, RuntimeException("Non-critical error"), 50L),
            AdapterExecutionResult(SimpleAdapter("success"), TransactionPhase.BEFORE_COMMIT, true, null, 75L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.BEFORE_COMMIT, results)
        val criticalFailures = phaseResults.getCriticalFailures()

        // Then
        assertEquals(1, criticalFailures.size)
        assertEquals(criticalAdapter, criticalFailures[0].adapter)
    }

    @Test
    fun `getNonCriticalFailures should return only non-critical adapter failures`() {
        // Given
        val criticalAdapter = CriticalAdapter("critical")
        val nonCriticalAdapter = SimpleAdapter("non-critical")

        val results = listOf(
            AdapterExecutionResult(criticalAdapter, TransactionPhase.BEFORE_COMMIT, false, RuntimeException(), 100L),
            AdapterExecutionResult(nonCriticalAdapter, TransactionPhase.BEFORE_COMMIT, false, RuntimeException(), 50L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.BEFORE_COMMIT, results)
        val nonCriticalFailures = phaseResults.getNonCriticalFailures()

        // Then
        assertEquals(1, nonCriticalFailures.size)
        assertEquals(nonCriticalAdapter, nonCriticalFailures[0].adapter)
    }

    @Test
    fun `getSuccesses should return only successful executions`() {
        // Given
        val adapter1 = SimpleAdapter("success1")
        val adapter2 = SimpleAdapter("success2")
        val adapter3 = SimpleAdapter("failure")

        val results = listOf(
            AdapterExecutionResult(adapter1, TransactionPhase.AFTER_COMMIT, true, null, 100L),
            AdapterExecutionResult(adapter2, TransactionPhase.AFTER_COMMIT, true, null, 150L),
            AdapterExecutionResult(adapter3, TransactionPhase.AFTER_COMMIT, false, RuntimeException(), 50L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.AFTER_COMMIT, results)
        val successes = phaseResults.getSuccesses()

        // Then
        assertEquals(2, successes.size)
        assertTrue(successes.all { it.success })
    }

    @Test
    fun `totalDuration should sum all adapter durations`() {
        // Given
        val results = listOf(
            AdapterExecutionResult(SimpleAdapter("a1"), TransactionPhase.AFTER_COMMIT, true, null, 100L),
            AdapterExecutionResult(SimpleAdapter("a2"), TransactionPhase.AFTER_COMMIT, true, null, 150L),
            AdapterExecutionResult(SimpleAdapter("a3"), TransactionPhase.AFTER_COMMIT, true, null, 250L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.AFTER_COMMIT, results)
        val totalDuration = phaseResults.totalDuration()

        // Then
        assertEquals(500L, totalDuration)
    }

    @Test
    fun `totalDuration should return 0 for empty results`() {
        // Given
        val phaseResults = PhaseExecutionResults(TransactionPhase.AFTER_COMMIT, emptyList())

        // When
        val totalDuration = phaseResults.totalDuration()

        // Then
        assertEquals(0L, totalDuration)
    }

    @Test
    fun `getSummary should provide readable summary`() {
        // Given
        val criticalAdapter = CriticalAdapter("critical")
        val results = listOf(
            AdapterExecutionResult(SimpleAdapter("success1"), TransactionPhase.AFTER_COMMIT, true, null, 100L),
            AdapterExecutionResult(SimpleAdapter("success2"), TransactionPhase.AFTER_COMMIT, true, null, 150L),
            AdapterExecutionResult(criticalAdapter, TransactionPhase.AFTER_COMMIT, false, RuntimeException(), 50L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.AFTER_COMMIT, results)
        val summary = phaseResults.getSummary()

        // Then
        assertTrue(summary.contains("AFTER_COMMIT"))
        assertTrue(summary.contains("Total: 3"))
        assertTrue(summary.contains("Success: 2"))
        assertTrue(summary.contains("Failure: 1"))
        assertTrue(summary.contains("Critical: 1"))
        assertTrue(summary.contains("300ms"))
    }

    // ========== ADAPTER PRIORITY TESTS ==========

    @Test
    fun `adapters should be orderable by priority`() {
        // Given
        val lowPriority = PriorityAdapter("low", priority = 10)
        val mediumPriority = PriorityAdapter("medium", priority = 50)
        val highPriority = PriorityAdapter("high", priority = 100)

        val adapters = listOf(lowPriority, highPriority, mediumPriority)

        // When
        val sorted = adapters.sortedByDescending { it.priority() }

        // Then
        assertEquals(highPriority, sorted[0])
        assertEquals(mediumPriority, sorted[1])
        assertEquals(lowPriority, sorted[2])
    }

    @Test
    fun `adapters with same priority should maintain order`() {
        // Given
        val adapter1 = PriorityAdapter("adapter1", priority = 50)
        val adapter2 = PriorityAdapter("adapter2", priority = 50)
        val adapter3 = PriorityAdapter("adapter3", priority = 50)

        val adapters = listOf(adapter1, adapter2, adapter3)

        // When
        val sorted = adapters.sortedByDescending { it.priority() }

        // Then
        // With stable sort, order should be preserved
        assertEquals(adapter1, sorted[0])
        assertEquals(adapter2, sorted[1])
        assertEquals(adapter3, sorted[2])
    }

    // ========== EDGE CASES ==========

    @Test
    fun `PhaseExecutionResults should handle empty results`() {
        // Given
        val phaseResults = PhaseExecutionResults(TransactionPhase.BEFORE_BEGIN, emptyList())

        // Then
        assertFalse(phaseResults.hasCriticalFailures())
        assertTrue(phaseResults.getCriticalFailures().isEmpty())
        assertTrue(phaseResults.getNonCriticalFailures().isEmpty())
        assertTrue(phaseResults.getSuccesses().isEmpty())
        assertEquals(0L, phaseResults.totalDuration())
    }

    @Test
    fun `PhaseExecutionResults should handle all successes`() {
        // Given
        val results = listOf(
            AdapterExecutionResult(SimpleAdapter("a1"), TransactionPhase.AFTER_COMMIT, true, null, 100L),
            AdapterExecutionResult(SimpleAdapter("a2"), TransactionPhase.AFTER_COMMIT, true, null, 150L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.AFTER_COMMIT, results)

        // Then
        assertFalse(phaseResults.hasCriticalFailures())
        assertEquals(0, phaseResults.getCriticalFailures().size)
        assertEquals(0, phaseResults.getNonCriticalFailures().size)
        assertEquals(2, phaseResults.getSuccesses().size)
    }

    @Test
    fun `PhaseExecutionResults should handle all failures`() {
        // Given
        val results = listOf(
            AdapterExecutionResult(SimpleAdapter("a1"), TransactionPhase.AFTER_COMMIT, false, RuntimeException(), 100L),
            AdapterExecutionResult(CriticalAdapter("a2"), TransactionPhase.AFTER_COMMIT, false, RuntimeException(), 150L)
        )

        // When
        val phaseResults = PhaseExecutionResults(TransactionPhase.AFTER_COMMIT, results)

        // Then
        assertTrue(phaseResults.hasCriticalFailures())
        assertEquals(1, phaseResults.getCriticalFailures().size)
        assertEquals(1, phaseResults.getNonCriticalFailures().size)
        assertEquals(0, phaseResults.getSuccesses().size)
    }

    @Test
    fun `adapter should handle very long execution time`() {
        // Given
        val adapter = SimpleAdapter("slow")
        val duration = Long.MAX_VALUE

        // When
        val result = AdapterExecutionResult(adapter, TransactionPhase.AFTER_COMMIT, true, null, duration)

        // Then
        assertEquals(duration, result.duration)
    }

    @Test
    fun `adapter should handle zero execution time`() {
        // Given
        val adapter = SimpleAdapter("fast")

        // When
        val result = AdapterExecutionResult(adapter, TransactionPhase.AFTER_COMMIT, true, null, 0L)

        // Then
        assertEquals(0L, result.duration)
    }

    // ========== TEST ADAPTER IMPLEMENTATIONS ==========

    private class SimpleAdapter(private val adapterName: String) : TransactionAdapter {
        override fun name(): String = adapterName

        override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
            // No-op
        }
    }

    private class PriorityAdapter(
        private val adapterName: String,
        private val priority: Int
    ) : TransactionAdapter {
        override fun name(): String = adapterName

        override fun priority(): Int = priority

        override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
            // No-op
        }
    }

    private class CriticalAdapter(private val adapterName: String) : TransactionAdapter {
        override fun name(): String = adapterName

        override fun isCritical(): Boolean = true

        override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
            // No-op
        }
    }

    private class TrackingAdapter(private val adapterName: String) : TransactionAdapter {
        val invokedPhases = mutableListOf<TransactionPhase>()

        override fun name(): String = adapterName

        override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
            invokedPhases.add(phase)
        }
    }
}
