package com.ead.katalyst.transactions.workflow

import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Health monitoring for workflow recovery system.
 *
 * Tracks:
 * - Overall success rates
 * - Alert thresholds for concerning metrics
 * - Recovery performance trends
 * - System health status
 *
 * **Alert Conditions**:
 * - Success rate below threshold (default: 70%)
 * - Too many failed workflows accumulating (default: >100)
 * - Recovery job not running
 * - Too many consecutive errors
 *
 * **Usage**:
 * ```kotlin
 * val monitor = RecoveryHealthMonitor(scheduler)
 * monitor.setAlertCallback { alert ->
 *     emailService.sendAlert("Recovery Alert", alert.message)
 *     slackService.postAlert(alert)
 * }
 *
 * scheduler.start()
 * // Monitor runs periodically to check health
 * ```
 */
class RecoveryHealthMonitor(
    private val scheduler: RecoveryJobScheduler,
    val config: HealthMonitorConfig = HealthMonitorConfig()
) {
    private val logger = LoggerFactory.getLogger(RecoveryHealthMonitor::class.java)

    private var lastHealthCheck: Instant? = null
    private val alerts = mutableListOf<HealthAlert>()
    private var alertCallback: ((HealthAlert) -> Unit)? = null

    /**
     * Perform a health check.
     *
     * Called periodically (e.g., every 5 minutes).
     *
     * @return HealthCheckResult with status and any alerts
     */
    fun performHealthCheck(): HealthCheckResult {
        val status = scheduler.getStatus()
        val metrics = status.metrics
        val timestamp = Instant.now()
        lastHealthCheck = timestamp

        val issues = mutableListOf<HealthIssue>()

        // Check if scheduler is running
        if (!status.isRunning) {
            issues.add(HealthIssue(
                severity = HealthSeverity.CRITICAL,
                component = "RecoveryScheduler",
                message = "Recovery scheduler is not running"
            ))
        }

        // Check consecutive errors
        if (status.consecutiveErrors >= status.maxConsecutiveErrors) {
            issues.add(HealthIssue(
                severity = HealthSeverity.CRITICAL,
                component = "RecoveryScheduler",
                message = "Too many consecutive errors (${status.consecutiveErrors}/${status.maxConsecutiveErrors})"
            ))
        } else if (status.consecutiveErrors > 0) {
            issues.add(HealthIssue(
                severity = HealthSeverity.WARNING,
                component = "RecoveryScheduler",
                message = "Consecutive errors detected (${status.consecutiveErrors})"
            ))
        }

        // Check success rate
        if (metrics.totalSuccessfulRecoveries + metrics.totalFailedRecoveries > 0) {
            if (metrics.successRate < config.minSuccessRatePercent) {
                issues.add(HealthIssue(
                    severity = HealthSeverity.WARNING,
                    component = "RecoveryEffectiveness",
                    message = "Low recovery success rate: ${String.format("%.1f%%", metrics.successRate)} " +
                              "(threshold: ${config.minSuccessRatePercent}%)"
                ))
            }
        }

        // Check if too many workflows are in retry state
        if (metrics.workflowsInRetry > config.maxWorkflowsInRetry) {
            issues.add(HealthIssue(
                severity = HealthSeverity.WARNING,
                component = "RecoveryBacklog",
                message = "Many workflows in retry state: ${metrics.workflowsInRetry} " +
                          "(threshold: ${config.maxWorkflowsInRetry})"
            ))
        }

        // Check if failure rate is increasing
        if (metrics.totalFailedRecoveries > config.maxFailedRecoveriesThreshold) {
            issues.add(HealthIssue(
                severity = HealthSeverity.WARNING,
                component = "RecoveryFailures",
                message = "High number of failed recoveries: ${metrics.totalFailedRecoveries} " +
                          "(threshold: ${config.maxFailedRecoveriesThreshold})"
            ))
        }

        val overallHealth = determineHealthStatus(issues)

        // Generate alerts for critical/warning issues
        issues.forEach { issue ->
            if (issue.severity == HealthSeverity.CRITICAL ||
                (issue.severity == HealthSeverity.WARNING && config.alertOnWarnings)) {
                val alert = HealthAlert(
                    timestamp = timestamp,
                    severity = issue.severity,
                    component = issue.component,
                    message = issue.message,
                    metrics = metrics
                )
                alerts.add(alert)
                alertCallback?.invoke(alert)
                logger.warn("Health Alert [{}]: {}", issue.severity, issue.message)
            }
        }

        logger.info("Health check completed: status={}, issues={}", overallHealth, issues.size)

        return HealthCheckResult(
            timestamp = timestamp,
            status = overallHealth,
            issues = issues,
            metrics = metrics
        )
    }

    /**
     * Set callback for health alerts.
     *
     * @param callback Called when alerts are generated
     */
    fun setAlertCallback(callback: (HealthAlert) -> Unit) {
        this.alertCallback = callback
    }

    /**
     * Get recent alerts.
     *
     * @param maxResults Maximum number of alerts to return
     * @return List of recent alerts
     */
    fun getRecentAlerts(maxResults: Int = 10): List<HealthAlert> {
        return alerts.takeLast(maxResults)
    }

    /**
     * Clear alert history.
     */
    fun clearAlerts() {
        alerts.clear()
    }

    /**
     * Get overall system health status.
     */
    fun getHealthStatus(): HealthCheckResult {
        return performHealthCheck()
    }

    /**
     * Generate a detailed health report.
     */
    fun generateHealthReport(): String = buildString {
        val status = scheduler.getStatus()
        val metrics = status.metrics
        val lastCheck = lastHealthCheck ?: Instant.now()

        appendLine("=== Workflow Recovery Health Report ===")
        appendLine("Generated: $lastCheck")
        appendLine()

        appendLine("Scheduler Status:")
        appendLine("  Running: ${status.isRunning}")
        appendLine("  Scan Interval: ${status.scanIntervalMs}ms")
        appendLine("  Consecutive Errors: ${status.consecutiveErrors}/${status.maxConsecutiveErrors}")
        appendLine()

        appendLine("Recovery Metrics:")
        appendLine("  Total Scans: ${metrics.totalScans}")
        appendLine("  Workflows Found: ${metrics.totalFailedWorkflowsFound}")
        appendLine("  Successful Recoveries: ${metrics.totalSuccessfulRecoveries}")
        appendLine("  Failed Recoveries: ${metrics.totalFailedRecoveries}")
        appendLine("  Success Rate: ${String.format("%.2f%%", metrics.successRate)}")
        appendLine("  Workflows in Retry: ${metrics.workflowsInRetry}")
        appendLine()

        if (alerts.isNotEmpty()) {
            appendLine("Recent Alerts (last ${alerts.size}):")
            alerts.takeLast(5).forEach { alert ->
                appendLine("  [${alert.severity}] ${alert.timestamp}: ${alert.message}")
            }
        } else {
            appendLine("No recent alerts")
        }
    }

    /**
     * Determine overall health status from issues.
     */
    private fun determineHealthStatus(issues: List<HealthIssue>): HealthStatus {
        return when {
            issues.any { it.severity == HealthSeverity.CRITICAL } -> HealthStatus.UNHEALTHY
            issues.any { it.severity == HealthSeverity.WARNING } -> HealthStatus.DEGRADED
            else -> HealthStatus.HEALTHY
        }
    }
}

