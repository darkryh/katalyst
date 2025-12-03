package io.github.darkryh.katalyst.transactions.adapter

/**
 * Exception thrown when a critical transaction adapter fails.
 *
 * When a critical adapter (marked with isCritical() = true) fails during transaction execution,
 * this exception is thrown to cause the transaction to rollback and prevent inconsistent state.
 *
 * **Example:** If EventsTransactionAdapter fails during BEFORE_COMMIT_VALIDATION phase,
 * this exception is thrown, causing the entire transaction to rollback rather than
 * committing with unpublished events.
 */
class TransactionAdapterException(
    message: String,
    cause: Exception? = null
) : RuntimeException(message, cause)
