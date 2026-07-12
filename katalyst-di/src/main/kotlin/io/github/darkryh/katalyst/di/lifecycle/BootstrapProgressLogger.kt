package io.github.darkryh.katalyst.di.lifecycle

import org.slf4j.LoggerFactory

/**
 * Tracks and displays bootstrap lifecycle progress in real-time.
 *
 * **Purpose**: Show structured progress through each initialization lifecycle
 * with real-time status updates (⏳ running, ✓ completed, ✗ failed).
 *
 * **Display Format**:
 * ```
 * ╔════════════════════════════════════════════════════════════╗
 * ║ LIFECYCLE: Bean Container Bootstrap                        ║
 * ║ Reference: LIFECYCLE_BEAN_CONTAINER_BOOTSTRAP              ║
 * ╚════════════════════════════════════════════════════════════╝
 * ⏳ Scanning packages...
 * ✓  Completed in 234ms
 * ```
 */
internal class BootstrapProgressLogger {
    private val logger = LoggerFactory.getLogger("BootstrapProgressLogger")

    data class LifecycleInfo(
        val lifecycle: BootstrapLifecycle,
        var startTime: Long = 0L,
        var endTime: Long = 0L,
        var status: PhaseStatus = PhaseStatus.PENDING,
        var message: String? = null
    )

    enum class PhaseStatus {
        PENDING,      // Not started
        RUNNING,      // Currently executing
        COMPLETED,    // Finished successfully
        FAILED,       // Failed with error
        SKIPPED       // Skipped/not applicable
    }

    private val lifecycles = BootstrapLifecycle.entries
        .map { lifecycle -> LifecycleInfo(lifecycle = lifecycle) }
        .toMutableList()
    private val lifecycleStartTimes = mutableMapOf<BootstrapLifecycle, Long>()

    init {
        require(lifecycles.isNotEmpty()) { "Bootstrap lifecycle definitions cannot be empty" }
    }

    /**
     * Mark a lifecycle as started.
     */
    fun startLifecycle(lifecycle: BootstrapLifecycle) {
        startLifecycle(lifecycle, compact = false)
    }

    fun startLifecycle(lifecycle: BootstrapLifecycle, compact: Boolean = false) {
        val lifecycleInfo = lifecycles.firstOrNull { it.lifecycle == lifecycle } ?: return

        val startTime = System.currentTimeMillis()
        lifecycleStartTimes[lifecycle] = startTime

        lifecycleInfo.startTime = startTime
        lifecycleInfo.status = PhaseStatus.RUNNING

        logger.info("")
        if (compact) {
            logger.info("▶ LIFECYCLE {}: {}", lifecycle.lifecycleRef, lifecycle.displayName)
            logger.info("⏳ {}", lifecycle.description)
        } else {
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ LIFECYCLE: {} ║", padRight(lifecycle.displayName, 39))
            logger.info("║ Reference: {} ║", padRight(lifecycle.lifecycleRef, 39))
            logger.info("║ {}", padRight(lifecycle.description, 50) + "║")
            logger.info("╚════════════════════════════════════════════════════╝")
            logger.info("⏳ Running...")
        }
    }

    /**
     * Mark a lifecycle as completed.
     */
    fun completeLifecycle(lifecycle: BootstrapLifecycle, message: String? = null) {
        val lifecycleInfo = lifecycles.firstOrNull { it.lifecycle == lifecycle } ?: return

        val endTime = System.currentTimeMillis()
        lifecycleInfo.endTime = endTime
        lifecycleInfo.status = PhaseStatus.COMPLETED
        lifecycleInfo.message = message

        val duration = endTime - lifecycleInfo.startTime
        logger.info("✓  Completed in {}ms", duration)
        message?.let { logger.info("   {}", it) }
    }

    /**
     * Mark a lifecycle as failed.
     */
    fun failLifecycle(lifecycle: BootstrapLifecycle, error: Throwable? = null) {
        val lifecycleInfo = lifecycles.firstOrNull { it.lifecycle == lifecycle } ?: return

        val endTime = System.currentTimeMillis()
        lifecycleInfo.endTime = endTime
        lifecycleInfo.status = PhaseStatus.FAILED
        lifecycleInfo.message = error?.message

        val duration = endTime - lifecycleInfo.startTime
        logger.error("✗  Failed after {}ms", duration)
        error?.let { logger.error("   Error: {}", it.message) }
    }

    /**
     * Mark a lifecycle as skipped.
     */
    fun skipLifecycle(lifecycle: BootstrapLifecycle, reason: String? = null) {
        val lifecycleInfo = lifecycles.firstOrNull { it.lifecycle == lifecycle } ?: return

        lifecycleInfo.status = PhaseStatus.SKIPPED
        lifecycleInfo.message = reason

        logger.info("⊘  Skipped")
        reason?.let { logger.info("   Reason: {}", it) }
    }

