package com.ead.katalyst.transactions.config

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Strategy for calculating backoff delay between retries.
 *
 * Different backoff strategies handle transient failures differently:
 * - EXPONENTIAL: Good for distributed systems, prevents thundering herd
 * - LINEAR: Simpler, predictable delays
 * - IMMEDIATE: No delay, for retries that should happen immediately
 */
enum class BackoffStrategy {
    /**
     * Exponential backoff: 1s, 2s, 4s, 8s, 16s, ...
     *
     * Good for:
     * - Database deadlock recovery
     * - Network timeouts
     * - High contention scenarios
     */
    EXPONENTIAL,

    /**
     * Linear backoff: 1s, 2s, 3s, 4s, 5s, ...
     *
     * Good for:
     * - Predictable scenarios
     * - Testing
     * - Simple transient errors
     */
    LINEAR,

    /**
     * Immediate retry with no delay.
     *
     * Good for:
     * - Lock acquisition
     * - Quick circuit breaker recovery
     * - Optimistic retries
     */
    IMMEDIATE
}

/**
 * Retry policy for transactions.
 *
 * Controls how failed transactions are retried with backoff strategies
 * and exception filtering.
 *
 * **Usage:**
 * ```kotlin
 * val policy = RetryPolicy(
 *     maxRetries = 3,
 *     backoffStrategy = BackoffStrategy.EXPONENTIAL,
 *     initialDelayMs = 1000,
 *     maxDelayMs = 30000
 * )
 *
 * transactionManager.transaction(
 *     config = TransactionConfig(retryPolicy = policy)
 * ) {
 *     // Will retry up to 3 times on transient errors
 * }
 * ```
 */
data class RetryPolicy(
    /**
     * Maximum number of retries after initial attempt.
     *
     * Total attempts = maxRetries + 1 (initial attempt)
     *
     * Default: 3 (so 4 total attempts)
     */
    val maxRetries: Int = 3,

    /**
     * Strategy for calculating backoff delay between retries.
     *
     * Default: EXPONENTIAL (prevents thundering herd)
     */
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,

    /**
     * Initial delay for first retry in milliseconds.
     *
     * Default: 1000ms (1 second)
     */
    val initialDelayMs: Long = 1000,

    /**
     * Maximum delay between retries in milliseconds.
     *
     * Prevents delays from growing unbounded with exponential backoff.
     *
     * Default: 30000ms (30 seconds)
     */
    val maxDelayMs: Long = 30000,

    /**
     * Add randomness to backoff delay to prevent thundering herd.
     *
     * Applied as a percentage: 0.1 = ±10% variance
     *
     * Default: 0.1 (10% jitter)
     */
    val jitterFactor: Double = 0.1,

    /**
     * Exceptions that should trigger automatic retry.
     *
     * Typically transient errors that might succeed on retry:
     * - Database deadlocks
     * - Connection timeouts
     * - Temporary network errors
     *
     * Default: SQL, Timeout, IOException, Deadlock
     */
    val retryableExceptions: Set<KClass<out Exception>> = setOf(
        java.sql.SQLException::class,
        java.util.concurrent.TimeoutException::class,
        java.io.IOException::class
    ),

    /**
     * Exceptions that should NOT be retried.
     *
     * Permanent errors that won't succeed on retry:
     * - Validation errors
     * - Authentication failures
     * - Data integrity violations
     *
     * Default: Validation, Authentication, DataIntegrityViolation
     */
    val nonRetryableExceptions: Set<KClass<out Exception>> = setOf(
        java.lang.IllegalArgumentException::class,
        java.lang.SecurityException::class
    )
)

/**
 * Isolation level for transaction execution.
 *
 * Determines how concurrent transactions interact:
 * - READ_UNCOMMITTED: Lowest isolation, highest performance
 * - READ_COMMITTED: Prevents dirty reads (most common)
 * - REPEATABLE_READ: Prevents dirty and non-repeatable reads
 * - SERIALIZABLE: Highest isolation, lowest performance
 */
enum class TransactionIsolationLevel {
    /**
     * READ_UNCOMMITTED:
     * - Transaction can read uncommitted changes from other transactions
     * - Allows dirty reads
     * - Highest performance, lowest isolation
     */
    READ_UNCOMMITTED,

    /**
     * READ_COMMITTED (DEFAULT):
     * - Transaction can only read committed changes
     * - Prevents dirty reads
     * - Good balance of performance and isolation
     */
    READ_COMMITTED,

    /**
     * REPEATABLE_READ:
     * - Prevents dirty reads and non-repeatable reads
     * - Snapshots data for duration of transaction
     * - Better isolation, slightly lower performance
     */
    REPEATABLE_READ,

    /**
     * SERIALIZABLE:
     * - Highest isolation level
     * - Transactions execute as if they were serial
     * - Prevents all anomalies
     * - Lowest performance, highest isolation
     */
    SERIALIZABLE
}

/**
 * Configuration for transaction execution.
 *
 * Controls timeout, retry behavior, and isolation level for transactions.
 *
 * **Usage:**
 * ```kotlin
 * val config = TransactionConfig(
 *     timeout = 30.seconds,
 *     retryPolicy = RetryPolicy(maxRetries = 3),
 *     isolationLevel = TransactionIsolationLevel.READ_COMMITTED
 * )
 *
 * transactionManager.transaction(config = config) {
 *     // Transaction with custom timeout, retry, and isolation
 * }
 * ```
 */
data class TransactionConfig(
    /**
     * Maximum duration for transaction execution.
     *
     * If transaction exceeds this duration, it's cancelled with TimeoutException.
     *
     * Default: 30 seconds
     */
    val timeout: Duration = 30.toDuration(DurationUnit.SECONDS),

    /**
     * Retry policy for failed transactions.
     *
     * Defines how many times to retry and with what backoff strategy.
     *
     * Default: Exponential backoff up to 3 retries
     */
    val retryPolicy: RetryPolicy = RetryPolicy(),

    /**
     * Isolation level for the transaction.
     *
     * Determines how this transaction interacts with concurrent transactions.
     *
     * Default: READ_COMMITTED
     */
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.READ_COMMITTED
)
