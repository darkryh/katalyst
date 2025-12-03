package io.github.darkryh.katalyst.transactions.workflow

/**
 * Represents a single operation in a workflow that can be tracked and undone.
 *
 * Each operation tracks:
 * - What was done (INSERT, UPDATE, DELETE, API_CALL, etc)
 * - Which resource was affected (User, Order, Payment, etc)
 * - Data needed to undo the operation
 *
 * Operations are executed in order and undone in reverse order (LIFO).
 */
interface TransactionOperation {
    /**
     * Workflow ID this operation belongs to
     */
    val workflowId: String

    /**
     * Sequential index in the workflow (0-based)
     */
    val operationIndex: Int

    /**
     * Type of operation: INSERT, UPDATE, DELETE, API_CALL, EMAIL, etc
     */
    val operationType: String

    /**
     * Type of resource affected: User, Order, Payment, etc
     */
    val resourceType: String

    /**
     * ID of the affected resource (optional)
     */
    val resourceId: String?

    /**
     * Original data before the operation (for restore)
     */
    val operationData: Map<String, Any?>?

    /**
     * Data needed to undo the operation
     */
    val undoData: Map<String, Any?>?

    /**
     * Execute the undo operation.
     * Should be idempotent - safe to call multiple times.
     *
     * @return true if undo succeeded, false if failed
     */
    suspend fun undo(): Boolean
}

/**
 * Simple implementation of TransactionOperation
 */
data class SimpleTransactionOperation(
    override val workflowId: String,
    override val operationIndex: Int,
    override val operationType: String,
    override val resourceType: String,
    override val resourceId: String? = null,
    override val operationData: Map<String, Any?>? = null,
    override val undoData: Map<String, Any?>? = null,
    private val undoAction: suspend () -> Boolean = { true }
) : TransactionOperation {

    override suspend fun undo(): Boolean {
        return undoAction()
    }
}

/**
 * Status of an operation in the operation log
 */
enum class OperationStatus {
    PENDING,      // Logged but not yet committed
    COMMITTED,    // Committed and confirmed
    UNDONE,       // Successfully undone
    FAILED,       // Undo failed
    UNFINISHED    // Workflow failed before completion
}
