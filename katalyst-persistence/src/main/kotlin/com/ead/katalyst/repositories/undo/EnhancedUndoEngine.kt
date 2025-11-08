package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.UndoEngine
import com.ead.katalyst.transactions.workflow.TransactionOperation
import org.slf4j.LoggerFactory

/**
 * Enhanced implementation of UndoEngine with strategy-based execution and retry logic.
 *
 * **Improvements over SimpleUndoEngine**:
 * - Pluggable strategies for different operation types
 * - Automatic retry with exponential backoff
 * - Better error tracking and detailed reporting
 * - Support for operation-specific undo approaches
 *
 * **Execution Flow**:
 * 1. Processes operations in reverse order (LIFO)
 * 2. For each operation:
 *    a. Find appropriate strategy based on operation type
 *    b. Execute undo with retry policy
 *    c. Track success/failure and error details
 * 3. Return detailed summary with per-operation results
 *
 * **Phase 3 Implementation**:
 * - All operations undone with configurable retry policy
 * - Detailed error tracking for each operation
 * - Continues with remaining operations even if some fail (best-effort)
 */
class EnhancedUndoEngine(
    private val strategyRegistry: UndoStrategyRegistry = UndoStrategyRegistry.createDefault(),
    private val retryPolicy: RetryPolicy = RetryPolicy.aggressive()
) : UndoEngine {

    private val logger = LoggerFactory.getLogger(EnhancedUndoEngine::class.java)

    /**
     * Undo all operations in reverse order with strategy-based execution.
     *
     * @param workflowId Workflow ID
     * @param operations All operations for the workflow (will be reversed)
     * @return Detailed result with success/failure counts and per-operation results
     */
    override suspend fun undoWorkflow(
        workflowId: String,
        operations: List<TransactionOperation>
    ): UndoEngine.UndoResult {
        logger.info(
            "Starting enhanced undo for workflow: {} with {} operations",
            workflowId, operations.size
        )

        val results = mutableListOf<UndoEngine.UndoOperationResult>()
        var succeededCount = 0
        var failedCount = 0

        // Execute in reverse order (LIFO) - most recent first
        for (operation in operations.reversed()) {
            try {
                logger.debug(
                    "Processing undo: index={}, type={}, resource={}",
                    operation.operationIndex, operation.operationType, operation.resourceType
                )

                // Find the appropriate strategy
                val strategy = strategyRegistry.findStrategy(
                    operation.operationType,
                    operation.resourceType
                )

                // Execute undo with retry logic
                val success = retryPolicy.execute {
                    strategy.undo(operation)
                }

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
                    logger.warn("Undo failed for operation: index={}", operation.operationIndex)
                    results.add(
                        UndoEngine.UndoOperationResult(
                            operationIndex = operation.operationIndex,
                            operationType = operation.operationType,
                            resourceType = operation.resourceType,
                            succeeded = false,
                            error = "Undo operation returned false after retries"
                        )
                    )
                    failedCount++
                }
            } catch (e: Exception) {
                logger.error(
                    "Exception while undoing operation: index={}, type={}, error={}",
                    operation.operationIndex, operation.operationType, e.message, e
                )
                results.add(
                    UndoEngine.UndoOperationResult(
                        operationIndex = operation.operationIndex,
                        operationType = operation.operationType,
                        resourceType = operation.resourceType,
                        succeeded = false,
                        error = e.message ?: "Unknown error: ${e::class.simpleName}"
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
            "Enhanced undo completed for workflow: {} - {}",
            workflowId, result.summary
        )

        return result
    }
}
