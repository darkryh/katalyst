package io.github.darkryh.katalyst.transactions.metrics

import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Interface for collecting and storing transaction metrics.
 *
 * Implementations track transaction execution, adapter performance, errors, and other metrics
 * for observability, monitoring, and debugging.
 *
 * **Lifecycle:**
 * 1. startTransaction() - Begin tracking a transaction
 * 2. recordAdapterExecution() - Track each adapter execution
 * 3. recordOperationExecuted() - Count database operations
 * 4. recordEventPublished() - Count published events
 * 5. recordError() - Track errors that occur
 * 6. completeTransaction() - Mark transaction as complete
 * 7. getMetrics() - Retrieve metrics for analysis
 *
 * **Thread Safety:**
 * Implementations must be thread-safe for use in concurrent transaction environments.
 *
 * **Memory Management:**
 * Implementations should implement cleanup/purging to prevent unbounded growth.
 */
interface MetricsCollector {
    /**
     * Start tracking metrics for a new transaction.
     *
     * @param transactionId Unique identifier for this transaction
     * @param workflowId Optional workflow ID for grouping related transactions
     * @return TransactionMetrics object for this transaction
     */
    fun startTransaction(transactionId: String, workflowId: String? = null): TransactionMetrics

    /**
     * Record the execution of a transaction adapter.
     *
     * @param transactionId ID of the transaction
     * @param adapterName Name of the adapter
     * @param phase Transaction phase during which adapter executed
     * @param startTime When adapter started
     * @param endTime When adapter ended
     * @param success Whether adapter executed successfully
     * @param error Exception thrown by adapter (if any)
     */
    fun recordAdapterExecution(
        transactionId: String,
        adapterName: String,
        phase: TransactionPhase,
        startTime: Instant,
        endTime: Instant,
        success: Boolean,
        error: Exception? = null
    )

    /**
     * Record that an event was published.
     *
     * @param transactionId ID of the transaction
     */
    fun recordEventPublished(transactionId: String)

    /**
     * Record that a database operation was executed.
     *
     * @param transactionId ID of the transaction
     */
    fun recordOperationExecuted(transactionId: String)

    /**
     * Record an error that occurred during transaction execution.
     *
     * @param transactionId ID of the transaction
     * @param phase Phase during which error occurred
     * @param error Exception that occurred
     * @param isRetryable Whether this error is retryable
     */
    fun recordError(
        transactionId: String,
        phase: TransactionPhase,
        error: Exception,
        isRetryable: Boolean
    )

    /**
     * Mark a transaction as complete and record final status.
     *
     * @param transactionId ID of the transaction
     * @param status Final status of the transaction
     * @param retryCount Number of retries that were attempted
     */
    fun completeTransaction(
        transactionId: String,
        status: TransactionStatus,
        retryCount: Int = 0
    )

    /**
     * Retrieve metrics for a specific transaction.
     *
     * @param transactionId ID of the transaction
     * @return TransactionMetrics if found, null if transaction not tracked
     */
    fun getMetrics(transactionId: String): TransactionMetrics?

    /**
     * Get all metrics currently tracked.
     *
     * @return Map of transactionId to TransactionMetrics
     */
    fun getAllMetrics(): Map<String, TransactionMetrics>

    /**
     * Clear metrics for a specific transaction.
     *
     * Useful for cleanup and memory management.
     *
     * @param transactionId ID of the transaction to clear
     * @return true if transaction was cleared, false if not found
     */
    fun clearMetrics(transactionId: String): Boolean

    /**
     * Clear all metrics.
     *
     * Warning: This removes all tracked metrics from memory.
     */
    fun clearAllMetrics()
}

/**
 * Default in-memory implementation of MetricsCollector.
 *
 * Stores metrics in thread-safe ConcurrentHashMap.
 * Suitable for single-node deployments and testing.
 *
 * For distributed deployments, consider implementing a persistent
 * store using database or monitoring system (Prometheus, Datadog, etc.).
 *
 * **Memory Considerations:**
 * In production, implement periodic cleanup of old metrics to prevent
 * unbounded memory growth. For example:
 *
 * ```kotlin
 * // Clean metrics older than 1 hour every minute
 * val scheduler = Executors.newScheduledThreadPool(1)
 * scheduler.scheduleAtFixedRate({
 *     val oneHourAgo = Instant.now().minus(Duration.ofHours(1))
 *     collector.clearMetricsBefore(oneHourAgo)
 * }, 1, 1, TimeUnit.MINUTES)
 * ```
 */
class DefaultMetricsCollector : MetricsCollector {
    private val metricsMap = ConcurrentHashMap<String, TransactionMetrics>()

