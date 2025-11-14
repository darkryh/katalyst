package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.SimpleTransactionOperation
import com.ead.katalyst.transactions.workflow.TransactionOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for SimpleUndoEngine.
 *
 * Tests cover:
 * - LIFO execution order (most recent first)
 * - Best-effort approach (continues on failure)
 * - Success/failure counting
 * - Exception handling
 * - Empty operations list
 * - Large operation sets
 */
class SimpleUndoEngineTest {

    private val engine = SimpleUndoEngine()

    // ========== LIFO EXECUTION ORDER TESTS ==========

    @Test
    fun `undoWorkflow should execute operations in reverse order`() = runTest {
        // Given
        val executionOrder = mutableListOf<Int>()
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) {
                executionOrder.add(0)
                true
            },
            createOperation(workflowId = "wf1", index = 1) {
                executionOrder.add(1)
                true
            },
            createOperation(workflowId = "wf1", index = 2) {
                executionOrder.add(2)
                true
            }
        )

        // When
        engine.undoWorkflow("wf1", operations)

        // Then - Should execute in reverse order: 2, 1, 0
        assertEquals(listOf(2, 1, 0), executionOrder)
    }

    @Test
    fun `undoWorkflow should execute 10 operations in correct LIFO order`() = runTest {
        // Given
        val executionOrder = mutableListOf<Int>()
        val operations = (0..9).map { index ->
            createOperation(workflowId = "wf1", index = index) {
                executionOrder.add(index)
                true
            }
        }

        // When
        engine.undoWorkflow("wf1", operations)

        // Then - Should execute in reverse order: 9, 8, 7, ... 1, 0
        assertEquals((9 downTo 0).toList(), executionOrder)
    }

    @Test
    fun `undoWorkflow should execute 100 operations in LIFO order`() = runTest {
        // Given
        val executionOrder = mutableListOf<Int>()
        val operations = (0..99).map { index ->
            createOperation(workflowId = "wf1", index = index) {
                executionOrder.add(index)
                true
            }
        }

        // When
        engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals((99 downTo 0).toList(), executionOrder)
    }

    // ========== SUCCESS SCENARIOS ==========

    @Test
    fun `undoWorkflow should succeed when all operations succeed`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { true },
            createOperation(workflowId = "wf1", index = 1) { true },
            createOperation(workflowId = "wf1", index = 2) { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals("wf1", result.workflowId)
        assertEquals(3, result.totalOperations)
        assertEquals(3, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isFullySuccessful)
    }

    @Test
    fun `undoWorkflow should track individual operation results`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT") { true },
            createOperation(workflowId = "wf1", index = 1, type = "UPDATE") { true },
            createOperation(workflowId = "wf1", index = 2, type = "DELETE") { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(3, result.results.size)
        // Results should be in reverse order (LIFO)
        assertEquals(2, result.results[0].operationIndex)
        assertEquals("DELETE", result.results[0].operationType)
        assertTrue(result.results[0].succeeded)

        assertEquals(1, result.results[1].operationIndex)
        assertEquals("UPDATE", result.results[1].operationType)
        assertTrue(result.results[1].succeeded)

        assertEquals(0, result.results[2].operationIndex)
        assertEquals("INSERT", result.results[2].operationType)
        assertTrue(result.results[2].succeeded)
    }

    // ========== FAILURE SCENARIOS ==========

    @Test
    fun `undoWorkflow should continue when one operation fails`() = runTest {
        // Given
        val executionOrder = mutableListOf<Int>()
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) {
                executionOrder.add(0)
                true
            },
            createOperation(workflowId = "wf1", index = 1) {
                executionOrder.add(1)
                false  // Fails
            },
            createOperation(workflowId = "wf1", index = 2) {
                executionOrder.add(2)
                true
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then - Should execute all operations despite failure
        assertEquals(listOf(2, 1, 0), executionOrder)
        assertEquals(2, result.succeededCount)
        assertEquals(1, result.failedCount)
        assertFalse(result.isFullySuccessful)
    }

    @Test
    fun `undoWorkflow should track which operations failed`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { true },
            createOperation(workflowId = "wf1", index = 1) { false },  // Fails
            createOperation(workflowId = "wf1", index = 2) { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        val failedOperation = result.results.find { !it.succeeded }
        assertNotNull(failedOperation)
        assertEquals(1, failedOperation.operationIndex)
        assertEquals("Undo returned false", failedOperation.error)
    }

    @Test
    fun `undoWorkflow should handle multiple failures`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { false },  // Fails
            createOperation(workflowId = "wf1", index = 1) { true },
            createOperation(workflowId = "wf1", index = 2) { false },  // Fails
            createOperation(workflowId = "wf1", index = 3) { false },  // Fails
            createOperation(workflowId = "wf1", index = 4) { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(5, result.totalOperations)
        assertEquals(2, result.succeededCount)
        assertEquals(3, result.failedCount)
        assertFalse(result.isFullySuccessful)
    }

    // ========== EXCEPTION HANDLING TESTS ==========

    @Test
    fun `undoWorkflow should catch and continue on exception`() = runTest {
        // Given
        val executionOrder = mutableListOf<Int>()
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) {
                executionOrder.add(0)
                true
            },
            createOperation(workflowId = "wf1", index = 1) {
                executionOrder.add(1)
                throw RuntimeException("Simulated failure")
            },
            createOperation(workflowId = "wf1", index = 2) {
                executionOrder.add(2)
                true
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then - Should execute all operations despite exception
        assertEquals(listOf(2, 1, 0), executionOrder)
        assertEquals(2, result.succeededCount)
        assertEquals(1, result.failedCount)
    }

    @Test
    fun `undoWorkflow should capture exception message in result`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) {
                throw IllegalStateException("Invalid state error")
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        val failedResult = result.results.first()
        assertFalse(failedResult.succeeded)
        assertTrue(failedResult.error?.contains("Invalid state error") == true)
    }

    @Test
    fun `undoWorkflow should handle multiple exceptions`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) {
                throw RuntimeException("Error 1")
            },
            createOperation(workflowId = "wf1", index = 1) { true },
            createOperation(workflowId = "wf1", index = 2) {
                throw IllegalArgumentException("Error 2")
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(1, result.succeededCount)
        assertEquals(2, result.failedCount)

        val failedResults = result.results.filter { !it.succeeded }
        assertEquals(2, failedResults.size)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `undoWorkflow should handle empty operations list`() = runTest {
        // When
        val result = engine.undoWorkflow("wf1", emptyList())

        // Then
        assertEquals("wf1", result.workflowId)
        assertEquals(0, result.totalOperations)
        assertEquals(0, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isFullySuccessful)
        assertEquals(0, result.results.size)
    }

    @Test
    fun `undoWorkflow should handle single operation`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(1, result.totalOperations)
        assertEquals(1, result.succeededCount)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun `undoWorkflow should handle all operations failing`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { false },
            createOperation(workflowId = "wf1", index = 1) { false },
            createOperation(workflowId = "wf1", index = 2) { false }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(3, result.totalOperations)
        assertEquals(0, result.succeededCount)
        assertEquals(3, result.failedCount)
        assertFalse(result.isFullySuccessful)
    }

    // ========== RESULT SUMMARY TESTS ==========

    @Test
    fun `UndoResult summary should be formatted correctly`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { true },
            createOperation(workflowId = "wf1", index = 1) { false },
            createOperation(workflowId = "wf1", index = 2) { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertTrue(result.summary.contains("2 succeeded"))
        assertTrue(result.summary.contains("1 failed"))
        assertTrue(result.summary.contains("3"))
    }

    @Test
    fun `isFullySuccessful should be false when any operation fails`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { true },
            createOperation(workflowId = "wf1", index = 1) { false },  // One failure
            createOperation(workflowId = "wf1", index = 2) { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertFalse(result.isFullySuccessful)
    }

    @Test
    fun `isFullySuccessful should be true when all operations succeed`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { true },
            createOperation(workflowId = "wf1", index = 1) { true },
            createOperation(workflowId = "wf1", index = 2) { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertTrue(result.isFullySuccessful)
    }

    // ========== RESOURCE TYPE TRACKING ==========

    @Test
    fun `undoWorkflow should track resource types in results`() = runTest {
        // Given
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT", resource = "User") { true },
            createOperation(workflowId = "wf1", index = 1, type = "UPDATE", resource = "Order") { true },
            createOperation(workflowId = "wf1", index = 2, type = "DELETE", resource = "Payment") { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals("Payment", result.results[0].resourceType)
        assertEquals("Order", result.results[1].resourceType)
        assertEquals("User", result.results[2].resourceType)
    }

    // ========== HELPER METHODS ==========

    private fun createOperation(
        workflowId: String,
        index: Int,
        type: String = "INSERT",
        resource: String = "User",
        undoAction: suspend () -> Boolean
    ): TransactionOperation {
        return SimpleTransactionOperation(
            workflowId = workflowId,
            operationIndex = index,
            operationType = type,
            resourceType = resource,
            resourceId = "resource-$index",
            undoAction = undoAction
        )
    }
}
