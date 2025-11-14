package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.SimpleTransactionOperation
import com.ead.katalyst.transactions.workflow.TransactionOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for EnhancedUndoEngine.
 *
 * Tests cover:
 * - Strategy-based undo execution
 * - Retry policy integration
 * - LIFO execution order
 * - Best-effort approach
 * - Strategy lookup and fallback
 * - Custom strategies and policies
 */
class EnhancedUndoEngineTest {

    // ========== BASIC FUNCTIONALITY ==========

    @Test
    fun `undoWorkflow should execute operations in reverse order with default strategies`() = runTest {
        // Given
        val engine = delegatingEngine()
        val executionOrder = mutableListOf<Int>()
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT") {
                executionOrder.add(0)
                true
            },
            createOperation(workflowId = "wf1", index = 1, type = "UPDATE") {
                executionOrder.add(1)
                true
            },
            createOperation(workflowId = "wf1", index = 2, type = "DELETE") {
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
    fun `undoWorkflow should use appropriate strategy for each operation type`() = runTest {
        // Given
        val registry = UndoStrategyRegistry.createDefault()
        val engine = EnhancedUndoEngine(strategyRegistry = registry)

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT") { true },
            createOperation(workflowId = "wf1", index = 1, type = "UPDATE") { true },
            createOperation(workflowId = "wf1", index = 2, type = "DELETE") { true },
            createOperation(workflowId = "wf1", index = 3, type = "API_CALL") { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(4, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isFullySuccessful)
    }

    // ========== RETRY POLICY INTEGRATION ==========

    @Test
    fun `undoWorkflow should use retry policy for operations`() = runTest {
        // Given
        val retryPolicy = RetryPolicy(maxRetries = 2, initialDelayMs = 1)
        val engine = delegatingEngine(retryPolicy = retryPolicy)

        var attemptCount = 0
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT") {
                attemptCount++
                if (attemptCount < 2) {
                    throw RuntimeException("Transient failure")
                }
                true
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then - Should succeed after retry
        assertEquals(1, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertEquals(2, attemptCount) // Failed once, succeeded on retry
    }

    @Test
    fun `undoWorkflow should exhaust retries and fail if operation keeps failing`() = runTest {
        // Given
        val retryPolicy = RetryPolicy(maxRetries = 2, initialDelayMs = 1)
        val engine = delegatingEngine(retryPolicy = retryPolicy)

        var attemptCount = 0
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT") {
                attemptCount++
                throw RuntimeException("Persistent failure")
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then - Should fail after exhausting retries
        assertEquals(0, result.succeededCount)
        assertEquals(1, result.failedCount)
        assertEquals(3, attemptCount) // Initial + 2 retries
    }

    @Test
    fun `undoWorkflow should use aggressive retry policy by default`() = runTest {
        // Given - Default engine uses aggressive policy (5 retries)
        val engine = delegatingEngine()

        var attemptCount = 0
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT") {
                attemptCount++
                if (attemptCount < 4) {
                    throw RuntimeException("Transient failure")
                }
                true
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then - Should succeed within aggressive retry limit
        assertEquals(1, result.succeededCount)
        assertEquals(4, attemptCount)
    }

    // ========== STRATEGY REGISTRY INTEGRATION ==========

    @Test
    fun `undoWorkflow should use custom strategy registry`() = runTest {
        // Given - Custom strategy that tracks invocations
        val customStrategyInvoked = mutableListOf<String>()
        val customStrategy = object : UndoStrategy {
            override fun canHandle(operationType: String, resourceType: String): Boolean {
                return operationType.uppercase() == "CUSTOM"
            }

            override suspend fun undo(operation: TransactionOperation): Boolean {
                customStrategyInvoked.add(operation.resourceId ?: "unknown")
                return true
            }
        }

        val registry = UndoStrategyRegistry().register(customStrategy)
        val engine = EnhancedUndoEngine(strategyRegistry = registry)

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "CUSTOM", resourceId = "res-1") { true }
        )

        // When
        engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(1, customStrategyInvoked.size)
        assertEquals("res-1", customStrategyInvoked[0])
    }

    @Test
    fun `undoWorkflow should fallback to NoOp strategy for unknown operation types`() = runTest {
        // Given - Empty registry (no strategies registered)
        val registry = UndoStrategyRegistry()
        val engine = EnhancedUndoEngine(strategyRegistry = registry)

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "UNKNOWN") { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then - NoOp strategy should return true
        assertEquals(1, result.succeededCount)
        assertEquals(0, result.failedCount)
    }

    // ========== BEST-EFFORT EXECUTION ==========

    @Test
    fun `undoWorkflow should continue on failure like SimpleUndoEngine`() = runTest {
        // Given
        val engine = delegatingEngine()
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

        // Then - Should execute all despite failure (retries may duplicate entries)
        assertEquals(listOf(2, 1, 0), executionOrder.distinct())
        assertEquals(2, result.succeededCount)
        assertEquals(1, result.failedCount)
    }

    @Test
    fun `undoWorkflow should handle multiple failures with retries`() = runTest {
        // Given
        val retryPolicy = RetryPolicy(maxRetries = 1, initialDelayMs = 1)
        val engine = delegatingEngine(retryPolicy = retryPolicy)

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { false },  // Fails
            createOperation(workflowId = "wf1", index = 1) { true },
            createOperation(workflowId = "wf1", index = 2) { false }   // Fails
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(1, result.succeededCount)
        assertEquals(2, result.failedCount)
    }

    // ========== ERROR TRACKING ==========

    @Test
    fun `undoWorkflow should provide detailed error messages`() = runTest {
        // Given
        val retryPolicy = RetryPolicy(maxRetries = 0, initialDelayMs = 1)
        val engine = delegatingEngine(retryPolicy = retryPolicy)

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) {
                throw IllegalStateException("Specific error message")
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        val failedResult = result.results.first()
        assertFalse(failedResult.succeeded)
        assertTrue(failedResult.error?.contains("returned false after retries") == true)
    }

    @Test
    fun `undoWorkflow should track operation that returned false after retries`() = runTest {
        // Given
        val retryPolicy = RetryPolicy(maxRetries = 2, initialDelayMs = 1)
        val engine = delegatingEngine(retryPolicy = retryPolicy)

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { false }  // Always returns false
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        val failedResult = result.results.first()
        assertFalse(failedResult.succeeded)
        assertTrue(failedResult.error?.contains("returned false after retries") == true)
    }

    // ========== LIFO ORDER WITH STRATEGIES ==========

    @Test
    fun `undoWorkflow should execute 10 operations in LIFO order with strategies`() = runTest {
        // Given
        val engine = delegatingEngine()
        val executionOrder = mutableListOf<Int>()

        val operations = (0..9).map { index ->
            createOperation(workflowId = "wf1", index = index) {
                executionOrder.add(index)
                true
            }
        }

        // When
        engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals((9 downTo 0).toList(), executionOrder)
    }

    // ========== RESULT TRACKING ==========

    @Test
    fun `undoWorkflow should track operation types and resources`() = runTest {
        // Given
        val engine = delegatingEngine()
        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0, type = "INSERT", resource = "User") { true },
            createOperation(workflowId = "wf1", index = 1, type = "UPDATE", resource = "Order") { true }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(2, result.results.size)
        // Results in reverse order
        assertEquals("UPDATE", result.results[0].operationType)
        assertEquals("Order", result.results[0].resourceType)
        assertEquals("INSERT", result.results[1].operationType)
        assertEquals("User", result.results[1].resourceType)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `undoWorkflow should handle empty operations list`() = runTest {
        // Given
        val engine = delegatingEngine()

        // When
        val result = engine.undoWorkflow("wf1", emptyList())

        // Then
        assertEquals(0, result.totalOperations)
        assertEquals(0, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isFullySuccessful)
    }

    @Test
    fun `undoWorkflow should handle single operation`() = runTest {
        // Given
        val engine = delegatingEngine()
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
    fun `undoWorkflow should handle 100 operations successfully`() = runTest {
        // Given
        val retryPolicy = RetryPolicy(maxRetries = 0, initialDelayMs = 1)  // Fast execution
        val engine = delegatingEngine(retryPolicy = retryPolicy)

        val operations = (0..99).map { index ->
            createOperation(workflowId = "wf1", index = index) { true }
        }

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(100, result.totalOperations)
        assertEquals(100, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isFullySuccessful)
    }

    // ========== PERFORMANCE & RETRY OPTIMIZATION ==========

    @Test
    fun `undoWorkflow should handle operations with different retry behaviors`() = runTest {
        // Given
        val retryPolicy = RetryPolicy(maxRetries = 2, initialDelayMs = 1)
        val engine = delegatingEngine(retryPolicy = retryPolicy)

        var op1Attempts = 0
        var op2Attempts = 0

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) {
                op1Attempts++
                true  // Succeeds immediately
            },
            createOperation(workflowId = "wf1", index = 1) {
                op2Attempts++
                if (op2Attempts < 2) throw RuntimeException("Retry needed")
                true  // Succeeds on retry
            }
        )

        // When
        val result = engine.undoWorkflow("wf1", operations)

        // Then
        assertEquals(2, result.succeededCount)
        assertEquals(1, op1Attempts)  // No retries needed
        assertEquals(2, op2Attempts)  // One retry needed
    }

