package com.ead.katalyst.transactions.workflow

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Background job for recovering failed workflows.
 *
 * Responsibilities:
 * - Periodically scan for failed workflows
 * - Attempt recovery using appropriate strategy based on failure type
 * - Retry with exponential backoff
 * - Track recovery metrics and success rates
 * - Generate alerts for unrecoverable failures
 *
 * **Recovery Strategies**:
 * 1. RETRY: Retry the entire workflow from FAILED state
 * 2. RESUME_FROM_CHECKPOINT: Resume from last successful checkpoint
 * 3. MANUAL_INTERVENTION: Mark for manual review (requires support ticket)
 * 4. ABANDON: Skip workflow (log and monitor)
 *
 * **Thread Safety**: Thread-safe via ConcurrentHashMap and AtomicLong
 *
 * **Usage**:
 * ```kotlin
 * val recoveryJob = WorkflowRecoveryJob(
 *     workflowStateManager = workflowStateRepository,
 *     operationLog = operationLogRepository,
 *     undoEngine = enhancedUndoEngine,
 *     config = RecoveryJobConfig(
 *         maxRetriesPerWorkflow = 3,
 *         retryDelayMs = 1000,
 *         scanIntervalMs = 30000
 *     )
 * )
 *
 * // Run periodically (e.g., every 30 seconds)
 * scheduler.scheduleAtFixedRate({ recoveryJob.scanAndRecover() }, 30, 30, TimeUnit.SECONDS)
 * ```
 */
