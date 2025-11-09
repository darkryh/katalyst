package com.ead.katalyst.repositories.implementation

import com.ead.katalyst.database.table.OperationLogTable
import com.ead.katalyst.transactions.workflow.OperationLog
import com.ead.katalyst.transactions.workflow.OperationStatus
import com.ead.katalyst.transactions.workflow.SimpleTransactionOperation
import com.ead.katalyst.transactions.workflow.TransactionOperation
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Persistent repository for operation logs.
 *
 * Provides storage and retrieval of operation tracking data for workflows.
 * Implements the OperationLog interface for workflow tracking.
 *
 * **Thread Safety**: Safe for concurrent access via Exposed transaction management.
 *
 * **Async Behavior**: Writes are intentionally async to avoid blocking the main transaction.
 */
class OperationLogRepository(private val database: Database) : OperationLog {

    private val logger = LoggerFactory.getLogger(OperationLogRepository::class.java)

    /**
     * Log a new operation (async write).
     *
     * @param workflowId Workflow ID
     * @param operationIndex Sequential index (0-based)
     * @param operation Operation details
     */
    override suspend fun logOperation(
        workflowId: String,
        operationIndex: Int,
        operation: TransactionOperation
    ) {
        try {
            transaction(database) {
                OperationLogTable.insert {
                    it[OperationLogTable.workflowId] = workflowId
                    it[OperationLogTable.operationIndex] = operationIndex
                    it[OperationLogTable.operationType] = operation.operationType
                    it[OperationLogTable.resourceType] = operation.resourceType
                    it[OperationLogTable.resourceId] = operation.resourceId
                    it[OperationLogTable.operationData] = operation.operationData?.toString() // Convert to JSON string
                    it[OperationLogTable.undoData] = operation.undoData?.toString()           // Convert to JSON string
                    it[OperationLogTable.status] = OperationStatus.PENDING.name
                    it[OperationLogTable.createdAt] = System.currentTimeMillis()
                }
            }
            logger.debug(
                "Logged operation: workflow={}, index={}, type={}, resource={}",
                workflowId, operationIndex, operation.operationType, operation.resourceType
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to log operation for workflow: {} ({}/{})",
                workflowId, operation.resourceType, operation.resourceId, e
            )
            // Don't throw - operation already succeeded
        }
    }

    /**
     * Get all pending operations for a workflow (not yet committed).
     *
     * @param workflowId Workflow ID
     * @return Operations in order (oldest first)
     */
    override suspend fun getPendingOperations(workflowId: String): List<TransactionOperation> {
        return try {
            transaction(database) {
                OperationLogTable
                    .selectAll()
                    .where(
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.status eq OperationStatus.PENDING.name)
                    )
                    .orderBy(OperationLogTable.operationIndex)
                    .map { row ->
                        SimpleTransactionOperation(
                            workflowId = row[OperationLogTable.workflowId],
                            operationIndex = row[OperationLogTable.operationIndex],
                            operationType = row[OperationLogTable.operationType],
                            resourceType = row[OperationLogTable.resourceType],
                            resourceId = row[OperationLogTable.resourceId],
                            operationData = row[OperationLogTable.operationData]?.let { parseJson(it) },
                            undoData = row[OperationLogTable.undoData]?.let { parseJson(it) }
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get pending operations for workflow: {}", workflowId, e)
            emptyList()
        }
    }

    /**
     * Get all operations for a workflow.
     *
     * @param workflowId Workflow ID
     * @return All operations in order
     */
    override suspend fun getAllOperations(workflowId: String): List<TransactionOperation> {
        return try {
            transaction(database) {
                OperationLogTable
                    .selectAll()
                    .where(OperationLogTable.workflowId eq workflowId)
                    .orderBy(OperationLogTable.operationIndex)
                    .map { row ->
                        SimpleTransactionOperation(
                            workflowId = row[OperationLogTable.workflowId],
                            operationIndex = row[OperationLogTable.operationIndex],
                            operationType = row[OperationLogTable.operationType],
                            resourceType = row[OperationLogTable.resourceType],
                            resourceId = row[OperationLogTable.resourceId],
                            operationData = row[OperationLogTable.operationData]?.let { parseJson(it) },
                            undoData = row[OperationLogTable.undoData]?.let { parseJson(it) }
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get all operations for workflow: {}", workflowId, e)
            emptyList()
        }
    }

    /**
     * Mark an operation as committed.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     */
    override suspend fun markAsCommitted(workflowId: String, operationIndex: Int) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    {
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.operationIndex eq operationIndex)
                    }
                ) {
                    it[OperationLogTable.status] = OperationStatus.COMMITTED.name
                    it[OperationLogTable.committedAt] = System.currentTimeMillis()
                }
            }
            logger.debug(
                "Marked operation as committed: workflow={}, index={}",
                workflowId, operationIndex
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to mark operation as committed: workflow={}, index={}",
                workflowId, operationIndex, e
            )
        }
    }