    override fun startTransaction(transactionId: String, workflowId: String?): TransactionMetrics {
        val metrics = TransactionMetrics(transactionId, workflowId)
        metricsMap[transactionId] = metrics
        return metrics
    }

    override fun recordAdapterExecution(
        transactionId: String,
        adapterName: String,
        phase: TransactionPhase,
        startTime: Instant,
        endTime: Instant,
        success: Boolean,
        error: Exception?
    ) {
        metricsMap[transactionId]?.let { metrics ->
            val duration = (endTime.toEpochMilli() - startTime.toEpochMilli()).toDuration(DurationUnit.MILLISECONDS)
            metrics.adapterExecutions.add(
                AdapterMetrics(
                    adapterName = adapterName,
                    phase = phase,
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    success = success,
                    error = error
                )
            )
        }
    }

    override fun recordEventPublished(transactionId: String) {
        metricsMap[transactionId]?.let { metrics ->
            metrics.eventCount++
        }
    }

    override fun recordOperationExecuted(transactionId: String) {
        metricsMap[transactionId]?.let { metrics ->
            metrics.operationCount++
        }
    }

    override fun recordError(
        transactionId: String,
        phase: TransactionPhase,
        error: Exception,
        isRetryable: Boolean
    ) {
        metricsMap[transactionId]?.errors?.add(
            TransactionError(
                timestamp = Instant.now(),
                phase = phase,
                message = error.message ?: "Unknown error",
                stackTrace = error.stackTraceToString(),
                isRetryable = isRetryable,
                exceptionClassName = error::class.simpleName ?: "Unknown"
            )
        )
    }

    override fun completeTransaction(
        transactionId: String,
        status: TransactionStatus,
        retryCount: Int
    ) {
        metricsMap[transactionId]?.let { metrics ->
            metrics.endTime = Instant.now()
            metrics.status = status
            metrics.retryCount = retryCount
            metrics.duration = (metrics.endTime!!.toEpochMilli() - metrics.startTime.toEpochMilli()).toDuration(DurationUnit.MILLISECONDS)
        }
    }

    override fun getMetrics(transactionId: String): TransactionMetrics? {
        return metricsMap[transactionId]
    }

    override fun getAllMetrics(): Map<String, TransactionMetrics> {
        return metricsMap.toMap()
    }

    override fun clearMetrics(transactionId: String): Boolean {
        return metricsMap.remove(transactionId) != null
    }

    override fun clearAllMetrics() {
        metricsMap.clear()
    }

    /**
     * Get count of currently tracked transactions.
     */
    fun getTransactionCount(): Int = metricsMap.size

    /**
     * Clean up metrics older than a specific instant.
     *
     * Useful for periodic cleanup to prevent unbounded growth.
     *
     * @param beforeInstant Remove metrics with endTime before this instant
     * @return Number of metrics removed
     */
    fun clearMetricsBefore(beforeInstant: Instant): Int {
        var removed = 0
        metricsMap.entries.removeIf { (_, metrics) ->
            if (metrics.endTime != null && metrics.endTime!! < beforeInstant) {
                removed++
                true
            } else {
                false
            }
        }
        return removed
    }
}

/**
 * No-op implementation of MetricsCollector for testing.
 *
 * All methods do nothing. Useful for testing when metrics collection
 * overhead should be eliminated.
 */
class NoOpMetricsCollector : MetricsCollector {
    override fun startTransaction(transactionId: String, workflowId: String?): TransactionMetrics {
        return TransactionMetrics(transactionId, workflowId)
    }

    override fun recordAdapterExecution(
        transactionId: String,
        adapterName: String,
        phase: TransactionPhase,
        startTime: Instant,
        endTime: Instant,
        success: Boolean,
        error: Exception?
    ) {
        // No-op
    }

    override fun recordEventPublished(transactionId: String) {
        // No-op
    }

    override fun recordOperationExecuted(transactionId: String) {
        // No-op
    }

    override fun recordError(
        transactionId: String,
        phase: TransactionPhase,
        error: Exception,
        isRetryable: Boolean
    ) {
        // No-op
    }

    override fun completeTransaction(
        transactionId: String,
        status: TransactionStatus,
        retryCount: Int
    ) {
        // No-op
    }

    override fun getMetrics(transactionId: String): TransactionMetrics? = null

    override fun getAllMetrics(): Map<String, TransactionMetrics> = emptyMap()

    override fun clearMetrics(transactionId: String): Boolean = false

    override fun clearAllMetrics() {
        // No-op
    }
}