class WorkflowRecoveryJob(
    private val workflowStateManager: WorkflowStateManager,
    private val operationLog: OperationLog,
    private val undoEngine: UndoEngine,
    private val config: RecoveryJobConfig = RecoveryJobConfig()
) {
    private val logger = LoggerFactory.getLogger(WorkflowRecoveryJob::class.java)

    // Metrics tracking
    private val totalScans = AtomicLong(0)
    private val failedWorkflowsFound = AtomicLong(0)
    private val successfulRecoveries = AtomicLong(0)
    private val failedRecoveries = AtomicLong(0)
    private val workflowRetryAttempts = ConcurrentHashMap<String, Int>()

    /**
     * Scan for failed workflows and attempt recovery.
     *
     * Called periodically by scheduler.
     *
     * @return RecoveryScanResult with statistics
     */
    suspend fun scanAndRecover(): RecoveryScanResult {
        val scanStartTime = System.currentTimeMillis()
        totalScans.incrementAndGet()

        logger.info("Starting workflow recovery scan (scan #{}, batch size: {})",
            totalScans.get(), config.batchSize)

        return try {
            val failedWorkflows = workflowStateManager.getFailedWorkflows()
            failedWorkflowsFound.addAndGet(failedWorkflows.size.toLong())

            if (failedWorkflows.isEmpty()) {
                logger.debug("No failed workflows found")
                return RecoveryScanResult(
                    scanNumber = totalScans.get(),
                    failedWorkflowsFound = 0,
                    workflowsRecovered = 0,
                    workflowsFailed = 0,
                    durationMs = System.currentTimeMillis() - scanStartTime,
                    errors = emptyList()
                )
            }

            logger.info("Found {} failed workflows, attempting recovery", failedWorkflows.size)

            val errors = mutableListOf<RecoveryError>()
            var recovered = 0
            var failed = 0

            // Process workflows in batches
            failedWorkflows.chunked(config.batchSize).forEach { batch ->
                for (workflow in batch) {
                    val result = attemptRecovery(workflow)
                    if (result.isSuccessful) {
                        recovered++
                        successfulRecoveries.incrementAndGet()
                    } else {
                        failed++
                        failedRecoveries.incrementAndGet()
                        if (result.error != null) {
                            errors.add(result.error)
                        }
                    }

                    // Rate limiting to avoid overwhelming system
                    if (config.delayBetweenRecoveriesMs > 0) {
                        kotlinx.coroutines.delay(config.delayBetweenRecoveriesMs)
                    }
                }
            }

            val totalDuration = System.currentTimeMillis() - scanStartTime
            logger.info(
                "Recovery scan completed: recovered={}, failed={}, duration={}ms",
                recovered, failed, totalDuration
            )

            RecoveryScanResult(
                scanNumber = totalScans.get(),
                failedWorkflowsFound = failedWorkflows.size,
                workflowsRecovered = recovered,
                workflowsFailed = failed,
                durationMs = totalDuration,
                errors = errors
            )
        } catch (e: Exception) {
            logger.error("Recovery scan failed with exception", e)
            RecoveryScanResult(
                scanNumber = totalScans.get(),
                failedWorkflowsFound = 0,
                workflowsRecovered = 0,
                workflowsFailed = 0,
                durationMs = System.currentTimeMillis() - scanStartTime,
                errors = listOf(RecoveryError(
                    workflowId = "SCAN_ERROR",
                    reason = "Scan exception: ${e.message}",
                    timestamp = Instant.now()
                ))
            )
        }
    }

    /**
     * Attempt recovery of a single failed workflow.
     *
     * @param workflow The failed workflow to recover
     * @return RecoveryAttemptResult indicating success/failure
     */
    private suspend fun attemptRecovery(workflow: WorkflowState): RecoveryAttemptResult {
        logger.debug("Attempting recovery of workflow: {} (status={})",
            workflow.workflowId, workflow.status)

        return try {
            // Check if workflow has exceeded max retries
            val retryCount = workflowRetryAttempts.getOrDefault(workflow.workflowId, 0)
            if (retryCount >= config.maxRetriesPerWorkflow) {
                logger.warn(
                    "Workflow {} exceeded max retries ({}), marking for manual intervention",
                    workflow.workflowId, config.maxRetriesPerWorkflow
                )
                return RecoveryAttemptResult(
                    workflowId = workflow.workflowId,
                    isSuccessful = false,
                    strategy = RecoveryStrategy.MANUAL_INTERVENTION,
                    error = RecoveryError(
                        workflowId = workflow.workflowId,
                        reason = "Max retries (${config.maxRetriesPerWorkflow}) exceeded",
                        timestamp = Instant.now()
                    )
                )
            }

            // Determine recovery strategy based on workflow state
            val strategy = determineRecoveryStrategy(workflow)
            logger.info("Using recovery strategy: {} for workflow: {}",
                strategy, workflow.workflowId)

            // Execute recovery
            val result = executeRecoveryStrategy(workflow, strategy)

            if (result.isSuccessful) {
                logger.info("Successfully recovered workflow: {}", workflow.workflowId)
                workflowRetryAttempts.remove(workflow.workflowId)
            } else {
                workflowRetryAttempts.compute(workflow.workflowId) { _, current ->
                    (current ?: 0) + 1
                }
                logger.warn("Failed to recover workflow: {} (retry {}/{})",
                    workflow.workflowId, retryCount + 1, config.maxRetriesPerWorkflow)
            }

            result
        } catch (e: Exception) {
            logger.error("Exception while recovering workflow: {}", workflow.workflowId, e)
            RecoveryAttemptResult(
                workflowId = workflow.workflowId,
                isSuccessful = false,
                strategy = RecoveryStrategy.MANUAL_INTERVENTION,
                error = RecoveryError(
                    workflowId = workflow.workflowId,
                    reason = "Exception: ${e.message}",
                    timestamp = Instant.now()
                )
            )
        }
    }

    /**
     * Determine the best recovery strategy based on workflow failure.
     */
    private suspend fun determineRecoveryStrategy(workflow: WorkflowState): RecoveryStrategy {
        return when {
            // If failed at specific operation, try resuming from checkpoint
            workflow.failedAtOperation != null && workflow.failedAtOperation!! > 0 -> {
                RecoveryStrategy.RESUME_FROM_CHECKPOINT
            }
            // If error is transient, retry entire workflow
            isTransientError(workflow.errorMessage) -> {
                RecoveryStrategy.RETRY
            }
            // Otherwise, require manual intervention
            else -> {
                RecoveryStrategy.MANUAL_INTERVENTION
            }
        }
    }

    /**
     * Execute the selected recovery strategy.
     */
    private suspend fun executeRecoveryStrategy(
        workflow: WorkflowState,
        strategy: RecoveryStrategy
    ): RecoveryAttemptResult {
        return when (strategy) {
            RecoveryStrategy.RETRY -> {
                // Retry entire workflow from FAILED state
                logger.info("Retrying failed workflow: {}", workflow.workflowId)
                // TODO: Implement workflow retry by transitioning state machine
                RecoveryAttemptResult(
                    workflowId = workflow.workflowId,
                    isSuccessful = false,
                    strategy = strategy,
                    error = RecoveryError(
                        workflowId = workflow.workflowId,
                        reason = "RETRY strategy not yet implemented",
                        timestamp = Instant.now()
                    )
                )
            }

            RecoveryStrategy.RESUME_FROM_CHECKPOINT -> {
                // Resume from last checkpoint
                logger.info("Resuming workflow from checkpoint: {}", workflow.workflowId)
                // TODO: Implement checkpoint-based resume
                RecoveryAttemptResult(
                    workflowId = workflow.workflowId,
                    isSuccessful = false,
                    strategy = strategy,
                    error = RecoveryError(
                        workflowId = workflow.workflowId,
                        reason = "RESUME_FROM_CHECKPOINT strategy not yet implemented",
                        timestamp = Instant.now()
                    )
                )
            }

            RecoveryStrategy.MANUAL_INTERVENTION -> {
                // Mark for manual review - don't retry
                logger.warn("Workflow requires manual intervention: {}", workflow.workflowId)
                RecoveryAttemptResult(
                    workflowId = workflow.workflowId,
                    isSuccessful = false,
                    strategy = strategy,
                    error = RecoveryError(
                        workflowId = workflow.workflowId,
                        reason = "Manual intervention required",
                        timestamp = Instant.now()
                    )
                )
            }

            RecoveryStrategy.ABANDON -> {
                // Log and skip - workflow will not be recovered
                logger.warn("Abandoning workflow recovery: {}", workflow.workflowId)
                RecoveryAttemptResult(
                    workflowId = workflow.workflowId,
                    isSuccessful = false,
                    strategy = strategy
                )
            }
        }
    }

    /**
     * Determine if an error is transient (retry-worthy).
     */
    private fun isTransientError(errorMessage: String?): Boolean {
        if (errorMessage == null) return false
        return errorMessage.contains("timeout", ignoreCase = true) ||
               errorMessage.contains("connection", ignoreCase = true) ||
               errorMessage.contains("temporarily unavailable", ignoreCase = true) ||
               errorMessage.contains("try again", ignoreCase = true)
    }

    /**
     * Get current recovery metrics.
     */
    fun getMetrics(): RecoveryMetrics {
        return RecoveryMetrics(
            totalScans = totalScans.get(),
            totalFailedWorkflowsFound = failedWorkflowsFound.get(),
            totalSuccessfulRecoveries = successfulRecoveries.get(),
            totalFailedRecoveries = failedRecoveries.get(),
            successRate = if (successfulRecoveries.get() + failedRecoveries.get() > 0) {
                (successfulRecoveries.get().toDouble() / (successfulRecoveries.get() + failedRecoveries.get())) * 100
            } else {
                0.0
            },
            workflowsInRetry = workflowRetryAttempts.size
        )
    }

    /**
     * Reset metrics (for testing).
     */
    fun resetMetrics() {
        totalScans.set(0)
        failedWorkflowsFound.set(0)
        successfulRecoveries.set(0)
        failedRecoveries.set(0)
        workflowRetryAttempts.clear()
    }
}

