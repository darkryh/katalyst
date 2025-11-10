package com.ead.katalyst.transactions.metrics

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interface for exporting transaction metrics to external systems.
 *
 * Implementations can export to monitoring systems, logging, databases, etc.
 *
 * **Examples:**
 * - LoggingMetricsExporter - Export metrics as logs
 * - PrometheusMetricsExporter - Export to Prometheus
 * - DatadogMetricsExporter - Export to Datadog
 * - ElasticsearchMetricsExporter - Export to Elasticsearch
 */
interface MetricsExporter {
    /**
     * Export transaction metrics.
     *
     * @param metrics The metrics to export
     */
    suspend fun export(metrics: TransactionMetrics)
}

/**
 * Logs transaction metrics at INFO level.
 *
 * Output includes:
 * - Transaction ID and workflow ID
 * - Status (COMMITTED, FAILED, TIMEOUT, etc)
 * - Duration and retry count
 * - Operation and event counts
 * - Adapter execution timings
 * - Errors encountered (if any)
 *
 * **Example Output:**
 * ```
 * Transaction: tx-abc123 Status: COMMITTED Duration: 145ms Operations: 5 Events: 2 Adapters: 3
 *   - EventsTransactionAdapter: SUCCESS (32ms)
 *   - PersistenceTransactionAdapter: SUCCESS (78ms)
 * ```
 */
class LoggingMetricsExporter : MetricsExporter {
    companion object {
        private val logger = LoggerFactory.getLogger(LoggingMetricsExporter::class.java)
    }

    override suspend fun export(metrics: TransactionMetrics) {
        logger.info(
            "Transaction: {} Status: {} Duration: {}ms Operations: {} Events: {} Retries: {} Adapters: {} Errors: {}",
            metrics.transactionId,
            metrics.status,
            metrics.duration?.inWholeMilliseconds ?: "pending",
            metrics.operationCount,
            metrics.eventCount,
            metrics.retryCount,
            metrics.adapterExecutions.size,
            metrics.errors.size
        )

        // Log workflow ID if present
        if (metrics.workflowId != null) {
            logger.debug("  Workflow ID: {}", metrics.workflowId)
        }

        // Log adapter details
        if (metrics.adapterExecutions.isNotEmpty()) {
            logger.debug("  Adapter Executions:")
            metrics.adapterExecutions.forEach { adapter ->
                logger.debug(
                    "    - {}: {} ({}ms) [{}]",
                    adapter.adapterName,
                    if (adapter.success) "SUCCESS" else "FAILED",
                    adapter.duration?.inWholeMilliseconds ?: "pending",
                    adapter.phase
                )
                if (adapter.error != null) {
                    logger.debug(
                        "      Error: {} - {}",
                        adapter.error!!::class.simpleName,
                        adapter.error!!.message
                    )
                }
            }
        }

        // Log errors with severity
        if (metrics.errors.isNotEmpty()) {
            logger.warn("  Transaction Errors ({}):", metrics.errors.size)
            metrics.errors.forEach { error ->
                val severity = if (error.isRetryable) "RETRYABLE" else "FATAL"
                logger.warn(
                    "    - [{}] Phase: {} Error: {} - {}",
                    severity,
                    error.phase,
                    error.exceptionClassName,
                    error.message
                )
                logger.debug("      Stack Trace:\n{}", error.stackTrace)
            }
        }

        // Log success summary
        if (metrics.status == TransactionStatus.COMMITTED && metrics.errors.isEmpty()) {
            logger.info(
                "  ✓ Transaction completed successfully in {}ms",
                metrics.duration?.inWholeMilliseconds ?: 0
            )
        }
    }
}

/**
 * Registry for managing multiple metrics exporters.
 *
 * Allows registering multiple exporters that all receive metrics.
 *
 * **Usage:**
 * ```kotlin
 * val registry = MetricsExporterRegistry()
 * registry.registerExporter(LoggingMetricsExporter())
 * registry.registerExporter(PrometheusMetricsExporter())
 *
 * // All exporters will receive metrics
 * registry.exportMetrics(metrics)
 * ```
 */
class MetricsExporterRegistry {
    private val exporters = CopyOnWriteArrayList<MetricsExporter>()

    /**
     * Register a metrics exporter.
     *
     * @param exporter The exporter to register
     */
    fun registerExporter(exporter: MetricsExporter) {
        exporters.add(exporter)
    }

    /**
     * Unregister a metrics exporter.
     *
     * @param exporter The exporter to unregister
     */
    fun unregisterExporter(exporter: MetricsExporter) {
        exporters.remove(exporter)
    }

    /**
     * Export metrics to all registered exporters.
     *
     * Exporters are called sequentially. If one fails, others still execute.
     *
     * @param metrics The metrics to export
     */
    suspend fun exportMetrics(metrics: TransactionMetrics) {
        exporters.forEach { exporter ->
            try {
                exporter.export(metrics)
            } catch (e: Exception) {
                LoggerFactory.getLogger(MetricsExporterRegistry::class.java).warn(
                    "Error exporting metrics to {} for transaction {}",
                    exporter::class.simpleName,
                    metrics.transactionId,
                    e
                )
            }
        }
    }

    /**
     * Get all registered exporters.
     *
     * @return List of registered exporters
     */
    fun getExporters(): List<MetricsExporter> = exporters.toList()

    /**
     * Clear all registered exporters.
     */
    fun clearExporters() {
        exporters.clear()
    }
}

/**
 * Metrics exporter that formats metrics as structured JSON for log aggregation systems.
 *
 * Suitable for centralized logging systems like ELK Stack, Splunk, etc.
 */
class JsonMetricsExporter : MetricsExporter {
    companion object {
        private val logger = LoggerFactory.getLogger(JsonMetricsExporter::class.java)
    }

    override suspend fun export(metrics: TransactionMetrics) {
        val json = buildString {
            append("{")
            append("\"transactionId\":\"${metrics.transactionId}\",")
            append("\"status\":\"${metrics.status}\",")
            append("\"duration\":${metrics.duration?.inWholeMilliseconds ?: 0},")
            append("\"operationCount\":${metrics.operationCount},")
            append("\"eventCount\":${metrics.eventCount},")
            append("\"retryCount\":${metrics.retryCount},")
            append("\"errorCount\":${metrics.errors.size},")
            append("\"adapterCount\":${metrics.adapterExecutions.size}")
            if (metrics.workflowId != null) {
                append(",\"workflowId\":\"${metrics.workflowId}\"")
            }
            append("}")
        }
        logger.info("metrics:{}", json)
    }
}
