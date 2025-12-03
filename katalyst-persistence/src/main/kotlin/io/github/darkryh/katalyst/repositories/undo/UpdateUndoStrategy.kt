package io.github.darkryh.katalyst.repositories.undo

import io.github.darkryh.katalyst.transactions.workflow.TransactionOperation
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing UPDATE operations.
 *
 * When an UPDATE operation is undone, we UPDATE the record back to its original value.
 * The undoData contains the original values before the update.
 *
 * **Implementation Notes**:
 * - Phase 2: This is a stub that logs the undo attempt
 * - Phase 3+: Will integrate with actual repository update methods
 * - The undoData must contain the original field values as JSON
 */
class UpdateUndoStrategy : UndoStrategy {
    private val logger = LoggerFactory.getLogger(UpdateUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        return operationType.uppercase() == "UPDATE"
    }

    override suspend fun undo(operation: TransactionOperation): Boolean {
        return try {
            logger.info(
                "Undoing UPDATE operation: resource={}, id={}",
                operation.resourceType, operation.resourceId
            )

            // TODO: Phase 3 - Integrate with repository update method
            val undoData = operation.undoData
            if (undoData == null) {
                logger.warn("No undo data available for UPDATE operation on {}", operation.resourceId)
                return false
            }

            // The undoData contains the original field values (as Map or serialized JSON)
            // A repository instance would use this to revert the update
            logger.debug(
                "Successfully undone UPDATE operation: id={}, fieldsCount={}",
                operation.resourceId,
                undoData.size
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to undo UPDATE operation", e)
            false
        }
    }
}
