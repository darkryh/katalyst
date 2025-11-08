package com.ead.katalyst.transactions.workflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scheduler for running WorkflowRecoveryJob periodically.
 *
 * Manages the background recovery process with:
 * - Configurable scan intervals
 * - Graceful shutdown
 * - Error handling and restart capability
 * - Status monitoring
 *
 * **Usage**:
 * ```kotlin
 * val scheduler = RecoveryJobScheduler(
 *     recoveryJob = workflowRecoveryJob,
 *     scanIntervalMs = 30000,  // Run every 30 seconds
 *     coroutineScope = viewModelScope  // or applicationScope
 * )
 *
 * // Start background recovery
 * scheduler.start()
 *
 * // Stop when app shuts down
 * onDestroy { scheduler.stop() }
 * ```
 *
 * **Thread Safety**: Thread-safe via AtomicBoolean and coroutines
 */
class RecoveryJobScheduler(
    private val recoveryJob: WorkflowRecoveryJob,
    private val scanIntervalMs: Long = 30000,
    private val coroutineScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(RecoveryJobScheduler::class.java)

    private val isRunning = AtomicBoolean(false)
    private var recoveryTask: Job? = null
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 5

    /**
     * Start the background recovery scheduler.
     *
     * Launches a coroutine that periodically scans for failed workflows.
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            logger.warn("Recovery job scheduler already running")
            return
        }

        logger.info("Starting recovery job scheduler (interval={}ms)", scanIntervalMs)

        recoveryTask = coroutineScope.launch {
            while (isActive) {
                try {
                    logger.debug("Running recovery job scan")
                    val result = recoveryJob.scanAndRecover()

                    // Reset error counter on successful scan
                    consecutiveErrors = 0

                    logScanResult(result)
                } catch (e: Exception) {
                    consecutiveErrors++
                    logger.error(
                        "Error during recovery job scan (consecutive errors: {}/{})",
                        consecutiveErrors, maxConsecutiveErrors, e
                    )

                    // Stop scheduler if too many consecutive errors
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        logger.error(
                            "Recovery scheduler stopping due to {} consecutive errors",
                            consecutiveErrors
                        )
                        stop()
                        return@launch
                    }
                }

                // Wait before next scan
                delay(scanIntervalMs)
            }
        }
    }

    /**
     * Stop the background recovery scheduler.
     *
     * Gracefully shuts down the recovery task.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            logger.warn("Recovery job scheduler not running")
            return
        }

        logger.info("Stopping recovery job scheduler")
        recoveryTask?.cancel()
        recoveryTask = null
        consecutiveErrors = 0
    }

    /**
     * Check if scheduler is currently running.
     */
    fun isActive(): Boolean = isRunning.get()

    /**
     * Trigger a manual recovery scan immediately.
     *
     * Useful for testing or manual intervention.
     *
     * @return The scan result
     */
    suspend fun manualScan(): RecoveryScanResult {
        logger.info("Triggering manual recovery scan")
        return recoveryJob.scanAndRecover()
    }

    /**
     * Get current recovery metrics.
     */
    fun getMetrics(): RecoveryMetrics = recoveryJob.getMetrics()

    /**
     * Get scheduler status.
     */
    fun getStatus(): RecoverySchedulerStatus {
        return RecoverySchedulerStatus(
            isRunning = isRunning.get(),
            scanIntervalMs = scanIntervalMs,
            consecutiveErrors = consecutiveErrors,
            maxConsecutiveErrors = maxConsecutiveErrors,
            metrics = recoveryJob.getMetrics()
        )
    }

    /**
     * Log scan result with appropriate level.
     */
    private fun logScanResult(result: RecoveryScanResult) {
        when {
            result.workflowsFailed == 0 && result.workflowsRecovered == 0 -> {
                logger.debug(
                    "Recovery scan #{}: No failed workflows found",
                    result.scanNumber
                )
            }
            result.workflowsFailed > 0 -> {
                logger.warn(
                    "Recovery scan #{}: Found {}, recovered {}, failed {}, rate={:.1f}%",
                    result.scanNumber,
                    result.failedWorkflowsFound,
                    result.workflowsRecovered,
                    result.workflowsFailed,
                    result.getRecoveryRate()
                )
            }
            else -> {
                logger.info(
                    "Recovery scan #{}: Successfully recovered {} workflows ({}ms)",
                    result.scanNumber,
                    result.workflowsRecovered,
                    result.durationMs
                )
            }
        }

        // Log any recovery errors
        if (result.errors.isNotEmpty()) {
            logger.error("Recovery errors found:")
            result.errors.forEach { error ->
                logger.error("  - Workflow {}: {}", error.workflowId, error.reason)
            }
        }
    }
}

/**
 * Status snapshot of recovery scheduler.
 */
data class RecoverySchedulerStatus(
    val isRunning: Boolean,
    val scanIntervalMs: Long,
    val consecutiveErrors: Int,
    val maxConsecutiveErrors: Int,
    val metrics: RecoveryMetrics
) {
    fun isHealthy(): Boolean = consecutiveErrors < maxConsecutiveErrors / 2

    override fun toString(): String = buildString {
        append("RecoveryScheduler(")
        append("running=$isRunning, ")
        append("interval=${scanIntervalMs}ms, ")
        append("errors=$consecutiveErrors/$maxConsecutiveErrors, ")
        append("scans=${metrics.totalScans}, ")
        append("recovered=${metrics.totalSuccessfulRecoveries}, ")
        append("failed=${metrics.totalFailedRecoveries}, ")
        append("rate=${String.format("%.1f%%", metrics.successRate)}")
        append(")")
    }
}
