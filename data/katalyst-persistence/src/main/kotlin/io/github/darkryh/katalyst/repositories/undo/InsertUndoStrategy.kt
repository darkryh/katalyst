package io.github.darkryh.katalyst.repositories.undo

import io.github.darkryh.katalyst.transactions.workflow.TransactionOperation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing INSERT operations.
 *
 * When an INSERT operation is undone, we DELETE the record that was inserted.
 * The resourceId from the operation tells us which record to delete; resourceType
 * is used as the table name since no table registry is available at this layer.
 *
 * @param database Database to run the compensating statement against. When `null`,
 * the ambient/current Exposed transaction (or the process default database) is used.
 */
internal class InsertUndoStrategy(
    private val database: Database? = null
) : UndoStrategy {
    private val logger = LoggerFactory.getLogger(InsertUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        return operationType.uppercase() == "INSERT"
    }

    override suspend fun undo(operation: TransactionOperation): Boolean {
        val tableName = operation.resourceType
        val resourceId = operation.resourceId

        if (resourceId == null) {
            logger.warn(
                "Cannot undo INSERT operation on {}: no resourceId captured to identify the inserted row",
                tableName
            )
            return false
        }

        if (!CompensatingSql.isValidIdentifier(tableName)) {
            logger.warn(
                "Cannot undo INSERT operation: resourceType '{}' is not a valid table identifier",
                tableName
            )
            return false
        }

        return try {
            transaction(database) {
                val sql = CompensatingSql.deleteByIdStatement(tableName, resourceId, db.identifierManager::quoteIfNecessary)
                exec(sql)
            }
            logger.debug(
                "Successfully undone INSERT operation: table={}, id={}",
                tableName, resourceId
            )
            true
        } catch (e: Exception) {
            logger.error(
                "Failed to undo INSERT operation for table={}, id={}",
                tableName, resourceId, e
            )
            false
        }
    }
}