    /**
     * Display overall progress summary.
     */
    fun displayProgressSummary(includePending: Boolean = true) {
        val lifecyclesToDisplay = if (includePending) {
            lifecycles
        } else {
            lifecycles.filter { it.status != PhaseStatus.PENDING }
        }

        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ BOOTSTRAP PROGRESS SUMMARY                         ║")
        logger.info("╚════════════════════════════════════════════════════╝")

        lifecyclesToDisplay.forEach { lifecycle ->
            val statusIcon = when (lifecycle.status) {
                PhaseStatus.COMPLETED -> "✓"
                PhaseStatus.FAILED -> "✗"
                PhaseStatus.SKIPPED -> "⊘"
                PhaseStatus.RUNNING -> "⏳"
                PhaseStatus.PENDING -> "○"
            }

            val duration = if (lifecycle.endTime > 0 && lifecycle.startTime > 0) {
                "${lifecycle.endTime - lifecycle.startTime}ms"
            } else {
                "-"
            }

            logger.info(
                "│ {} {}: {} [{}] │",
                statusIcon,
                padRight(lifecycle.lifecycle.lifecycleRef, 34),
                padRight(lifecycle.lifecycle.displayName, 26),
                duration
            )
        }
    }

    /**
     * Get total bootstrap time in milliseconds.
     */
    fun getTotalBootstrapTime(): Long {
        val firstStart = lifecycles.filter { it.startTime > 0 }.minOfOrNull { it.startTime } ?: return 0L
        val lastEnd = lifecycles.filter { it.endTime > 0 }.maxOfOrNull { it.endTime } ?: return 0L
        return if (lastEnd > firstStart) lastEnd - firstStart else 0L
    }

    /**
     * Get all lifecycles and their status.
     */
    fun getLifecycles(): List<LifecycleInfo> = lifecycles.toList()

    /**
     * Clear all lifecycle data (for testing).
     */
    fun clear() {
        lifecycles.forEach {
            it.startTime = 0L
            it.endTime = 0L
            it.status = PhaseStatus.PENDING
            it.message = null
        }
        lifecycleStartTimes.clear()
    }

    private fun padRight(text: String, width: Int): String {
        return if (text.length >= width) {
            text.substring(0, width)
        } else {
            text + " ".repeat(width - text.length)
        }
    }

}

/**
 * Global instance for bootstrap progress tracking.
 */
object BootstrapProgress {
    private val logger = BootstrapProgressLogger()

    internal fun startLifecycle(lifecycle: BootstrapLifecycle) = logger.startLifecycle(lifecycle)
    internal fun startLifecycleCompact(lifecycle: BootstrapLifecycle) = logger.startLifecycle(lifecycle, compact = true)

    internal fun completeLifecycle(lifecycle: BootstrapLifecycle, message: String? = null) =
        logger.completeLifecycle(lifecycle, message)

    internal fun failLifecycle(lifecycle: BootstrapLifecycle, error: Throwable? = null) =
        logger.failLifecycle(lifecycle, error)

    internal fun skipLifecycle(lifecycle: BootstrapLifecycle, reason: String? = null) =
        logger.skipLifecycle(lifecycle, reason)

    fun displayProgressSummary(includePending: Boolean = true) =
        logger.displayProgressSummary(includePending)

    fun getTotalBootstrapTime(): Long = logger.getTotalBootstrapTime()

    internal fun getLifecycles(): List<BootstrapProgressLogger.LifecycleInfo> = logger.getLifecycles()

    fun clear() = logger.clear()
}

internal enum class BootstrapLifecycle(
    val lifecycleRef: String,
    val displayName: String,
    val description: String
) {
    // Names are ENGINE-NEUTRAL on purpose: the bean container is an abstraction
    // (KatalystBeanEngine — Koin is only the default implementation), and the HTTP engine is
    // selectable (Netty/Jetty/CIO). Phase wording must survive an engine swap unchanged.
    BEAN_CONTAINER_BOOTSTRAP(
        lifecycleRef = "LIFECYCLE_BEAN_CONTAINER_BOOTSTRAP",
        displayName = "Bean Container Bootstrap",
        description = "Starting the dependency-injection engine and loading core bean modules"
    ),
    COMPONENT_DISCOVERY_REGISTRATION(
        lifecycleRef = "LIFECYCLE_COMPONENT_DISCOVERY_REGISTRATION",
        displayName = "Component Discovery & Registration",
        description = "Scanning packages, validating the dependency graph, and registering components"
    ),
    DATABASE_SCHEMA_INITIALIZATION(
        lifecycleRef = "LIFECYCLE_DATABASE_SCHEMA_INITIALIZATION",
        displayName = "Database Schema Initialization",
        description = "Connecting to the database and applying the configured schema policy"
    ),
    TRANSACTION_ADAPTER_REGISTRATION(
        lifecycleRef = "LIFECYCLE_TRANSACTION_ADAPTER_REGISTRATION",
        displayName = "Transaction Adapter Registration",
        description = "Wiring transaction adapters for persistence and events"
    ),
    PRE_START_INITIALIZERS(
        lifecycleRef = "LIFECYCLE_PRE_START_INITIALIZERS",
        displayName = "Pre-Start Initializers",
        description = "Running startup validators and pre-start hooks"
    ),
    HTTP_SERVER_STARTUP(
        lifecycleRef = "LIFECYCLE_HTTP_SERVER_STARTUP",
        displayName = "HTTP Server Startup",
        description = "Starting the HTTP engine and binding the configured host and port"
    ),
    RUNTIME_READY_INITIALIZERS(
        lifecycleRef = "LIFECYCLE_RUNTIME_READY_INITIALIZERS",
        displayName = "Runtime-Ready Initializers",
        description = "Activating the scheduler and runtime hooks now that the server accepts traffic"
    )
}
