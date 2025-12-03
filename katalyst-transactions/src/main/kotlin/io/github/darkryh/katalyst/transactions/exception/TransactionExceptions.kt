package io.github.darkryh.katalyst.transactions.exception

import kotlin.time.Duration

/**
 * Thrown when a transaction exceeds its configured timeout duration.
 *
 * Indicates that the transaction took longer than allowed and was cancelled.
 *
 * **Causes:**
 * - Database query is slow or deadlocked
 * - Network latency is high
 * - Application code is slow
 * - Resource contention
 *
 * **Recovery:**
 * - Automatic retry with exponential backoff
 * - Increase timeout if legitimate
 * - Optimize slow operations
 * - Distribute load
 */
class TransactionTimeoutException(
    message: String = "Transaction timeout",
    val transactionId: String = "unknown",
    val attemptNumber: Int = 0,
    val timeout: Duration? = null,
    cause: Throwable? = null
) : Exception(
    "$message (transactionId=$transactionId, attempt=$attemptNumber, timeout=$timeout)",
    cause
) {
    /**
     * Whether this timeout exception indicates a retryable situation.
     *
     * Generally true - timeouts are often transient and can be retried.
     */
    val isRetryable: Boolean = true
}

/**
 * Thrown when a transaction fails after exhausting all retry attempts.
 *
 * Indicates that the transaction failed, retries were attempted,
 * but all retries also failed.
 *
 * **Causes:**
 * - Permanent database error
 * - Data constraint violation
 * - Authentication/authorization failure
 * - Application logic error
 *
 * **Recovery:**
 * - Manual intervention required
 * - Check logs for root cause
 * - Fix underlying issue
 * - Retry manually if appropriate
 */
class TransactionFailedException(
    message: String = "Transaction failed",
    val transactionId: String = "unknown",
    val finalAttemptNumber: Int = 0,
    val totalRetries: Int = 0,
    cause: Throwable? = null
) : Exception(
    "$message (transactionId=$transactionId, attempts=$finalAttemptNumber, retries=$totalRetries)",
    cause
) {
    /**
     * Whether this failure is due to a retryable condition.
     *
     * If false, retries won't help and manual intervention is needed.
     */
    val isRetryable: Boolean = cause?.let {
        when (it) {
            is java.sql.SQLException -> {
                // Check for specific SQL error codes
                it.errorCode.let { code ->
                    // Deadlock: 1213 (MySQL), 40P01 (PostgreSQL)
                    // Lock timeout: 1205 (MySQL)
                    code in setOf(1213, 1205)
                }
            }
            is java.util.concurrent.TimeoutException -> true
            is java.io.IOException -> true
            else -> false
        }
    } ?: false
}

/**
 * Thrown when a database deadlock is detected.
 *
 * Indicates that two transactions are waiting for each other to release locks.
 *
 * **Causes:**
 * - Two transactions acquiring locks in different order
 * - High contention on same resources
 * - Slow transaction holding locks

 *
 * **Recovery:**
 * - Automatic retry with backoff
 * - Reorder lock acquisition
 * - Reduce transaction duration
 * - Partition data to reduce contention
 */
class DeadlockException(
    message: String = "Database deadlock detected",
    val transactionId: String = "unknown",
    cause: Throwable? = null
) : java.sql.SQLException(message, cause) {
    /**
     * Whether this is a retryable deadlock.
     *
     * Typically true - retrying usually resolves the deadlock.
     */
    val isRetryable: Boolean = true
}

/**
 * Thrown when a transaction encounters a transient error.
 *
 * Indicates an error that might succeed on retry:
 * - Connection pool timeout
 * - Network glitch
 * - Temporary server unavailability
 *
 * **Recovery:**
 * - Automatic retry with backoff
 * - Circuit breaker pattern
 * - Fallback to alternate server
 */
class TransientException(
    message: String = "Transient error occurred",
    val transactionId: String = "unknown",
    cause: Throwable? = null
) : Exception(message, cause) {
    /**
     * This exception is always retryable by definition.
     */
    val isRetryable: Boolean = true
}

/**
 * Helper to determine if an exception is retryable.
 */
fun Exception.isTransient(): Boolean = when (this) {
    is TransactionTimeoutException -> true
    is TransientException -> true
    is DeadlockException -> true
    is java.util.concurrent.TimeoutException -> true
    is java.io.IOException -> true
    is java.sql.SQLException -> {
        // Check for common transient SQL errors
        val sqlState = this.sqlState
        sqlState?.startsWith("08") == true ||  // Connection errors
            sqlState?.startsWith("40") == true    // Transaction timeout/conflict
    }
    else -> false
}
