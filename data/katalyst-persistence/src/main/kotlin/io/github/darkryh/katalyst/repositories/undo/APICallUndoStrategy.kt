package io.github.darkryh.katalyst.repositories.undo

import io.github.darkryh.katalyst.transactions.workflow.TransactionOperation
import org.slf4j.LoggerFactory

/**
 * Strategy for undoing external API calls and notifications.
 *
 * When an external API call or notification is undone, we typically call
 * a corresponding "undo" or "delete" endpoint on the external service.
 * Examples:
 * - Email sent → Call email service to mark as removed/deleted
 * - Payment created → Call payment service to cancel/refund
 * - Third-party record created → Call DELETE endpoint
 *
 * **Status: NOT IMPLEMENTED — fails closed.**
 *
 * No HTTP transport is wired into this strategy: it cannot call `undo_endpoint`, so it
 * cannot reverse anything. [undo] therefore always returns `false` and logs at ERROR.
 *
 * That is deliberate. The previous implementation returned `true` whenever `undoData` was
 * non-null, which made [EnhancedUndoEngine] count the operation as succeeded and let
 * `WorkflowStateRepository.markAsUndone` record the workflow as UNDONE — while the charge
 * was never refunded, the email was never retracted and the remote record still existed.
 * A rollback that silently loses external compensations is far worse than one that reports
 * `FAILED_UNDO` and demands reconciliation, so this strategy reports the truth.
 *
 * It stays registered in [UndoStrategyRegistry.createDefault] rather than refusing
 * registration at startup for two reasons:
 * - Refusing to register only swaps this strategy for the registry's NoOp fallback, which
 *   also fails closed but logs a generic "no strategy found" message. Keeping it registered
 *   preserves the specific, actionable diagnostic below (operation type, resource, id).
 * - Failing at startup would break every application that merely *declares* API_CALL
 *   operations without ever rolling one back, which is the common case.
 *
 * **To implement**: give the strategy an HTTP client, read `undo_endpoint`, `method`,
 * `remote_resource_id` and `headers` out of [TransactionOperation.undoData], issue the call
 * (idempotency key included), and return `true` only when the remote service confirms the
 * compensation. Retries are already handled by [RetryPolicy] in [EnhancedUndoEngine].
 */
internal class APICallUndoStrategy : UndoStrategy {
    private val logger = LoggerFactory.getLogger(APICallUndoStrategy::class.java)

    override fun canHandle(operationType: String, resourceType: String): Boolean {
        val op = operationType.uppercase()
        return op == "API_CALL" || op == "EXTERNAL_CALL" || op == "NOTIFICATION"
    }

    /**
     * Always reports failure: there is no transport to perform the compensating call.
     *
     * @return `false`, always.
     */
    override suspend fun undo(operation: TransactionOperation): Boolean {
        logger.error(
            "Cannot undo {} operation on {} (id={}): APICallUndoStrategy has no HTTP transport, " +
                "so the external side effect has NOT been reversed. Reporting failure so the " +
                "workflow is marked FAILED_UNDO and reconciled manually rather than recorded as " +
                "UNDONE. undo_endpoint present in undoData={}",
            operation.operationType,
            operation.resourceType,
            operation.resourceId,
            operation.undoData?.containsKey("undo_endpoint") == true
        )
        return false
    }
}