/**
 * Configuration for recovery job.
 */
data class RecoveryJobConfig(
    val maxRetriesPerWorkflow: Int = 3,
    val retryDelayMs: Long = 1000,
    val batchSize: Int = 10,
    val delayBetweenRecoveriesMs: Long = 100,
    val scanIntervalMs: Long = 30000
)

/**
 * Recovery strategies for different failure types.
 */
enum class RecoveryStrategy {
    /** Retry entire workflow from FAILED state */
    RETRY,

    /** Resume from last successful checkpoint */
    RESUME_FROM_CHECKPOINT,

    /** Require manual intervention (support ticket) */
    MANUAL_INTERVENTION,

    /** Abandon recovery, skip this workflow */
    ABANDON
}

/**
 * Result of a single recovery attempt.
 */
data class RecoveryAttemptResult(
    val workflowId: String,
    val isSuccessful: Boolean,
    val strategy: RecoveryStrategy,
    val error: RecoveryError? = null
)

/**
 * Details of a recovery error.
 */
data class RecoveryError(
    val workflowId: String,
    val reason: String,
    val timestamp: Instant
)

/**
 * Result of a recovery scan operation.
 */
data class RecoveryScanResult(
    val scanNumber: Long,
    val failedWorkflowsFound: Int,
    val workflowsRecovered: Int,
    val workflowsFailed: Int,
    val durationMs: Long,
    val errors: List<RecoveryError>
) {
    fun getTotalProcessed(): Int = workflowsRecovered + workflowsFailed
    fun getRecoveryRate(): Double = if (getTotalProcessed() > 0) {
        (workflowsRecovered.toDouble() / getTotalProcessed()) * 100
    } else {
        0.0
    }
}

/**
 * Metrics for recovery job performance.
 */
data class RecoveryMetrics(
    val totalScans: Long,
    val totalFailedWorkflowsFound: Long,
    val totalSuccessfulRecoveries: Long,
    val totalFailedRecoveries: Long,
    val successRate: Double,
    val workflowsInRetry: Int
)
