package com.ead.katalyst.transactions.manager

import com.ead.katalyst.transactions.adapter.TransactionAdapter
import org.jetbrains.exposed.sql.Transaction

/**
 * Abstract interface for managing database transactions.
 *
 * Provides a clean API for executing code within a transaction context
 * with support for adapters and other cross-cutting concerns.
 *
 * **Implementation Requirements:**
 * - Implementations must support suspend functions
 * - Must create TransactionEventContext for event queueing
 * - Must execute adapters at appropriate phases
 * - Must handle rollback on exception
 * - Must clean up context after transaction
 *
 * **Usage:**
 * ```kotlin
 * val result = transactionManager.transaction {
 *     // Code here executes in transaction context
 *     repository.save(entity)
 *     // Events published here are queued
 *     eventBus.publish(Event(...))
 * }
 * // After block returns: adapters handle post-commit concerns (event publishing, etc.)
 * ```
 *
 * **Adapter Architecture:**
 * Modules register adapters to handle their transaction concerns independently:
 * - Persistence module registers adapter for DB state
 * - Events module registers adapter for event publishing
 * - Future modules register adapters for their concerns
 *
 * This enables clean decoupling without circular dependencies.
 */
interface TransactionManager {
    /**
     * Executes a block of code within a database transaction.
     *
     * **Execution Flow:**
     * 1. Create TransactionEventContext
     * 2. Execute adapters: BEFORE_BEGIN
     * 3. Begin database transaction
     * 4. Execute adapters: AFTER_BEGIN
     * 5. Execute user block
     * 6. Execute adapters: BEFORE_COMMIT
     * 7. Commit transaction
     * 8. Execute adapters: AFTER_COMMIT
     * 9. Return result
     *
     * **On Exception:**
     * 1. Execute adapters: ON_ROLLBACK
     * 2. Rollback transaction
     * 3. Execute adapters: AFTER_ROLLBACK
     * 4. Re-throw exception
     *
     * @param T The return type of the block
     * @param block The suspend function to execute within transaction
     * @return The result of the block
     * @throws Exception If transaction fails or block throws
     */
    suspend fun <T> transaction(block: suspend Transaction.() -> T): T

    /**
     * Registers a transaction adapter.
     *
     * Adapters execute in priority order during transaction phases.
     * Adapters from different modules can be registered without creating dependencies.
     *
     * @param adapter The adapter to register
     */
    fun addAdapter(adapter: TransactionAdapter)

    /**
     * Unregisters a transaction adapter.
     *
     * Mainly used for testing/cleanup. In production, adapters typically persist
     * for the application lifetime after registration during DI bootstrap.
     *
     * @param adapter The adapter to remove
     */
    fun removeAdapter(adapter: TransactionAdapter)
}
