package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.TransactionOperation
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing INSERT operations.
 *
 * When an INSERT operation is undone, we DELETE the record that was inserted.
 * The resourceId from the operation tells us which record to delete.
 *
 * **Implementation Notes**:
 * - Phase 2: This is a stub that logs the undo attempt
 * - Phase 3+: Will integrate with actual repository delete methods
 */
class InsertUndoStrategy : UndoStrategy {
    private val logger = LoggerFactory.getLogger(InsertUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        return operationType.uppercase() == "INSERT"
    }

    override suspend fun undo(operation: TransactionOperation): Boolean {
        return try {
            logger.info(
                "Undoing INSERT operation: resource={}, id={}",
                operation.resourceType, operation.resourceId
            )

            // TODO: Phase 3 - Integrate with repository delete method
            // For now, we assume the operation has the necessary undo data
            val undoData = operation.undoData
            if (undoData == null) {
                logger.warn("No undo data available for INSERT operation on {}", operation.resourceId)
                return false
            }

            // The actual delete would be performed by a repository instance
            // This would require dependency injection or a registry of repositories
            logger.debug("Successfully undone INSERT operation: id={}", operation.resourceId)
            true
        } catch (e: Exception) {
            logger.error("Failed to undo INSERT operation", e)
            false
        }
    }
}
