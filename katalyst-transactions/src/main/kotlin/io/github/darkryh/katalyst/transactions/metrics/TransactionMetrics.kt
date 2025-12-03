package io.github.darkryh.katalyst.transactions.metrics

import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Status of a transaction.
 *
 * - RUNNING: Transaction is actively executing
 * - COMMITTED: Transaction successfully committed
 * - ROLLED_BACK: Transaction was rolled back
 * - TIMEOUT: Transaction exceeded timeout duration
 * - FAILED: Transaction failed with exception
 */
enum class TransactionStatus {
    RUNNING,
    COMMITTED,
    ROLLED_BACK,
    TIMEOUT,
    FAILED
}

/**
 * Metrics collected during a transaction execution.
 *
 * Tracks performance, status, and execution details for audit trail,
 * performance monitoring, and debugging.
 *
 * **Usage:**
 * ```kotlin
 * val metrics = metricsCollector.startTransaction("tx-123")
 * try {
 *     transactionManager.transaction { ... }
 * } catch (e: Exception) {
 *     metricsCollector.recordError("tx-123", TransactionPhase.BEFORE_COMMIT, e)
 * }
 * metricsCollector.completeTransaction("tx-123", TransactionStatus.COMMITTED)
 * ```
 */
data class TransactionMetrics(
    /**
     * Unique identifier for this transaction.
     */
    val transactionId: String,

    /**
     * Optional workflow ID for grouping related transactions.
     */
    val workflowId: String? = null,

    /**
     * Timestamp when transaction execution started.
     */
    val startTime: Instant = Instant.now(),

    /**
     * Timestamp when transaction execution ended (null if still running).
     */
    var endTime: Instant? = null,

    /**
     * Current status of the transaction.
     */
    var status: TransactionStatus = TransactionStatus.RUNNING,

    /**
     * Number of database operations (queries) executed in this transaction.
     */
    var operationCount: Int = 0,

    /**
     * Number of events published during this transaction.
     */
    var eventCount: Int = 0,

    /**
     * Total duration of transaction execution.
     * Calculated from startTime to endTime.
     */
    var duration: Duration? = null,

    /**
     * Execution results for each transaction adapter.
     * Tracks timing and success/failure for each phase.
     */
    val adapterExecutions: MutableList<AdapterMetrics> = mutableListOf(),

    /**
     * All errors that occurred during transaction execution.
     * Includes both retried and final errors.
     */
    val errors: MutableList<TransactionError> = mutableListOf(),

    /**
     * Number of retry attempts made (0 for successful first attempt).
     */
    var retryCount: Int = 0,

    /**
     * Transaction configuration used (timeout, retry policy, isolation level).
     */
    val config: Any? = null
)

/**
 * Metrics for a single adapter execution within a transaction phase.
 *
 * Tracks which adapter executed, in which phase, and how long it took.
 *
 * **Example:**
 * - Adapter: EventsTransactionAdapter
 * - Phase: AFTER_COMMIT
 * - Duration: 45ms
 * - Success: true
 */
data class AdapterMetrics(
    /**
     * Name/identifier of the adapter.
     */
    val adapterName: String,

    /**
     * Transaction phase during which this adapter executed.
     */
    val phase: TransactionPhase,

    /**
     * Timestamp when adapter execution started.
     */
    val startTime: Instant,

    /**
     * Timestamp when adapter execution completed (null if still running).
     */
    var endTime: Instant? = null,

    /**
     * Duration of adapter execution.
     * Calculated from startTime to endTime.
     */
    var duration: Duration? = null,

    /**
     * Whether this adapter executed successfully.
     */
    var success: Boolean = false,

    /**
     * Exception thrown by adapter (if any).
     */
    var error: Exception? = null
) {
    /**
     * Calculate and return duration if endTime is set.
     */
    fun calculateDuration(): Duration? {
        return if (endTime != null) {
            (endTime!!.toEpochMilli() - startTime.toEpochMilli()).toDuration(DurationUnit.MILLISECONDS)
        } else {
            null
        }
    }
}

/**
 * Details of an error that occurred during transaction execution.
 *
 * Errors can be transient (retried) or permanent (failed transaction).
 *
 * **Example:**
 * - Phase: BEFORE_COMMIT
 * - Message: "Database deadlock detected"
 * - Retryable: true
 * - Stack trace for debugging
 */
data class TransactionError(
    /**
     * Timestamp when error occurred.
     */
    val timestamp: Instant,

    /**
     * Phase during which error occurred.
     */
    val phase: TransactionPhase,

    /**
     * Error message.
     */
    val message: String,

    /**
     * Full stack trace for debugging.
     */
    val stackTrace: String,

    /**
     * Whether this error is retryable.
     * Transient errors (deadlocks, timeouts) are retryable.
     * Permanent errors (validation, auth) are not retryable.
     */
    val isRetryable: Boolean,

    /**
     * Exception class name for categorization.
     */
    val exceptionClassName: String = Exception::class.simpleName ?: "Unknown"
)

/**
 * Summary statistics across multiple transactions.
 *
 * Used for reporting and monitoring transaction health.
 */
data class TransactionMetricsSummary(
    /**
     * Total number of transactions tracked.
     */
    val totalTransactions: Int,

    /**
     * Number of successful transactions.
     */
    val successfulTransactions: Int,

    /**
     * Number of failed transactions.
     */
    val failedTransactions: Int,

    /**
     * Number of timed-out transactions.
     */
    val timedOutTransactions: Int,

    /**
     * Number of rolled-back transactions.
     */
    val rolledBackTransactions: Int,

    /**
     * Average transaction duration.
     */
    val averageDuration: Duration,

    /**
     * Minimum transaction duration.
     */
    val minDuration: Duration,

    /**
     * Maximum transaction duration.
     */
    val maxDuration: Duration,

    /**
     * P50 (median) transaction duration.
     */
    val p50Duration: Duration,

    /**
     * P95 transaction duration.
     */
    val p95Duration: Duration,

    /**
     * P99 transaction duration.
     */
    val p99Duration: Duration,

    /**
     * Total operations (queries) executed.
     */
    val totalOperations: Int,

    /**
     * Total events published.
     */
    val totalEvents: Int,

    /**
     * Total errors encountered.
     */
    val totalErrors: Int,

    /**
     * Total retry attempts made.
     */
    val totalRetries: Int,

    /**
     * Success rate as a percentage.
     */
    val successRate: Double,

    /**
     * Average retry count per transaction.
     */
    val averageRetries: Double
)
