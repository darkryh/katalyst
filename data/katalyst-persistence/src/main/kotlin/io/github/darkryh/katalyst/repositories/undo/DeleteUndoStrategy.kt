package io.github.darkryh.katalyst.repositories.undo

import io.github.darkryh.katalyst.transactions.workflow.TransactionOperation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing DELETE operations.
 *
 * When a DELETE operation is undone, we RE-INSERT the record that was deleted.
 * The undoData from the operation must contain the full record (column name -> value)
 * to be re-inserted; resourceType is used as the table name since no table registry
 * is available at this layer.
 *
 * @param database Database to run the compensating statement against. When `null`,
 * the ambient/current Exposed transaction (or the process default database) is used.
 */
internal class DeleteUndoStrategy(
    private val database: Database? = null
) : UndoStrategy {
    private val logger = LoggerFactory.getLogger(DeleteUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        return operationType.uppercase() == "DELETE"
    }

    override suspend fun undo(operation: TransactionOperation): Boolean {
        val tableName = operation.resourceType
        val undoData = operation.undoData

        if (undoData.isNullOrEmpty()) {
            logger.warn(
                "Cannot undo DELETE operation on {} (id={}): no captured row data to re-insert",
                tableName, operation.resourceId
            )
            return false
        }

        if (!CompensatingSql.isValidIdentifier(tableName)) {
            logger.warn(
                "Cannot undo DELETE operation: resourceType '{}' is not a valid table identifier",
                tableName
            )
            return false
        }

        if (undoData.keys.any { !CompensatingSql.isValidIdentifier(it) }) {
            logger.warn(
                "Cannot undo DELETE operation on {} (id={}): captured row data has an invalid column name",
                tableName, operation.resourceId
            )
            return false
        }

        if (undoData.values.any { !CompensatingSql.isScalar(it) }) {
            logger.warn(
                "Cannot undo DELETE operation on {} (id={}): captured row data contains non-scalar values",
                tableName, operation.resourceId
            )
            return false
        }

        return try {
            transaction(database) {
                val sql = CompensatingSql.insertStatement(tableName, undoData, db.identifierManager::quoteIfNecessary)
                exec(sql)
            }
            logger.debug(
                "Successfully undone DELETE operation: table={}, id={}, columns={}",
                tableName, operation.resourceId, undoData.size
            )
            true
        } catch (e: Exception) {
            logger.error(
                "Failed to undo DELETE operation for table={}, id={}",
                tableName, operation.resourceId, e
            )
            false
        }
    }
}
