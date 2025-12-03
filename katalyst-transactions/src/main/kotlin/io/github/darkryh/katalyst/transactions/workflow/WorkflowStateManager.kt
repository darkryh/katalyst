package io.github.darkryh.katalyst.transactions.workflow

/**
 * Manages the lifecycle and state of workflows.
 *
 * Tracks:
 * - Workflow start/completion
 * - Success/failure status
 * - Which operation failed (for recovery)
 * - Error messages for debugging
 */
interface WorkflowStateManager {

    /**
     * Start a new workflow.
     *
     * Called at the beginning of a transaction.
     *
     * @param workflowId Unique workflow identifier
     * @param workflowName Human-readable name (e.g., "user_registration")
     */
    suspend fun startWorkflow(workflowId: String, workflowName: String)

    /**
     * Mark workflow as successfully completed.
     *
     * Called when all operations succeeded.
     *
     * @param workflowId Workflow ID
     */
    suspend fun commitWorkflow(workflowId: String)

    /**
     * Mark workflow as failed.
     *
     * Called when an operation fails and rollback is needed.
     *
     * @param workflowId Workflow ID
     * @param failedAtOperation Index of operation that failed
     * @param error Error message
     */
    suspend fun failWorkflow(
        workflowId: String,
        failedAtOperation: Int,
        error: String
    )

    /**
     * Mark workflow as undone.
     *
     * Called when all operations have been successfully undone.
     *
     * @param workflowId Workflow ID
     */
    suspend fun markAsUndone(workflowId: String)

    /**
     * Get current state of a workflow.
     *
     * @param workflowId Workflow ID
     * @return Workflow state, or null if not found
     */
    suspend fun getWorkflowState(workflowId: String): WorkflowState?

    /**
     * Get all failed workflows that need recovery.
     *
     * Used by recovery job to identify workflows needing undo.
     *
     * @return List of failed workflows
     */
    suspend fun getFailedWorkflows(): List<WorkflowState>

    /**
     * Delete old workflow state records (for cleanup/archival).
     *
     * @param beforeTimestamp Delete records created before this timestamp (millis)
     * @return Number of records deleted
     */
    suspend fun deleteOldWorkflows(beforeTimestamp: Long): Int
}

/**
 * Current state of a workflow
 */
data class WorkflowState(
    val workflowId: String,
    val workflowName: String,
    val status: WorkflowStatus,
    val totalOperations: Int = 0,
    val failedAtOperation: Int? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null
)

/**
 * Status of a workflow
 */
enum class WorkflowStatus {
    STARTED,      // Workflow started
    COMMITTED,    // All operations completed successfully
    FAILED,       // An operation failed, rollback needed
    UNDONE,       // All operations successfully undone
    FAILED_UNDO   // Undo failed, manual intervention needed
}
