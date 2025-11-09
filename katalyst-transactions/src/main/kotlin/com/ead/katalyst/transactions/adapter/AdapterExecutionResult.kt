package com.ead.katalyst.transactions.adapter

import com.ead.katalyst.transactions.hooks.TransactionPhase

/**
 * Result of a single adapter execution during a transaction phase.
 *
 * Tracks whether the adapter succeeded or failed, the exception if any,
 * and the execution duration for monitoring and debugging.
 */
data class AdapterExecutionResult(
    /** The adapter that executed */
    val adapter: TransactionAdapter,
    /** The transaction phase during which it executed */
    val phase: TransactionPhase,
    /** True if adapter executed successfully */
    val success: Boolean,
    /** Exception thrown, if any */
    val error: Exception? = null,
    /** Execution duration in milliseconds */
    val duration: Long
)

/**
 * Results of all adapters executing for a single transaction phase.
 *
 * Provides:
 * - Individual execution results for each adapter
 * - Convenience methods to check for critical failures
 * - Summary of successes and failures
 */
data class PhaseExecutionResults(
    /** The transaction phase these results are for */
    val phase: TransactionPhase,
    /** Results for each adapter that executed */
    val results: List<AdapterExecutionResult>
) {
    /**
     * Check if any critical adapter failed
     */
    fun hasCriticalFailures(): Boolean {
        return results.any { !it.success && it.adapter.isCritical() }
    }

    /**
     * Get all critical adapter failures
     */
    fun getCriticalFailures(): List<AdapterExecutionResult> {
        return results.filter { !it.success && it.adapter.isCritical() }
    }

    /**
     * Get all non-critical failures (for logging/monitoring)
     */
    fun getNonCriticalFailures(): List<AdapterExecutionResult> {
        return results.filter { !it.success && !it.adapter.isCritical() }
    }

    /**
     * Get all successful executions
     */
    fun getSuccesses(): List<AdapterExecutionResult> {
        return results.filter { it.success }
    }

    /**
     * Get total execution duration for this phase
     */
    fun totalDuration(): Long {
        return results.sumOf { it.duration }
    }

    /**
     * Get summary of execution
     */
    fun getSummary(): String {
        val totalAdapters = results.size
        val successCount = getSuccesses().size
        val failureCount = results.size - successCount
        val criticalFailures = getCriticalFailures().size
        val duration = totalDuration()

        return "Phase: $phase | Total: $totalAdapters | Success: $successCount | Failure: $failureCount | Critical: $criticalFailures | Duration: ${duration}ms"
    }
}
