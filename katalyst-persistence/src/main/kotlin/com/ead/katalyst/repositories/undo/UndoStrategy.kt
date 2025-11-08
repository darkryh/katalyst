package com.ead.katalyst.repositories.undo

import com.ead.katalyst.transactions.workflow.TransactionOperation

/**
 * Strategy pattern for executing undo operations.
 *
 * Different resource types require different undo approaches:
 * - DELETE operations need to be undone by RE-INSERTING the record
 * - INSERT operations need to be undone by DELETING the record
 * - UPDATE operations need to be undone by UPDATING back to original values
 * - External API calls need to be undone by calling an inverse endpoint
 *
 * Each UndoStrategy encapsulates the logic for reversing a specific type of operation.
 *
 * **Phase 3 Implementation**:
 * - Pluggable strategy pattern for extensibility
 * - Each strategy can implement custom retry logic
 * - Support for both synchronous and asynchronous undo operations
 * - Detailed error tracking and recovery
 */
interface UndoStrategy {
    /**
     * Check if this strategy can handle the given operation type.
     *
     * @param operationType The type of operation (INSERT, UPDATE, DELETE, API_CALL, etc)
     * @param resourceType The type of resource being operated on
     * @return true if this strategy can handle the operation
     */
    fun canHandle(operationType: String, resourceType: String): Boolean

    /**
     * Execute the undo operation.
     *
     * @param operation The operation to undo
     * @return true if undo succeeded, false if undo failed
     * @throws Exception if an unrecoverable error occurs
     */
    suspend fun undo(operation: TransactionOperation): Boolean
}
