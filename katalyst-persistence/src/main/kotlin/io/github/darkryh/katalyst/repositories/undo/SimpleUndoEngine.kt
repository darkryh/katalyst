package io.github.darkryh.katalyst.repositories.undo

import io.github.darkryh.katalyst.transactions.workflow.TransactionOperation
import io.github.darkryh.katalyst.transactions.workflow.UndoEngine
import org.slf4j.LoggerFactory

/**
 * Simple implementation of the UndoEngine.
 *
 * Executes undo operations in reverse order (LIFO).
 * Best-effort approach: continues with other undos even if one fails.
 *
 * **Phase 2 Implementation**:
 * This is a basic stub that will be enhanced in Phase 3 with:
 * - Undo strategies for different operation types
 * - Retry logic with exponential backoff
 * - Better error handling and recovery
 *
 * **Current Behavior**:
 * - Executes operations in reverse order
 * - Catches and logs exceptions
 * - Returns summary of results
 */
class SimpleUndoEngine : UndoEngine {

    private val logger = LoggerFactory.getLogger(SimpleUndoEngine::class.java)

    /**
     * Undo all operations in reverse order (LIFO).
     *
     * @param workflowId Workflow ID
     * @param operations All operations for the workflow
     * @return Result with success/failure counts
     */
    override suspend fun undoWorkflow(
        workflowId: String,
        operations: List<TransactionOperation>
    ): UndoEngine.UndoResult {
        logger.info("Starting undo for workflow: {} with {} operations", workflowId, operations.size)

        val results = mutableListOf<UndoEngine.UndoOperationResult>()
        var succeededCount = 0
        var failedCount = 0

        // Execute in reverse order (LIFO)
        for (operation in operations.reversed()) {
            try {
                logger.debug(
                    "Undoing operation: index={}, type={}, resource={}",
                    operation.operationIndex, operation.operationType, operation.resourceType
                )

                val success = operation.undo()

                if (success) {
                    logger.debug("Successfully undone operation: index={}", operation.operationIndex)
                    results.add(
                        UndoEngine.UndoOperationResult(
                            operationIndex = operation.operationIndex,
                            operationType = operation.operationType,
                            resourceType = operation.resourceType,
                            succeeded = true
                        )
                    )
                    succeededCount++
                } else {
                    logger.warn("Undo returned false for operation: index={}", operation.operationIndex)
                    results.add(
                        UndoEngine.UndoOperationResult(
                            operationIndex = operation.operationIndex,
                            operationType = operation.operationType,
                            resourceType = operation.resourceType,
                            succeeded = false,
                            error = "Undo returned false"
                        )
                    )
                    failedCount++
                }
            } catch (e: Exception) {
                logger.error(
                    "Exception while undoing operation: index={}, error={}",
                    operation.operationIndex, e.message, e
                )
                results.add(
                    UndoEngine.UndoOperationResult(
                        operationIndex = operation.operationIndex,
                        operationType = operation.operationType,
                        resourceType = operation.resourceType,
                        succeeded = false,
                        error = e.message ?: "Unknown error"
                    )
                )
                failedCount++
            }
        }

        val result = UndoEngine.UndoResult(
            workflowId = workflowId,
            totalOperations = operations.size,
            succeededCount = succeededCount,
            failedCount = failedCount,
            results = results
        )

        logger.info(
            "Undo completed for workflow: {} - {}",
            workflowId, result.summary
        )

        return result
    }
}