    // ========== COMPARISON WITH SIMPLE ENGINE ==========

    @Test
    fun `EnhancedUndoEngine should provide same basic functionality as SimpleUndoEngine`() = runTest {
        // Given
        val simpleEngine = SimpleUndoEngine()
        val enhancedEngine = delegatingEngine()

        val operations = listOf(
            createOperation(workflowId = "wf1", index = 0) { true },
            createOperation(workflowId = "wf1", index = 1) { false },
            createOperation(workflowId = "wf1", index = 2) { true }
        )

        // When
        val simpleResult = simpleEngine.undoWorkflow("wf1", operations)
        val enhancedResult = enhancedEngine.undoWorkflow("wf1", operations)

        // Then - Both should have same basic results
        assertEquals(simpleResult.totalOperations, enhancedResult.totalOperations)
        assertEquals(simpleResult.succeededCount, enhancedResult.succeededCount)
        assertEquals(simpleResult.failedCount, enhancedResult.failedCount)
    }

    // ========== HELPER METHODS ==========

    private fun delegatingEngine(
        retryPolicy: RetryPolicy = RetryPolicy.aggressive()
    ): EnhancedUndoEngine {
        val registry = UndoStrategyRegistry()
            .register(object : UndoStrategy {
                override fun canHandle(operationType: String, resourceType: String): Boolean = true
                override suspend fun undo(operation: TransactionOperation): Boolean = operation.undo()
            })
        return EnhancedUndoEngine(strategyRegistry = registry, retryPolicy = retryPolicy)
    }

    private fun createOperation(
        workflowId: String,
        index: Int,
        type: String = "INSERT",
        resource: String = "User",
        resourceId: String? = "resource-$index",
        undoAction: suspend () -> Boolean
    ): TransactionOperation {
        return SimpleTransactionOperation(
            workflowId = workflowId,
            operationIndex = index,
            operationType = type,
            resourceType = resource,
            resourceId = resourceId,
            undoData = mapOf("id" to resourceId),
            undoAction = undoAction
        )
    }
}