/**
 * Configuration for health monitoring.
 */
data class HealthMonitorConfig(
    val minSuccessRatePercent: Double = 70.0,
    val maxWorkflowsInRetry: Int = 50,
    val maxFailedRecoveriesThreshold: Int = 100,
    val alertOnWarnings: Boolean = true,
    val healthCheckIntervalMs: Long = 300000  // 5 minutes
)

/**
 * Overall health status.
 */
enum class HealthStatus {
    /** All systems operating normally */
    HEALTHY,

    /** Some issues detected, but still operational */
    DEGRADED,

    /** Critical issues detected, immediate action needed */
    UNHEALTHY
}

/**
 * Severity level of a health issue.
 */
enum class HealthSeverity {
    /** Informational message */
    INFO,

    /** Warning - attention needed */
    WARNING,

    /** Critical - immediate action required */
    CRITICAL
}

/**
 * A single health issue identified during health check.
 */
data class HealthIssue(
    val severity: HealthSeverity,
    val component: String,
    val message: String
)

/**
 * An alert generated due to health issues.
 */
data class HealthAlert(
    val timestamp: Instant,
    val severity: HealthSeverity,
    val component: String,
    val message: String,
    val metrics: RecoveryMetrics
)

/**
 * Result of a health check.
 */
data class HealthCheckResult(
    val timestamp: Instant,
    val status: HealthStatus,
    val issues: List<HealthIssue>,
    val metrics: RecoveryMetrics
) {
    fun isHealthy(): Boolean = status == HealthStatus.HEALTHY
    fun isDegraded(): Boolean = status == HealthStatus.DEGRADED
    fun isUnhealthy(): Boolean = status == HealthStatus.UNHEALTHY

    override fun toString(): String = buildString {
        append("HealthCheck(status=$status, issues=${issues.size}")
        if (issues.isNotEmpty()) {
            append(", critical=${issues.count { it.severity == HealthSeverity.CRITICAL }}")
            append(", warnings=${issues.count { it.severity == HealthSeverity.WARNING }}")
        }
        append(")")
    }
}