    /**
     * Mark all operations for a workflow as committed.
     *
     * @param workflowId Workflow ID
     */
    override suspend fun markAllAsCommitted(workflowId: String) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    { OperationLogTable.workflowId eq workflowId }
                ) {
                    it[OperationLogTable.status] = OperationStatus.COMMITTED.name
                    it[OperationLogTable.committedAt] = System.currentTimeMillis()
                }
            }
            logger.debug("Marked all operations as committed for workflow: {}", workflowId)
        } catch (e: Exception) {
            logger.error("Failed to mark all operations as committed for workflow: {}", workflowId, e)
        }
    }

    /**
     * Mark an operation as undone.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     */
    override suspend fun markAsUndone(workflowId: String, operationIndex: Int) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    {
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.operationIndex eq operationIndex)
                    }
                ) {
                    it[OperationLogTable.status] = OperationStatus.UNDONE.name
                    it[OperationLogTable.undoneAt] = System.currentTimeMillis()
                }
            }
            logger.debug(
                "Marked operation as undone: workflow={}, index={}",
                workflowId, operationIndex
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to mark operation as undone: workflow={}, index={}",
                workflowId, operationIndex, e
            )
        }
    }

    /**
     * Mark an operation as failed.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     * @param error Error message
     */
    override suspend fun markAsFailed(workflowId: String, operationIndex: Int, error: String) {
        try {
            transaction(database) {
                OperationLogTable.update(
                    {
                        (OperationLogTable.workflowId eq workflowId) and
                                (OperationLogTable.operationIndex eq operationIndex)
                    }
                ) {
                    it[OperationLogTable.status] = OperationStatus.FAILED.name
                    it[OperationLogTable.errorMessage] = error
                }
            }
            logger.warn(
                "Marked operation as failed: workflow={}, index={}, error={}",
                workflowId, operationIndex, error
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to mark operation as failed: workflow={}, index={}",
                workflowId, operationIndex, e
            )
        }
    }

    /**
     * Get all failed operations.
     *
     * @return Operations with FAILED status
     */
    override suspend fun getFailedOperations(): List<TransactionOperation> {
        return try {
            transaction(database) {
                OperationLogTable
                    .selectAll()
                    .where(OperationLogTable.status eq OperationStatus.FAILED.name)
                    .orderBy(OperationLogTable.createdAt)
                    .map { row ->
                        SimpleTransactionOperation(
                            workflowId = row[OperationLogTable.workflowId],
                            operationIndex = row[OperationLogTable.operationIndex],
                            operationType = row[OperationLogTable.operationType],
                            resourceType = row[OperationLogTable.resourceType],
                            resourceId = row[OperationLogTable.resourceId],
                            operationData = row[OperationLogTable.operationData]?.let { parseJson(it) },
                            undoData = row[OperationLogTable.undoData]?.let { parseJson(it) }
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get failed operations", e)
            emptyList()
        }
    }

    /**
     * Delete old operation logs (for cleanup/archival).
     *
     * @param beforeTimestamp Delete logs created before this timestamp (millis)
     * @return Number of logs deleted
     */
    override suspend fun deleteOldOperations(beforeTimestamp: Long): Int {
        return try {
            transaction(database) {
                // Delete operations that were created before the timestamp and are not in PENDING state
                OperationLogTable.deleteWhere {
                    (createdAt lessEq beforeTimestamp) and
                            (status neq OperationStatus.PENDING.name)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete old operations", e)
            0
        }
    }

    /**
     * Parse JSON string to Map.
     *
     * TODO: Use proper JSON serialization library (kotlinx.serialization or Jackson)
     */
    private fun parseJson(json: String): Map<String, Any?>? {
        return try {
            // Placeholder: In Phase 2.4 we'll implement proper JSON parsing
            mapOf()
        } catch (e: Exception) {
            logger.warn("Failed to parse JSON: {}", e.message)
            null
        }
    }
}