package com.ead.katalyst.transactions.adapter

import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import com.ead.katalyst.events.DomainEvent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for TransactionAdapterRegistry.
 *
 * Tests adapter registration, execution, priority ordering,
 * and critical failure handling.
 */
@DisplayName("Transaction Adapter Registry Tests")
class TransactionAdapterRegistryTest {

    // Test adapter implementations
    private class TestAdapter(
        private val adapterName: String,
        private val adapterPriority: Int = 0,
        private val isCriticalAdapter: Boolean = false
    ) : TransactionAdapter {
        var executionCount = 0
        var lastPhaseExecuted: TransactionPhase? = null
        var throwException: Exception? = null

        override fun name(): String = adapterName
        override fun priority(): Int = adapterPriority
        override fun isCritical(): Boolean = isCriticalAdapter

        override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
            executionCount++
            lastPhaseExecuted = phase
            if (throwException != null) {
                throw throwException!!
            }
        }
    }

    private lateinit var registry: TransactionAdapterRegistry
    private lateinit var mockContext: TransactionEventContext

    @BeforeEach
    fun setup() {
        registry = TransactionAdapterRegistry()
        mockContext = TransactionEventContext()
    }

    @Test
    @DisplayName("Should register single adapter")
    fun testRegisterSingleAdapter() {
        // Arrange
        val adapter = TestAdapter("test-adapter")

        // Act
        registry.register(adapter)

        // Assert
        assertEquals(1, registry.size())
        assertEquals(adapter, registry.getAdapters()[0])
    }

    @Test
    @DisplayName("Should register multiple adapters")
    fun testRegisterMultipleAdapters() {
        // Arrange
        val adapter1 = TestAdapter("adapter-1")
        val adapter2 = TestAdapter("adapter-2")
        val adapter3 = TestAdapter("adapter-3")

        // Act
        registry.register(adapter1)
        registry.register(adapter2)
        registry.register(adapter3)

        // Assert
        assertEquals(3, registry.size())
    }

    @Test
    @DisplayName("Should sort adapters by priority (high first)")
    fun testAdapterSortingByPriority() {
        // Arrange
        val lowPriority = TestAdapter("low-priority", adapterPriority = 1)
        val highPriority = TestAdapter("high-priority", adapterPriority =100)
        val mediumPriority = TestAdapter("medium-priority", adapterPriority =50)

        // Act
        registry.register(lowPriority)
        registry.register(highPriority)
        registry.register(mediumPriority)

        // Assert
        val adapters = registry.getAdapters()
        assertEquals(3, adapters.size)
        assertEquals("high-priority", adapters[0].name())
        assertEquals("medium-priority", adapters[1].name())
        assertEquals("low-priority", adapters[2].name())
    }

    @Test
    @DisplayName("Should maintain priority order after registration")
    fun testPriorityOrderAfterMultipleRegistrations() {
        // Arrange
        val adapter10 = TestAdapter("adapter-10", adapterPriority =10)
        val adapter20 = TestAdapter("adapter-20", adapterPriority =20)
        val adapter5 = TestAdapter("adapter-5", adapterPriority =5)
        val adapter50 = TestAdapter("adapter-50", adapterPriority =50)

        // Act
        registry.register(adapter10)
        registry.register(adapter20)
        registry.register(adapter5)
        registry.register(adapter50)

        // Assert
        val adapters = registry.getAdapters()
        assertEquals("adapter-50", adapters[0].name())
        assertEquals("adapter-20", adapters[1].name())
        assertEquals("adapter-10", adapters[2].name())
        assertEquals("adapter-5", adapters[3].name())
    }

    @Test
    @DisplayName("Should unregister adapter")
    fun testUnregisterAdapter() {
        // Arrange
        val adapter = TestAdapter("to-remove")
        registry.register(adapter)
        assertEquals(1, registry.size())

        // Act
        registry.unregister(adapter)

        // Assert
        assertEquals(0, registry.size())
    }

    @Test
    @DisplayName("Should clear all adapters")
    fun testClearAllAdapters() {
        // Arrange
        registry.register(TestAdapter("adapter-1"))
        registry.register(TestAdapter("adapter-2"))
        registry.register(TestAdapter("adapter-3"))
        assertEquals(3, registry.size())

        // Act
        registry.clear()

        // Assert
        assertEquals(0, registry.size())
    }

    @Test
    @DisplayName("Should execute adapters in priority order")
    fun testExecuteAdaptersInPriorityOrder() = runTest {
        // Arrange
        val adapter5 = TestAdapter("adapter-5", adapterPriority =5)
        val adapter10 = TestAdapter("adapter-10", adapterPriority =10)
        val adapter1 = TestAdapter("adapter-1", adapterPriority =1)
        val phase = TransactionPhase.AFTER_BEGIN

        registry.register(adapter5)
        registry.register(adapter10)
        registry.register(adapter1)

        // Act
        registry.executeAdapters(phase, mockContext)

        // Assert
        val adapters = registry.getAdapters()
        assertEquals(adapter10, adapters[0])
        assertEquals(adapter5, adapters[1])
        assertEquals(adapter1, adapters[2])
    }

    @Test
    @DisplayName("Should track execution results")
    fun testTrackExecutionResults() = runTest {
        // Arrange
        val adapter = TestAdapter("test-adapter")
        registry.register(adapter)

        // Act
        val results = registry.executeAdapters(TransactionPhase.AFTER_BEGIN, mockContext)

        // Assert
        assertEquals(1, results.results.size)
        assertTrue(results.results[0].success)
        assertEquals(adapter, results.results[0].adapter)
        assertEquals(TransactionPhase.AFTER_BEGIN, results.results[0].phase)
    }

    @Test
    @DisplayName("Should record execution duration")
    fun testRecordExecutionDuration() = runTest {
        // Arrange
        val adapter = TestAdapter("test-adapter")
        registry.register(adapter)

        // Act
        val results = registry.executeAdapters(TransactionPhase.AFTER_BEGIN, mockContext)

        // Assert
        assertEquals(1, results.results.size)
        assertTrue(results.results[0].duration >= 0)
    }

    @Test
    @DisplayName("Should handle adapter exceptions")
    fun testHandleAdapterExceptions() = runTest {
        // Arrange
        val exception = IllegalStateException("Test error")
        val failingAdapter = TestAdapter("failing-adapter")
        failingAdapter.throwException = exception

        val successAdapter = TestAdapter("success-adapter")

        registry.register(failingAdapter)
        registry.register(successAdapter)

        // Act
        val results = registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext, failFast = false)

        // Assert
        assertEquals(2, results.results.size)
        assertFalse(results.results[0].success)
        assertEquals(exception, results.results[0].error)
        assertTrue(results.results[1].success)
    }

    @Test
    @DisplayName("Should continue execution on non-critical adapter failure")
    fun testContinueOnNonCriticalFailure() = runTest {
        // Arrange
        val failingAdapter = TestAdapter("failing-adapter", isCriticalAdapter = false)
        failingAdapter.throwException = RuntimeException("Test error")

        val adapter1 = TestAdapter("adapter-1")
        val adapter2 = TestAdapter("adapter-2")

        registry.register(failingAdapter)
        registry.register(adapter1)
        registry.register(adapter2)

        // Act
        val results = registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext, failFast = false)

        // Assert
        assertEquals(3, results.results.size)
        assertTrue(adapter1.executionCount > 0, "adapter1 should have executed")
        assertTrue(adapter2.executionCount > 0, "adapter2 should have executed")
    }

    @Test
    @DisplayName("Should throw on critical adapter failure with failFast=true")
    fun testThrowOnCriticalFailureWithFailFast() = runTest {
        // Arrange
        val criticalAdapter = TestAdapter("critical-adapter", isCriticalAdapter = true)
        criticalAdapter.throwException = RuntimeException("Critical error")

        registry.register(criticalAdapter)

        // Act & Assert
        try {
            registry.executeAdapters(TransactionPhase.BEFORE_COMMIT_VALIDATION, mockContext, failFast = true)
            fail("Should have thrown TransactionAdapterException")
        } catch (e: TransactionAdapterException) {
            assertTrue(e.message!!.contains("Critical adapter"))
            assertEquals(criticalAdapter.throwException, e.cause)
        }
    }

    @Test
    @DisplayName("Should not throw on critical adapter failure with failFast=false")
    fun testNoThrowOnCriticalFailureWithoutFailFast() = runTest {
        // Arrange
        val criticalAdapter = TestAdapter("critical-adapter", isCriticalAdapter = true)
        criticalAdapter.throwException = RuntimeException("Critical error")

        registry.register(criticalAdapter)

        // Act
        val results = registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext, failFast = false)

        // Assert
        assertEquals(1, results.results.size)
        assertFalse(results.results[0].success)
    }

    @Test
    @DisplayName("PhaseExecutionResults should detect critical failures")
    fun testDetectCriticalFailures() = runTest {
        // Arrange
        val criticalAdapter = TestAdapter("critical", isCriticalAdapter = true)
        criticalAdapter.throwException = RuntimeException("Error")

        val nonCriticalAdapter = TestAdapter("non-critical", isCriticalAdapter = false)
        nonCriticalAdapter.throwException = RuntimeException("Error")

        registry.register(criticalAdapter)
        registry.register(nonCriticalAdapter)

        // Act
        val results = registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext, failFast = false)

        // Assert
        assertTrue(results.hasCriticalFailures())
        assertEquals(1, results.getCriticalFailures().size)
        assertEquals(1, results.getNonCriticalFailures().size)
    }

    @Test
    @DisplayName("PhaseExecutionResults should categorize successes and failures")
    fun testCategorizeResults() = runTest {
        // Arrange
        val successAdapter1 = TestAdapter("success-1")
        val successAdapter2 = TestAdapter("success-2")
        val failingAdapter = TestAdapter("failing")
        failingAdapter.throwException = RuntimeException("Error")

        registry.register(successAdapter1)
        registry.register(failingAdapter)
        registry.register(successAdapter2)

        // Act
        val results = registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext, failFast = false)

        // Assert
        assertEquals(2, results.getSuccesses().size)
        assertEquals(1, results.getNonCriticalFailures().size)
        assertEquals(0, results.getCriticalFailures().size)
    }

    @Test
    @DisplayName("PhaseExecutionResults should calculate total duration")
    fun testCalculateTotalDuration() = runTest {
        // Arrange
        val adapter1 = TestAdapter("adapter-1")
        val adapter2 = TestAdapter("adapter-2")
        registry.register(adapter1)
        registry.register(adapter2)

        // Act
        val results = registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext)

        // Assert
        val totalDuration = results.totalDuration()
        assertTrue(totalDuration >= 0)
    }

    @Test
    @DisplayName("PhaseExecutionResults should provide summary")
    fun testGetExecutionSummary() = runTest {
        // Arrange
        val adapter1 = TestAdapter("adapter-1")
        val adapter2 = TestAdapter("adapter-2")
        registry.register(adapter1)
        registry.register(adapter2)

        // Act
        val results = registry.executeAdapters(TransactionPhase.AFTER_COMMIT, mockContext)

        // Assert
        val summary = results.getSummary()
        assertTrue(summary.contains("AFTER_COMMIT"))
        assertTrue(summary.contains("Success: 2"))
        assertTrue(summary.contains("Duration:"))
    }

    @Test
    @DisplayName("Should return empty results for no adapters")
    fun testEmptyResultsForNoAdapters() = runTest {
        // Act
        val results = registry.executeAdapters(TransactionPhase.AFTER_BEGIN, mockContext)

        // Assert
        assertTrue(results.results.isEmpty())
        assertFalse(results.hasCriticalFailures())
    }

    @Test
    @DisplayName("Should execute each adapter only once per phase")
    fun testExecuteEachAdapterOncePerPhase() = runTest {
        // Arrange
        val adapter1 = TestAdapter("adapter-1")
        val adapter2 = TestAdapter("adapter-2")
        registry.register(adapter1)
        registry.register(adapter2)

        // Act
        registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext)

        // Assert
        assertEquals(1, adapter1.executionCount)
        assertEquals(1, adapter2.executionCount)
    }

    @Test
    @DisplayName("Should execute adapters for different phases independently")
    fun testExecuteIndependentlyAcrossPhases() = runTest {
        // Arrange
        val adapter = TestAdapter("test-adapter")
        registry.register(adapter)

        // Act
        registry.executeAdapters(TransactionPhase.BEFORE_BEGIN, mockContext)
        registry.executeAdapters(TransactionPhase.AFTER_BEGIN, mockContext)
        registry.executeAdapters(TransactionPhase.BEFORE_COMMIT, mockContext)

        // Assert
        assertEquals(3, adapter.executionCount)
        assertEquals(TransactionPhase.BEFORE_COMMIT, adapter.lastPhaseExecuted)
    }

    @Test
    @DisplayName("Should handle same adapter registered twice")
    fun testHandleDuplicateRegistration() {
        // Arrange
        val adapter = TestAdapter("test-adapter")

        // Act
        registry.register(adapter)
        registry.register(adapter)

        // Assert
        assertEquals(2, registry.size())
    }

    @Test
    @DisplayName("Should preserve adapter state after execution")
    fun testPreserveAdapterStateAfterExecution() = runTest {
        // Arrange
        val adapter = TestAdapter("test-adapter")
        registry.register(adapter)

        // Act
        registry.executeAdapters(TransactionPhase.AFTER_BEGIN, mockContext)

        // Assert
        assertEquals(1, adapter.executionCount)
        assertEquals(TransactionPhase.AFTER_BEGIN, adapter.lastPhaseExecuted)
    }

}
