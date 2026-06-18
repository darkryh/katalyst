package io.github.darkryh.katalyst.core.component

import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager as ExposedTransactionManager

/**
 * Marker for application services discovered by Katalyst.
 *
 * Services may keep constructor injection for explicit dependencies while still
 * using the transaction helpers below as the framework-owned transaction boundary.
 */
interface Service : Component {
    /**
     * Framework transaction manager for service-layer transaction boundaries.
     */
    val transactionManager: DatabaseTransactionManager
        get() = KatalystContainerProvider.current().get(DatabaseTransactionManager::class)

    /**
     * Run a service operation in the default transaction scope.
     *
     * Nested calls join the active Katalyst transaction when one exists;
     * otherwise a new root transaction is opened.
     */
    suspend fun <T> transaction(
        workflowId: String? = null,
        config: TransactionConfig? = null,
        block: suspend Transaction.() -> T,
    ): T =
        transactionManager.transaction(
            workflowId = workflowId,
            config = config,
            block = block,
        )

    /**
     * Run a service operation in a tracked workflow transaction.
     */
    suspend fun <T> workflowTransaction(
        workflowId: String,
        config: TransactionConfig? = null,
        block: suspend Transaction.() -> T,
    ): T =
        transactionManager.transaction(
            workflowId = workflowId,
            config = config,
            block = block,
        )

    /**
     * Run logic only when the caller already opened a transaction.
     *
     * This is useful for small private service helpers that must be composed
     * inside a larger transaction and should fail fast if called directly.
     */
    suspend fun <T> currentTransaction(
        block: suspend Transaction.() -> T,
    ): T {
        val current = ExposedTransactionManager.currentOrNull()
            ?: error("No active transaction is available for currentTransaction")

        return block(current)
    }
}
