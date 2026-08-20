package io.github.darkryh.katalyst.repositories.undo

import io.github.darkryh.katalyst.transactions.workflow.TransactionOperation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing UPDATE operations.
 *
 * When an UPDATE operation is undone, we UPDATE the record back to its original value.
 * The undoData contains the original field values (column name -> value) before the
 * update, and resourceId identifies which row to restore; resourceType is used as the
 * table name since no table registry is available at this layer.
 *
 * @param database Database to run the compensating statement against. When `null`,
 * the ambient/current Exposed transaction (or the process default database) is used.
 */
internal class UpdateUndoStrategy(
    private val database: Database? = null
) : UndoStrategy {
    private val logger = LoggerFactory.getLogger(UpdateUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        return operationType.uppercase() == "UPDATE"
    }

    override suspend fun undo(operation: TransactionOperation): Boolean {
        val tableName = operation.resourceType
        val resourceId = operation.resourceId
        val undoData = operation.undoData

        if (undoData.isNullOrEmpty()) {
            logger.warn(
                "Cannot undo UPDATE operation on {} (id={}): no captured original values to restore",
                tableName, resourceId
            )
            return false
        }

        if (resourceId == null) {
            logger.warn(
                "Cannot undo UPDATE operation on {}: no resourceId captured to identify the row to restore",
                tableName
            )
            return false
        }

        if (!CompensatingSql.isValidIdentifier(tableName)) {
            logger.warn(
                "Cannot undo UPDATE operation: resourceType '{}' is not a valid table identifier",
                tableName
            )
            return false
        }

        if (undoData.keys.any { !CompensatingSql.isValidIdentifier(it) }) {
            logger.warn(
                "Cannot undo UPDATE operation on {} (id={}): captured original values have an invalid column name",
                tableName, resourceId
            )
            return false
        }

        if (undoData.values.any { !CompensatingSql.isScalar(it) }) {
            logger.warn(
                "Cannot undo UPDATE operation on {} (id={}): captured original values contain non-scalar data",
                tableName, resourceId
            )
            return false
        }

        return try {
            transaction(database) {
                val sql = CompensatingSql.updateByIdStatement(tableName, resourceId, undoData, db.identifierManager::quoteIfNecessary)
                exec(sql)
            }
            logger.debug(
                "Successfully undone UPDATE operation: table={}, id={}, fieldsCount={}",
                tableName, resourceId, undoData.size
            )
            true
        } catch (e: Exception) {
            logger.error(
                "Failed to undo UPDATE operation for table={}, id={}",
                tableName, resourceId, e
            )
            false
        }
    }
}
