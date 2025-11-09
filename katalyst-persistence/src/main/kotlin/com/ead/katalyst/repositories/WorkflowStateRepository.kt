package com.ead.katalyst.repositories

import com.ead.katalyst.database.tables.WorkflowStateTable
import com.ead.katalyst.transactions.workflow.WorkflowState
import com.ead.katalyst.transactions.workflow.WorkflowStateManager
import com.ead.katalyst.transactions.workflow.WorkflowStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Persistent repository for workflow state.
 *
 * Provides storage and lifecycle management for workflows.
 * Implements the WorkflowStateManager interface for workflow orchestration.
 *
 * **Thread Safety**: Safe for concurrent access via Exposed transaction management.
 *
 * **Workflow States**:
 * - STARTED: Workflow created and execution started
 * - COMMITTED: All operations completed successfully
 * - FAILED: An operation failed, rollback needed
 * - UNDONE: All operations successfully undone
 * - FAILED_UNDO: Undo operation failed, manual intervention needed
 */
class WorkflowStateRepository(private val database: Database) : WorkflowStateManager {

    private val logger = LoggerFactory.getLogger(WorkflowStateRepository::class.java)

    /**
     * Start a new workflow.
     *
     * @param workflowId Unique workflow identifier
     * @param workflowName Human-readable name
     */
    override suspend fun startWorkflow(workflowId: String, workflowName: String) {
        try {
            transaction(database) {
                WorkflowStateTable.insert {
                    it[WorkflowStateTable.workflowId] = workflowId
                    it[WorkflowStateTable.workflowName] = workflowName
                    it[status] = WorkflowStatus.STARTED.name
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            logger.debug("Started workflow: {} ({})", workflowId, workflowName)
        } catch (e: Exception) {
            logger.error("Failed to start workflow: {}", workflowId, e)
            // Don't throw - workflow can continue without state tracking
        }
    }

    /**
     * Mark workflow as successfully completed.
     *
     * @param workflowId Workflow ID
     */
    override suspend fun commitWorkflow(workflowId: String) {
        try {
            transaction(database) {
                WorkflowStateTable.update({ WorkflowStateTable.workflowId eq workflowId }) {
                    it[status] = WorkflowStatus.COMMITTED.name
                    it[completedAt] = System.currentTimeMillis()
                }
            }
            logger.info("Committed workflow: {}", workflowId)
        } catch (e: Exception) {
            logger.error("Failed to commit workflow: {}", workflowId, e)
        }
    }

    /**
     * Mark workflow as failed.
     *
     * @param workflowId Workflow ID
     * @param failedAtOperation Index of operation that failed
     * @param error Error message
     */
    override suspend fun failWorkflow(
        workflowId: String,
        failedAtOperation: Int,
        error: String
    ) {
        try {
            transaction(database) {
                WorkflowStateTable.update({ WorkflowStateTable.workflowId eq workflowId }) {
                    it[status] = WorkflowStatus.FAILED.name
                    it[WorkflowStateTable.failedAtOperation] = failedAtOperation
                    it[errorMessage] = error
                    it[completedAt] = System.currentTimeMillis()
                }
            }
            logger.warn(
                "Marked workflow as failed: {} at operation {}: {}",
                workflowId, failedAtOperation, error
            )
        } catch (e: Exception) {
            logger.error("Failed to mark workflow as failed: {}", workflowId, e)
        }
    }

    /**
     * Mark workflow as undone.
     *
     * @param workflowId Workflow ID
     */
    override suspend fun markAsUndone(workflowId: String) {
        try {
            transaction(database) {
                WorkflowStateTable.update({ WorkflowStateTable.workflowId eq workflowId }) {
                    it[status] = WorkflowStatus.UNDONE.name
                    it[completedAt] = System.currentTimeMillis()
                }
            }
            logger.info("Marked workflow as undone: {}", workflowId)
        } catch (e: Exception) {
            logger.error("Failed to mark workflow as undone: {}", workflowId, e)
        }
    }

    /**
     * Get current state of a workflow.
     *
     * @param workflowId Workflow ID
     * @return Workflow state, or null if not found
     */
    override suspend fun getWorkflowState(workflowId: String): WorkflowState? {
        return try {
            transaction(database) {
                WorkflowStateTable
                    .selectAll().where { WorkflowStateTable.workflowId eq workflowId }
                    .firstOrNull()
                    ?.let { row ->
                        WorkflowState(
                            workflowId = row[WorkflowStateTable.workflowId],
                            workflowName = row[WorkflowStateTable.workflowName],
                            status = WorkflowStatus.valueOf(row[WorkflowStateTable.status]),
                            totalOperations = row[WorkflowStateTable.totalOperations],
                            failedAtOperation = row[WorkflowStateTable.failedAtOperation],
                            errorMessage = row[WorkflowStateTable.errorMessage],
                            createdAt = row[WorkflowStateTable.createdAt],
                            completedAt = row[WorkflowStateTable.completedAt]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get workflow state: {}", workflowId, e)
            null
        }
    }

    /**
     * Get all failed workflows that need recovery.
     *
     * @return List of failed workflows
     */
    override suspend fun getFailedWorkflows(): List<WorkflowState> {
        return try {
            transaction(database) {
                WorkflowStateTable
                    .selectAll()
                    .where(
                        (WorkflowStateTable.status eq WorkflowStatus.FAILED.name) or
                        (WorkflowStateTable.status eq WorkflowStatus.FAILED_UNDO.name)
                    )
                    .orderBy(WorkflowStateTable.createdAt)
                    .map { row ->
                        WorkflowState(
                            workflowId = row[WorkflowStateTable.workflowId],
                            workflowName = row[WorkflowStateTable.workflowName],
                            status = WorkflowStatus.valueOf(row[WorkflowStateTable.status]),
                            totalOperations = row[WorkflowStateTable.totalOperations],
                            failedAtOperation = row[WorkflowStateTable.failedAtOperation],
                            errorMessage = row[WorkflowStateTable.errorMessage],
                            createdAt = row[WorkflowStateTable.createdAt],
                            completedAt = row[WorkflowStateTable.completedAt]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to get failed workflows", e)
            emptyList()
        }
    }

    /**
     * Delete old workflow state records (for cleanup/archival).
     *
     * @param beforeTimestamp Delete records created before this timestamp (millis)
     * @return Number of records deleted
     */
    override suspend fun deleteOldWorkflows(beforeTimestamp: Long): Int {
        return try {
            transaction(database) {
                WorkflowStateTable.deleteWhere {
                    (WorkflowStateTable.createdAt lessEq beforeTimestamp) and
                    (WorkflowStateTable.status eq WorkflowStatus.COMMITTED.name)  // Only delete completed workflows
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete old workflows", e)
            0
        }
    }
}
