package com.ead.katalyst.repositories

import com.ead.katalyst.transactions.workflow.CurrentWorkflowContext
import com.ead.katalyst.transactions.workflow.OperationLog
import org.slf4j.LoggerFactory

/**
 * Base repository class that automatically tracks all operations for workflow purposes.
 *
 * **Auto-Tracking Behavior**:
 * When CurrentWorkflowContext is set (within a transactionManager. Transaction call),
 * all repository operations are automatically logged for:
 * - Audit trail
 * - Automatic undo on failure
 * - Recovery and replay
 *
 * **Usage**:
 * ```
 * class UserRepository(operationLog: OperationLog) : TrackedRepository<Long, User>(operationLog) {
 *     override suspend fun save(entity: User): User = tracked(
 *         operation = "INSERT",
 *         resourceType = "User",
 *         resourceId = entity.id,
 *         action = { super.save(entity) }
 *     )
 * }
 * ```
 *
 * **Non-Blocking**:
 * Operation logging is async and non-blocking. Repositories don't wait for
 * the log write to complete before returning to the caller.
 *
 * @param Id Entity ID type (must be Comparable)
 * @param T Entity type (must implement Identifiable<ID>)
 * @param operationLog The operation log for tracking
 */
abstract class TrackedRepository<Id : Comparable<Id>, T : Identifiable<Id>>(
    protected val operationLog: OperationLog
) : Repository<Id, T> {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Execute an action and track it in the operation log.
     *
     * Automatically logs the operation to the current workflow (if active).
     * Non-blocking: Returns immediately, logging happens async.
     *
     * @param operation Operation type (INSERT, UPDATE, DELETE, etc.)
     * @param resourceType Type of resource (User, Order, etc.)
     * @param resourceId ID of the resource (optional)
     * @param action The actual operation to perform
     * @return Result of the action
     */
    protected suspend fun <R> tracked(
        operation: String,
        resourceType: String,
        resourceId: Id? = null,
        action: suspend () -> R
    ): R {
        // Execute the actual operation
        val result = action()

        // Get current workflow context (thread-local)
        val workflowId = CurrentWorkflowContext.get()
        if (workflowId != null) {
            // Async log operation (fire and forget)
            logOperationAsync(
                workflowId = workflowId,
                operationType = operation,
                resourceType = resourceType,
                resourceId = resourceId?.toString()
            )
        }

        return result
    }

    /**
     * Asynchronously log an operation to the operation log.
     *
     * Non-blocking: Returns immediately, logging happens in background.
     * Errors are logged but don't affect the transaction.
     */
    private suspend fun logOperationAsync(
        workflowId: String,
        operationType: String,
        resourceType: String,
        resourceId: String?
    ) {
        try {
            // TODO: Implement async logging (will be done in Phase 2.4)
            // For now, this is a placeholder
            logger.debug(
                "Would log operation: workflow={}, type={}, resource={}, id={}",
                workflowId, operationType, resourceType, resourceId
            )
        } catch (e: Exception) {
            // Log but don't throw - operation already succeeded
            logger.error(
                "Failed to log operation for workflow: {} ({}: {})",
                workflowId, resourceType, resourceId, e
            )
        }
    }

    /**
     * Helper: Get the resource type name from the entity class.
     *
     * @return Simple class name (e.g., "User", "Order")
     */
    protected fun getResourceTypeName(): String {
        return this::class.simpleName?.replace("Repository", "") ?: "Unknown"
    }
}
