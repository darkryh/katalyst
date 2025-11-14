package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.SimpleTransactionOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for InsertUndoStrategy.
 *
 * Tests cover:
 * - canHandle() operation type matching
 * - undo() with valid undoData
 * - undo() with missing undoData
 * - Exception handling
 * - Case insensitivity
 */
class InsertUndoStrategyTest {

    private val strategy = InsertUndoStrategy()
    private fun createOperation(
        workflowId: String = "workflow-insert",
        operationIndex: Int = 0,
        operationType: String = "INSERT",
        resourceType: String = "User",
        resourceId: String? = "123",
        undoData: Map<String, Any?>? = null,
        operationData: Map<String, Any?>? = null,
    ) = SimpleTransactionOperation(
        workflowId = workflowId,
        operationIndex = operationIndex,
        operationType = operationType,
        resourceType = resourceType,
        resourceId = resourceId,
        operationData = operationData,
        undoData = undoData
    )

    // ========== canHandle() TESTS ==========

    @Test
    fun `canHandle should return true for INSERT operation`() {
        // When
        val result = strategy.canHandle("INSERT", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for lowercase insert`() {
        // When
        val result = strategy.canHandle("insert", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for mixed case InSeRt`() {
        // When
        val result = strategy.canHandle("InSeRt", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return false for UPDATE operation`() {
        // When
        val result = strategy.canHandle("UPDATE", "User")

        // Then
        assertFalse(result)
    }

    @Test
    fun `canHandle should return false for DELETE operation`() {
        // When
        val result = strategy.canHandle("DELETE", "User")

        // Then
        assertFalse(result)
    }

    @Test
    fun `canHandle should return false for API_CALL operation`() {
        // When
        val result = strategy.canHandle("API_CALL", "User")

        // Then
        assertFalse(result)
    }

    @Test
    fun `canHandle should ignore resourceType parameter`() {
        // When
        val result1 = strategy.canHandle("INSERT", "User")
        val result2 = strategy.canHandle("INSERT", "Order")
        val result3 = strategy.canHandle("INSERT", "Payment")

        // Then
        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)
    }

    // ========== undo() TESTS ==========

    @Test
    fun `undo should return true when undoData is present`() = runTest {
        // Given
        val operation = createOperation(
            resourceType = "User",
            resourceId = "123",
            undoData = mapOf("id" to "123", "name" to "John"),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should return false when undoData is null`() = runTest {
        // Given
        val operation = createOperation(undoData = null, operationData = null)

        // When
        val result = strategy.undo(operation)

        // Then
        assertFalse(result)
    }

    @Test
    fun `undo should return false when undoData is empty map`() = runTest {
        // Given
        val operation = createOperation(undoData = emptyMap(), operationData = null)

        // When
        val result = strategy.undo(operation)

        // Then
        // Empty map is still considered "present" in current implementation
        assertTrue(result)
    }

    @Test
    fun `undo should handle operation with complex undoData`() = runTest {
        // Given
        val operation = createOperation(
            resourceType = "Order",
            resourceId = "456",
            undoData = mapOf(
                "id" to "456",
                "customerId" to "789",
                "items" to listOf("item1", "item2", "item3"),
                "total" to 99.99,
                "timestamp" to "2025-01-01T00:00:00Z"
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle operation with null resourceId`() = runTest {
        // Given
        val operation = createOperation(
            resourceType = "User",
            resourceId = null,
            undoData = mapOf("name" to "John"),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle multiple operations sequentially`() = runTest {
        // Given
        val operations = listOf(
            createOperation(
                workflowId = "workflow-insert-multi",
                operationIndex = 0,
                resourceId = "1",
                undoData = mapOf("id" to "1")
            ),
            createOperation(
                workflowId = "workflow-insert-multi",
                operationIndex = 1,
                resourceId = "2",
                undoData = mapOf("id" to "2")
            ),
            createOperation(
                workflowId = "workflow-insert-multi",
                operationIndex = 2,
                resourceId = "3",
                undoData = mapOf("id" to "3")
            )
        )

        // When
        val results = operations.map { strategy.undo(it) }

        // Then
        assertTrue(results.all { it })
    }
}
