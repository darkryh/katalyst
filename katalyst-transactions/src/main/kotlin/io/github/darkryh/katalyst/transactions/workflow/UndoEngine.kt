package io.github.darkryh.katalyst.transactions.workflow

/**
 * Executes undo operations in reverse order to rollback a workflow.
 *
 * Responsibilities:
 * - Execute undo operations in reverse order (LIFO)
 * - Handle failures gracefully (continue with other undos)
 * - Track success/failure of each undo
 * - Log detailed error information for failed undos
 * - Return summary of undo results
 *
 * **Key Principle**: Undo operations are best-effort. If one undo fails,
 * continue with others to maximize chance of recovering to consistent state.
 */
interface UndoEngine {

    /**
     * Undo all operations for a workflow in reverse order.
     *
     * Executes operations in reverse order (LIFO) so that:
     * 1. Most recent operation is undone first
     * 2. Dependencies are respected
     * 3. System returns to consistent state
     *
     * @param workflowId Workflow ID
     * @param operations All operations for this workflow
     * @return Result containing success/failure counts and details
     */
    suspend fun undoWorkflow(
        workflowId: String,
        operations: List<TransactionOperation>
    ): UndoResult

    /**
     * Result of undo operation(s)
     */
    data class UndoResult(
        val workflowId: String,
        val totalOperations: Int,
        val succeededCount: Int,
        val failedCount: Int,
        val results: List<UndoOperationResult>
    ) {
        val isFullySuccessful: Boolean get() = failedCount == 0

        val summary: String get() {
            return "Undo complete: $succeededCount succeeded, $failedCount failed out of $totalOperations"
        }
    }

    /**
     * Result of a single undo operation
     */
    data class UndoOperationResult(
        val operationIndex: Int,
        val operationType: String,
        val resourceType: String,
        val succeeded: Boolean,
        val error: String? = null
    )
}
