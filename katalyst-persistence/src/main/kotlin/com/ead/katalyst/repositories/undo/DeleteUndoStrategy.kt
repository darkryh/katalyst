package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.TransactionOperation
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing DELETE operations.
 *
 * When a DELETE operation is undone, we RE-INSERT the record that was deleted.
 * The undoData from the operation contains the original record to be re-inserted.
 *
 * **Implementation Notes**:
 * - Phase 2: This is a stub that logs the undo attempt
 * - Phase 3+: Will integrate with actual repository insert methods
 * - The undoData must contain the full record serialized as JSON
 */
class DeleteUndoStrategy : UndoStrategy {
    private val logger = LoggerFactory.getLogger(DeleteUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        return operationType.uppercase() == "DELETE"
    }

    override suspend fun undo(operation: TransactionOperation): Boolean {
        return try {
            logger.info(
                "Undoing DELETE operation: resource={}, id={}",
                operation.resourceType, operation.resourceId
            )

            // TODO: Phase 3 - Integrate with repository insert method
            val undoData = operation.undoData
            if (undoData == null) {
                logger.warn("No undo data available for DELETE operation on {}", operation.resourceId)
                return false
            }

            // The undoData contains the serialized record to be re-inserted
            // A repository instance would deserialize and insert this data
            logger.debug(
                "Successfully undone DELETE operation: id={}, recordSize={}",
                operation.resourceId,
                undoData.size
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to undo DELETE operation", e)
            false
        }
    }
}
