package com.ead.katalyst.di.lifecycle

import org.slf4j.LoggerFactory

/**
 * Aggregates and displays startup warnings in a structured table format.
 *
 * **Purpose**: Consolidate optional feature warnings into a single visual table
 * instead of burying them in logs.
 *
 * **Warning Categories**:
 * - Missing features (Ktor modules, database migrations, etc.)
 * - Configuration warnings (suboptimal settings, non-production values)
 * - Optional features not enabled
 *
 * **Display**: ASCII table with warning count, category, and helpful hints
 */
class StartupWarningsAggregator {
    private val logger = LoggerFactory.getLogger("StartupWarningsAggregator")
    private val warnings = mutableListOf<Warning>()

    data class Warning(
        val category: String,
        val message: String,
        val severity: WarningSeverity = WarningSeverity.INFO,
        val hint: String? = null
    )

    enum class WarningSeverity {
        INFO,      // Optional feature not enabled
        WARNING,   // Configuration issue or degraded feature
        CRITICAL   // Missing required component (but not startup-blocking)
    }

    /**
     * Add a warning to the aggregator.
     */
    fun addWarning(
        category: String,
        message: String,
        severity: WarningSeverity = WarningSeverity.INFO,
        hint: String? = null
    ) {
        warnings.add(Warning(category, message, severity, hint))
    }

    /**
     * Display all aggregated warnings as a structured table.
     * Only displays if warnings exist.
     */
    fun displayIfPresent() {
        if (warnings.isEmpty()) {
            logger.debug("No startup warnings to display")
            return
        }

        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ ⚠  STARTUP WARNINGS & ALERTS ({} total)          ║", warnings.size)
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("")

        // Group by severity
        val criticalWarnings = warnings.filter { it.severity == WarningSeverity.CRITICAL }
        val warningItems = warnings.filter { it.severity == WarningSeverity.WARNING }
        val infoItems = warnings.filter { it.severity == WarningSeverity.INFO }

        // Display CRITICAL warnings first
        if (criticalWarnings.isNotEmpty()) {
            logger.warn("┌─ CRITICAL ITEMS ({}) ─────────────────────────────┐", criticalWarnings.size)
            criticalWarnings.forEachIndexed { index, warning ->
                logger.warn("│ [{}] {} - {}", index + 1, warning.category, warning.message)
                warning.hint?.let { logger.warn("│     💡 {}", it) }
            }
            logger.warn("└──────────────────────────────────────────────────────┘")
        }

        // Display WARNINGS
        if (warningItems.isNotEmpty()) {
            logger.warn("┌─ WARNINGS ({}) ───────────────────────────────────┐", warningItems.size)
            warningItems.forEachIndexed { index, warning ->
                logger.warn("│ [{}] {} - {}", index + 1, warning.category, warning.message)
                warning.hint?.let { logger.warn("│     💡 {}", it) }
            }
            logger.warn("└──────────────────────────────────────────────────────┘")
        }

        // Display INFO warnings
        if (infoItems.isNotEmpty()) {
            logger.info("┌─ INFO ({}) ────────────────────────────────────────┐", infoItems.size)
            infoItems.forEachIndexed { index, warning ->
                logger.info("│ [{}] {} - {}", index + 1, warning.category, warning.message)
                warning.hint?.let { logger.info("│     💡 {}", it) }
            }
            logger.info("└──────────────────────────────────────────────────────┘")
        }

        logger.info("")
    }

    /**
     * Get all warnings as a list (for testing or programmatic access).
     */
    fun getWarnings(): List<Warning> = warnings.toList()

    /**
     * Clear all warnings.
     */
    fun clear() {
        warnings.clear()
    }

    /**
     * Get warning count by severity.
     */
    fun getCountBySeverity(severity: WarningSeverity): Int =
        warnings.count { it.severity == severity }
}

// Global instance for use throughout the application
object StartupWarnings {
    private val aggregator = StartupWarningsAggregator()

    fun add(
        category: String,
        message: String,
        severity: StartupWarningsAggregator.WarningSeverity = StartupWarningsAggregator.WarningSeverity.INFO,
        hint: String? = null
    ) = aggregator.addWarning(category, message, severity, hint)

    fun display() = aggregator.displayIfPresent()

    fun get(): List<StartupWarningsAggregator.Warning> = aggregator.getWarnings()

    fun clear() = aggregator.clear()

    fun countCritical() = aggregator.getCountBySeverity(StartupWarningsAggregator.WarningSeverity.CRITICAL)

    fun countWarning() = aggregator.getCountBySeverity(StartupWarningsAggregator.WarningSeverity.WARNING)

    fun countInfo() = aggregator.getCountBySeverity(StartupWarningsAggregator.WarningSeverity.INFO)
}
