package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.SimpleTransactionOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for APICallUndoStrategy.
 *
 * Tests cover:
 * - canHandle() for API_CALL, EXTERNAL_CALL, NOTIFICATION
 * - undo() with valid undoData (undo endpoint info)
 * - undo() with missing undoData
 * - Exception handling
 * - Case insensitivity
 */
class APICallUndoStrategyTest {

    private val strategy = APICallUndoStrategy()
    private fun createOperation(
        workflowId: String = "workflow-api",
        operationIndex: Int = 0,
        operationType: String = "API_CALL",
        resourceType: String = "EmailService",
        resourceId: String? = "email-123",
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
    fun `canHandle should return true for API_CALL operation`() {
        // When
        val result = strategy.canHandle("API_CALL", "EmailService")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for EXTERNAL_CALL operation`() {
        // When
        val result = strategy.canHandle("EXTERNAL_CALL", "PaymentGateway")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for NOTIFICATION operation`() {
        // When
        val result = strategy.canHandle("NOTIFICATION", "SMSService")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for lowercase api_call`() {
        // When
        val result = strategy.canHandle("api_call", "Service")

        // Then
        assertTrue(result)
    }

    @Test
    fun `canHandle should return true for mixed case Api_Call`() {
        // When
        val result = strategy.canHandle("Api_Call", "Service")

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
    fun `canHandle should return false for DELETE operation`() {
        // When
        val result = strategy.canHandle("DELETE", "User")

        // Then
        assertFalse(result)
    }

    @Test
    fun `canHandle should ignore resourceType parameter`() {
        // When
        val result1 = strategy.canHandle("API_CALL", "EmailService")
        val result2 = strategy.canHandle("API_CALL", "SMSService")
        val result3 = strategy.canHandle("API_CALL", "PaymentGateway")

        // Then
        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)
    }

    // ========== undo() TESTS ==========

    @Test
    fun `undo should return true when undoData contains undo endpoint`() = runTest {
        // Given - undoData with undo endpoint information
        val operation = createOperation(
            operationType = "API_CALL",
            resourceType = "EmailService",
            resourceId = "email-123",
            undoData = mapOf(
                "undo_endpoint" to "https://api.example.com/emails/123/cancel",
                "remote_resource_id" to "email-123",
                "method" to "DELETE"
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
        val operation = createOperation(
            operationType = "API_CALL",
            resourceType = "EmailService",
            resourceId = "email-123",
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
        val operation = createOperation(
            operationType = "API_CALL",
            resourceType = "EmailService",
            resourceId = "email-123",
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
    fun `undo should handle EXTERNAL_CALL operation`() = runTest {
        // Given
        val operation = createOperation(
            operationType = "EXTERNAL_CALL",
            resourceType = "PaymentGateway",
            resourceId = "payment-456",
            undoData = mapOf(
                "undo_endpoint" to "https://payment.example.com/refund",
                "remote_resource_id" to "payment-456",
                "method" to "POST",
                "amount" to 99.99
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle NOTIFICATION operation`() = runTest {
        // Given
        val operation = createOperation(
            operationType = "NOTIFICATION",
            resourceType = "SMSService",
            resourceId = "sms-789",
            undoData = mapOf(
                "undo_endpoint" to "https://sms.example.com/messages/789/retract",
                "remote_resource_id" to "sms-789",
                "method" to "DELETE"
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle operation with retry information`() = runTest {
        // Given
        val operation = createOperation(
            operationType = "API_CALL",
            resourceType = "ThirdPartyAPI",
            resourceId = "api-999",
            undoData = mapOf(
                "undo_endpoint" to "https://api.third-party.com/undo",
                "remote_resource_id" to "api-999",
                "method" to "POST",
                "retries" to 3,
                "max_retries" to 5,
                "backoff_ms" to 1000
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle operation with authentication headers`() = runTest {
        // Given
        val operation = createOperation(
            operationType = "API_CALL",
            resourceType = "SecureAPI",
            resourceId = "secure-123",
            undoData = mapOf(
                "undo_endpoint" to "https://secure.example.com/undo",
                "remote_resource_id" to "secure-123",
                "method" to "DELETE",
                "headers" to mapOf(
                    "Authorization" to "Bearer token123",
                    "Content-Type" to "application/json"
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
            operationType = "API_CALL",
            resourceType = "EmailService",
            resourceId = null,
            undoData = mapOf("undo_endpoint" to "https://api.example.com/undo"),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle multiple API calls sequentially`() = runTest {
        // Given
        val operations = listOf(
            createOperation(
                workflowId = "workflow-api-seq",
                operationIndex = 0,
                resourceType = "EmailService",
                resourceId = "email-1",
                undoData = mapOf("undo_endpoint" to "url1")
            ),
            createOperation(
                workflowId = "workflow-api-seq",
                operationIndex = 1,
                resourceType = "SMSService",
                resourceId = "sms-2",
                undoData = mapOf("undo_endpoint" to "url2")
            ),
            createOperation(
                workflowId = "workflow-api-seq",
                operationIndex = 2,
                resourceType = "PaymentGateway",
                resourceId = "payment-3",
                undoData = mapOf("undo_endpoint" to "url3")
            )
        )

        // When
        val results = operations.map { strategy.undo(it) }

        // Then
        assertTrue(results.all { it })
    }

    @Test
    fun `undo should handle operation without undo_endpoint key`() = runTest {
        // Given - undoData exists but doesn't have undo_endpoint
        val operation = createOperation(
            operationType = "API_CALL",
            resourceType = "Service",
            resourceId = "123",
            undoData = mapOf(
                "remote_resource_id" to "123",
                "method" to "DELETE"
                // Missing "undo_endpoint"
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        // Current implementation checks containsKey, so this should still succeed
        assertTrue(result)
    }

    @Test
    fun `undo should handle email cancellation scenario`() = runTest {
        // Given - Real-world email cancellation
        val operation = createOperation(
            operationType = "NOTIFICATION",
            resourceType = "EmailService",
            resourceId = "email-welcome-123",
            undoData = mapOf(
                "undo_endpoint" to "https://mail.example.com/api/v1/emails/email-welcome-123/cancel",
                "remote_resource_id" to "email-welcome-123",
                "method" to "POST",
                "reason" to "Transaction rolled back",
                "notification_type" to "welcome_email",
                "recipient" to "user@example.com"
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }

    @Test
    fun `undo should handle payment refund scenario`() = runTest {
        // Given - Real-world payment refund
        val operation = createOperation(
            operationType = "EXTERNAL_CALL",
            resourceType = "PaymentGateway",
            resourceId = "charge-abc123",
            undoData = mapOf(
                "undo_endpoint" to "https://payment.gateway.com/api/v2/charges/charge-abc123/refund",
                "remote_resource_id" to "charge-abc123",
                "method" to "POST",
                "amount" to 149.99,
                "currency" to "USD",
                "reason" to "order_cancelled",
                "idempotency_key" to "refund-xyz789"
            ),
            operationData = null
        )

        // When
        val result = strategy.undo(operation)

        // Then
        assertTrue(result)
    }
}
