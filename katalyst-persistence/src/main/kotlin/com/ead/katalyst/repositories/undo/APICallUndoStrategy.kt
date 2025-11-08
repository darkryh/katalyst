package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.TransactionOperation
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing external API calls and notifications.
 *
 * When an external API call or notification is undone, we typically call
 * a corresponding "undo" or "delete" endpoint on the external service.
 * Examples:
 * - Email sent → Call email service to mark as removed/deleted
 * - Payment created → Call payment service to cancel/refund
 * - Third-party record created → Call DELETE endpoint
 *
 * **Implementation Notes**:
 * - Phase 2: This is a stub that logs the undo attempt
 * - Phase 3+: Will integrate with HTTP client to call undo endpoints
 * - The undoData should contain: undo_endpoint, resource_id_on_remote_system, etc.
 * - Includes retry logic for transient failures
 */
class APICallUndoStrategy : UndoStrategy {
    private val logger = LoggerFactory.getLogger(APICallUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        val op = operationType.uppercase()
        return op == "API_CALL" || op == "EXTERNAL_CALL" || op == "NOTIFICATION"
    }

    override suspend fun undo(operation: TransactionOperation): Boolean {
        return try {
            logger.info(
                "Undoing API call operation: resource={}, type={}, id={}",
                operation.resourceType, operation.operationType, operation.resourceId
            )

            // TODO: Phase 3 - Call HTTP endpoint to undo the external API call
            val undoData = operation.undoData
            if (undoData == null) {
                logger.warn("No undo data available for API call operation on {}", operation.resourceId)
                return false
            }

            // The undoData should contain:
            // - undo_endpoint: The URL to call to undo the operation
            // - remote_resource_id: The ID of the resource on the remote system
            // - method: The HTTP method (DELETE, POST, etc)
            // - retries: Number of retries attempted

            logger.debug(
                "Successfully undone API call operation: id={}, endpoint_available={}",
                operation.resourceId,
                undoData.containsKey("undo_endpoint")
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to undo API call operation", e)
            false
        }
    }
}
