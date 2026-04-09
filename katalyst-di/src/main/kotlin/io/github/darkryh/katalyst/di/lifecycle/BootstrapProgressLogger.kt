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
 * ║ LIFECYCLE: Koin DI Bootstrap                               ║
 * ║ Reference: LIFECYCLE_KOIN_DI_BOOTSTRAP                     ║
 * ╚════════════════════════════════════════════════════════════╝
 * ⏳ Scanning packages...
 * ✓  Completed in 234ms
 * ```
 */
class BootstrapProgressLogger {
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
     * Get lifecycle duration in milliseconds.
     */
    fun getLifecycleDuration(lifecycle: BootstrapLifecycle): Long {
        val lifecycleInfo = lifecycles.firstOrNull { it.lifecycle == lifecycle } ?: return 0L
        return if (lifecycleInfo.endTime > 0 && lifecycleInfo.startTime > 0) {
            lifecycleInfo.endTime - lifecycleInfo.startTime
        } else {
            0L
        }
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

    fun startLifecycle(lifecycle: BootstrapLifecycle) = logger.startLifecycle(lifecycle)
    fun startLifecycleCompact(lifecycle: BootstrapLifecycle) = logger.startLifecycle(lifecycle, compact = true)

    fun completeLifecycle(lifecycle: BootstrapLifecycle, message: String? = null) =
        logger.completeLifecycle(lifecycle, message)

    fun failLifecycle(lifecycle: BootstrapLifecycle, error: Throwable? = null) =
        logger.failLifecycle(lifecycle, error)

    fun skipLifecycle(lifecycle: BootstrapLifecycle, reason: String? = null) =
        logger.skipLifecycle(lifecycle, reason)

    fun displayProgressSummary(includePending: Boolean = true) =
        logger.displayProgressSummary(includePending)

    fun getTotalBootstrapTime(): Long = logger.getTotalBootstrapTime()

    fun getLifecycleDuration(lifecycle: BootstrapLifecycle): Long = logger.getLifecycleDuration(lifecycle)

    fun getLifecycles(): List<BootstrapProgressLogger.LifecycleInfo> = logger.getLifecycles()

    fun clear() = logger.clear()
}

enum class BootstrapLifecycle(
    val lifecycleRef: String,
    val displayName: String,
    val description: String
) {
    KOIN_DI_BOOTSTRAP(
        lifecycleRef = "LIFECYCLE_KOIN_DI_BOOTSTRAP",
        displayName = "Koin DI Bootstrap",
        description = "Loading modules and starting DI context"
    ),
    COMPONENT_DISCOVERY_REGISTRATION(
        lifecycleRef = "LIFECYCLE_COMPONENT_DISCOVERY_REGISTRATION",
        displayName = "Component Discovery & Registration",
        description = "Auto-discovering and validating components"
    ),
    DATABASE_SCHEMA_INITIALIZATION(
        lifecycleRef = "LIFECYCLE_DATABASE_SCHEMA_INITIALIZATION",
        displayName = "Database Schema Initialization",
        description = "Creating database schema"
    ),
    TRANSACTION_ADAPTER_REGISTRATION(
        lifecycleRef = "LIFECYCLE_TRANSACTION_ADAPTER_REGISTRATION",
        displayName = "Transaction Adapter Registration",
        description = "Registering transaction adapters"
    ),
    PRE_START_INITIALIZERS(
        lifecycleRef = "LIFECYCLE_PRE_START_INITIALIZERS",
        displayName = "Pre-Start Initializers",
        description = "Running startup validators and pre-start hooks"
    ),
    KTOR_ENGINE_STARTUP(
        lifecycleRef = "LIFECYCLE_KTOR_ENGINE_STARTUP",
        displayName = "Ktor Engine Startup",
        description = "Starting HTTP server"
    ),
    RUNTIME_READY_INITIALIZERS(
        lifecycleRef = "LIFECYCLE_RUNTIME_READY_INITIALIZERS",
        displayName = "Runtime-Ready Initializers",
        description = "Activating scheduler and background runtime hooks"
    )
}
