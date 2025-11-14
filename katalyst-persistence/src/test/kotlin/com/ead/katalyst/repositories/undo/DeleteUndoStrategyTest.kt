package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.SimpleTransactionOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for DeleteUndoStrategy.
 *
 * Tests cover:
 * - canHandle() operation type matching
 * - undo() with valid undoData (full record re-insertion)
 * - undo() with missing undoData
 * - Exception handling
 * - Case insensitivity
 */
class DeleteUndoStrategyTest {

    private val strategy = DeleteUndoStrategy()
    private fun createOperation(
        workflowId: String = "workflow-delete",
        operationIndex: Int = 0,
        operationType: String = "DELETE",
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
    fun `canHandle should return true for DELETE operation`() {
        // When
        val result = strategy.canHandle("DELETE", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for lowercase delete`() {
        // When
        val result = strategy.canHandle("delete", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for mixed case DeLeTe`() {
        // When
        val result = strategy.canHandle("DeLeTe", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return false for INSERT operation`() {
        // When
        val result = strategy.canHandle("INSERT", "User")

        // Then
        assertFalse(result)
    }

    @Test
    fun `canHandle should return false for UPDATE operation`() {
        // When
        val result = strategy.canHandle("UPDATE", "User")

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
        val result1 = strategy.canHandle("DELETE", "User")
        val result2 = strategy.canHandle("DELETE", "Order")
        val result3 = strategy.canHandle("DELETE", "Payment")

        // Then
        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)
    }

    // ========== undo() TESTS ==========

    @Test
    fun `undo should return true when undoData contains full record`() = runTest {
        // Given - undoData contains the deleted record to re-insert
        val operation = createOperation(
            resourceType = "User",
            resourceId = "123",
            undoData = mapOf(
                "id" to "123",
                "name" to "John Doe",
                "email" to "john@example.com",
                "age" to 30
            ),
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
    fun `undo should return true when undoData is empty map`() = runTest {
        // Given
        val operation = createOperation(undoData = emptyMap(), operationData = null)

        // When
        val result = strategy.undo(operation)

        // Then
        // Empty map is still considered "present"
        assertTrue(result)
    }

    @Test
    fun `undo should handle large record with many fields`() = runTest {
        // Given - Simulating a complex entity with many fields
        val operation = createOperation(
            resourceType = "Order",
            resourceId = "456",
            undoData = mapOf(
                "id" to "456",
                "customerId" to "789",
                "customerName" to "Jane Smith",
                "items" to listOf(
                    mapOf("id" to "item1", "name" to "Product A", "price" to 29.99),
                    mapOf("id" to "item2", "name" to "Product B", "price" to 49.99)
                ),
                "subtotal" to 79.98,
                "tax" to 7.20,
                "total" to 87.18,
                "status" to "COMPLETED",
                "createdAt" to "2025-01-01T10:00:00Z",
                "updatedAt" to "2025-01-01T12:00:00Z",
                "shippingAddress" to mapOf(
                    "street" to "123 Main St",
                    "city" to "Springfield",
                    "state" to "IL",
                    "zip" to "62701"
                )
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
    fun `undo should handle multiple deletions sequentially`() = runTest {
        // Given
        val operations = listOf(
            createOperation(
                workflowId = "workflow-delete-multi",
                operationIndex = 0,
                resourceId = "1",
                undoData = mapOf("id" to "1", "name" to "User1")
            ),
            createOperation(
                workflowId = "workflow-delete-multi",
                operationIndex = 1,
                resourceId = "2",
                undoData = mapOf("id" to "2", "name" to "User2")
            ),
            createOperation(
                workflowId = "workflow-delete-multi",
                operationIndex = 2,
                resourceId = "3",
                undoData = mapOf("id" to "3", "name" to "User3")
            )
        )

        // When
        val results = operations.map { strategy.undo(it) }

        // Then
        assertTrue(results.all { it })
    }

    @Test
    fun `undo should handle record with nested structures`() = runTest {
        // Given
        val operation = createOperation(
            resourceType = "Profile",
            resourceId = "789",
            undoData = mapOf(
                "id" to "789",
                "userId" to "123",
                "preferences" to mapOf(
                    "theme" to "dark",
                    "notifications" to mapOf(
                        "email" to true,
                        "sms" to false,
                        "push" to true
                    ),
                    "privacy" to mapOf(
                        "profileVisible" to true,
                        "showEmail" to false
                    )
                ),
                "metadata" to mapOf(
                    "createdFrom" to "web",
                    "lastLogin" to "2025-01-15T09:30:00Z"
                )
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }
}
