package com.ead.katalyst.transactions.workflow

/**
 * Persistent log of all operations in a workflow.
 *
 * This interface abstracts the storage of operations, allowing:
 * - Async writes (non-blocking transaction execution)
 * - Retrieval of operations for undo
 * - Status tracking (pending, committed, undone, failed)
 * - Audit trail of all workflow operations
 *
 * **Async Behavior**:
 * All write operations should be non-blocking. The caller should not wait
 * for the write to complete before continuing with the transaction.
 */
interface OperationLog {

    /**
     * Log a new operation (async write).
     *
     * Called when an operation is performed during a transaction.
     * Should be non-blocking - returns immediately.
     *
     * @param workflowId ID of the workflow
     * @param operationIndex Sequential index in the workflow
     * @param operation The operation details
     */
    suspend fun logOperation(
        workflowId: String,
        operationIndex: Int,
        operation: TransactionOperation
    )

    /**
     * Get all operations for a workflow that are not yet committed.
     *
     * Used during transaction failure to undo operations.
     *
     * @param workflowId Workflow ID
     * @return List of operations in order (oldest first)
     */
    suspend fun getPendingOperations(workflowId: String): List<TransactionOperation>

    /**
     * Get all operations for a workflow.
     *
     * @param workflowId Workflow ID
     * @return All operations in order
     */
    suspend fun getAllOperations(workflowId: String): List<TransactionOperation>

    /**
     * Mark an operation as committed (successful and permanent).
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     */
    suspend fun markAsCommitted(workflowId: String, operationIndex: Int)

    /**
     * Mark all operations for a workflow as committed.
     *
     * Called when transaction successfully completes.
     *
     * @param workflowId Workflow ID
     */
    suspend fun markAllAsCommitted(workflowId: String)

    /**
     * Mark an operation as undone.
     *
     * Called when an operation has been successfully undone.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     */
    suspend fun markAsUndone(workflowId: String, operationIndex: Int)

    /**
     * Mark an operation as failed to undo.
     *
     * Called when undo operation fails and needs manual intervention.
     *
     * @param workflowId Workflow ID
     * @param operationIndex Operation index
     * @param error Error message
     */
    suspend fun markAsFailed(workflowId: String, operationIndex: Int, error: String)

    /**
     * Get all operations for a workflow that failed to undo.
     *
     * Used by recovery job to identify workflows needing manual intervention.
     *
     * @return List of operations with failed undo status
     */
    suspend fun getFailedOperations(): List<TransactionOperation>

    /**
     * Delete old operation logs (for cleanup/archival).
     *
     * Called periodically to remove old logs beyond retention period.
     *
     * @param beforeTimestamp Delete logs created before this timestamp (millis)
     * @return Number of logs deleted
     */
    suspend fun deleteOldOperations(beforeTimestamp: Long): Int
}
