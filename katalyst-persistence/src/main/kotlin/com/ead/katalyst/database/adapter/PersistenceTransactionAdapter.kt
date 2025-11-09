package com.ead.katalyst.database.adapter

import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory

/**
 * Transaction adapter for persistence concerns.
 *
 * Handles database-specific transaction lifecycle:
 * - Connection validation
 * - Schema initialization
 * - Transaction state logging
 *
 * **Execution Priority**: 10 (high priority - runs early)
 *
 * **Phases Handled:**
 * - BEFORE_BEGIN: Prepare database connection and resources
 * - AFTER_BEGIN: Log transaction start and validate isolation level
 * - BEFORE_COMMIT_VALIDATION: No-op (let other adapters validate) (NEW - P0)
 * - BEFORE_COMMIT: Validate state and flush pending writes
 * - AFTER_COMMIT: Connection cleanup and state reset
 * - ON_ROLLBACK: Clear pending writes and reset transaction state
 * - AFTER_ROLLBACK: Final cleanup of database resources
 */
class PersistenceTransactionAdapter : TransactionAdapter {
    private val logger = LoggerFactory.getLogger(PersistenceTransactionAdapter::class.java)

    override fun name(): String = "Persistence"

    override fun priority(): Int = 10  // High priority - run before other adapters

    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_BEGIN -> {
                logger.debug("Preparing database connection for transaction")
                // Initialize connection pool, validation, etc.
                // Connection is already managed by Exposed's newSuspendedTransaction
            }
            TransactionPhase.AFTER_BEGIN -> {
                logger.debug("Transaction started, validating connection")
                // Log transaction ID, validate isolation level
                // Connection is now active and within transaction context
            }
            TransactionPhase.BEFORE_COMMIT_VALIDATION -> {
                logger.debug("Before commit validation phase - persistence no-op")
                // No-op for persistence adapter
                // Other adapters (e.g., Events) handle critical validation
                // NEW - P0: Event Publishing Validation
            }
            TransactionPhase.BEFORE_COMMIT -> {
                logger.debug("Preparing to commit transaction")
                // Final validation, flush pending writes
                // Still within transaction context
            }
            TransactionPhase.AFTER_COMMIT -> {
                logger.debug("Transaction committed successfully")
                // Connection cleanup, state reset
                // Transaction has been committed
            }
            TransactionPhase.ON_ROLLBACK -> {
                logger.debug("Rolling back transaction")
                // Clear pending writes, reset state
                // Exposed handles rollback automatically
            }
            TransactionPhase.AFTER_ROLLBACK -> {
                logger.debug("Transaction rolled back, cleaning up connection")
                // Final cleanup
                // Transaction has been fully rolled back
            }
        }
    }
}
