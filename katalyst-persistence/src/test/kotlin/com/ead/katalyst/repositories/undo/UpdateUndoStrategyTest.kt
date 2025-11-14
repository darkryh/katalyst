package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.TransactionOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for UpdateUndoStrategy.
 *
 * Tests cover:
 * - canHandle() operation type matching
 * - undo() with valid undoData (original values)
 * - undo() with missing undoData
 * - Exception handling
 * - Case insensitivity
 */
class UpdateUndoStrategyTest {

    private val strategy = UpdateUndoStrategy()

    // ========== canHandle() TESTS ==========

    @Test
    fun `canHandle should return true for UPDATE operation`() {
        // When
        val result = strategy.canHandle("UPDATE", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for lowercase update`() {
        // When
        val result = strategy.canHandle("update", "User")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for mixed case UpDaTe`() {
        // When
        val result = strategy.canHandle("UpDaTe", "User")

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
        val result1 = strategy.canHandle("UPDATE", "User")
        val result2 = strategy.canHandle("UPDATE", "Order")
        val result3 = strategy.canHandle("UPDATE", "Payment")

        // Then
        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)
    }

    // ========== undo() TESTS ==========

    @Test
    fun `undo should return true when undoData contains original values`() = runTest {
        // Given - undoData contains the original values before update
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "User",
            resourceId = "123",
            undoData = mapOf(
                "name" to "John Doe",  // Original name
                "email" to "john.old@example.com"  // Original email
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
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "User",
            resourceId = "123",
            undoData = null,
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertFalse(result)
    }

    @Test
    fun `undo should return true when undoData is empty map`() = runTest {
        // Given
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "User",
            resourceId = "123",
            undoData = emptyMap(),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        // Empty map is still considered "present"
        assertTrue(result)
    }

    @Test
    fun `undo should handle single field update`() = runTest {
        // Given - Only one field was updated
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "User",
            resourceId = "123",
            undoData = mapOf("status" to "ACTIVE"),  // Original status
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle multiple field updates`() = runTest {
        // Given - Multiple fields were updated
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "Order",
            resourceId = "456",
            undoData = mapOf(
                "status" to "PENDING",  // Original status
                "total" to 99.99,  // Original total
                "lastUpdated" to "2025-01-01T10:00:00Z",  // Original timestamp
                "notes" to "Initial notes"  // Original notes
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle nested object updates`() = runTest {
        // Given - Nested fields were updated
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "Profile",
            resourceId = "789",
            undoData = mapOf(
                "preferences" to mapOf(
                    "theme" to "light",  // Original theme
                    "language" to "en"  // Original language
                ),
                "privacy" to mapOf(
                    "profileVisible" to false  // Original privacy setting
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
    fun `undo should handle update with null original value`() = runTest {
        // Given - Field was null before update
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "User",
            resourceId = "123",
            undoData = mapOf(
                "middleName" to null,  // Was null before
                "phoneNumber" to null  // Was null before
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
        val operation = TransactionOperation(
            operationType = "UPDATE",
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
    fun `undo should handle multiple updates sequentially`() = runTest {
        // Given
        val operations = listOf(
            TransactionOperation("UPDATE", "User", "1", mapOf("status" to "ACTIVE"), null),
            TransactionOperation("UPDATE", "User", "2", mapOf("status" to "PENDING"), null),
            TransactionOperation("UPDATE", "User", "3", mapOf("status" to "INACTIVE"), null)
        )

        // When
        val results = operations.map { strategy.undo(it) }

        // Then
        assertTrue(results.all { it })
    }

    @Test
    fun `undo should handle updates with different data types`() = runTest {
        // Given
        val operation = TransactionOperation(
            operationType = "UPDATE",
            resourceType = "Product",
            resourceId = "999",
            undoData = mapOf(
                "name" to "Original Product",  // String
                "price" to 49.99,  // Double
                "quantity" to 100,  // Int
                "available" to true,  // Boolean
                "tags" to listOf("tag1", "tag2"),  // List
                "metadata" to mapOf("key" to "value")  // Map
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }
}
