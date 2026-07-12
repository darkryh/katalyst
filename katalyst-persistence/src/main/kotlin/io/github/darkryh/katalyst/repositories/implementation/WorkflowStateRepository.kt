package io.github.darkryh.katalyst.repositories.implementation

import io.github.darkryh.katalyst.database.table.WorkflowStateTable
import io.github.darkryh.katalyst.transactions.workflow.WorkflowState
import io.github.darkryh.katalyst.transactions.workflow.WorkflowStateManager
import io.github.darkryh.katalyst.transactions.workflow.WorkflowStatus
import io.github.darkryh.katalyst.transactions.workflow.WorkflowPageCursor
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
 *
 * **Error Handling**: Every persistence failure is logged with its real cause and then
 * rethrown. A workflow-state write or read that silently swallowed its exception would
 * let callers (e.g. recovery jobs) mistake a failed lookup/update for "nothing to do" -
 * so failures are surfaced instead of hidden behind a default return value.
 */
internal class WorkflowStateRepository(private val database: Database) : WorkflowStateManager {

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
            throw e
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
            throw e
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
            throw e
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
            throw e
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
            throw e
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
            throw e
        }
    }

    override suspend fun getFailedWorkflowsPage(
        limit: Int,
        after: WorkflowPageCursor?,
    ): List<WorkflowState> {
        require(limit > 0) { "limit must be positive" }
        return try {
            transaction(database) {
                val failed = (WorkflowStateTable.status eq WorkflowStatus.FAILED.name) or
                    (WorkflowStateTable.status eq WorkflowStatus.FAILED_UNDO.name)
                val afterCursor = after?.let { cursor ->
                    (WorkflowStateTable.createdAt greater cursor.createdAt) or
                        ((WorkflowStateTable.createdAt eq cursor.createdAt) and
                            (WorkflowStateTable.workflowId greater cursor.workflowId))
                }
                WorkflowStateTable
                    .selectAll()
                    .where { if (afterCursor == null) failed else failed and afterCursor }
                    .orderBy(WorkflowStateTable.createdAt)
                    .orderBy(WorkflowStateTable.workflowId)
                    .limit(limit)
                    .map(::toWorkflowState)
            }
        } catch (e: Exception) {
            logger.error("Failed to get failed workflow page", e)
            throw e
        }
    }

    private fun toWorkflowState(row: ResultRow): WorkflowState = WorkflowState(
        workflowId = row[WorkflowStateTable.workflowId],
        workflowName = row[WorkflowStateTable.workflowName],
        status = WorkflowStatus.valueOf(row[WorkflowStateTable.status]),
        totalOperations = row[WorkflowStateTable.totalOperations],
        failedAtOperation = row[WorkflowStateTable.failedAtOperation],
        errorMessage = row[WorkflowStateTable.errorMessage],
        createdAt = row[WorkflowStateTable.createdAt],
        completedAt = row[WorkflowStateTable.completedAt],
    )

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
            throw e
        }
    }
}
