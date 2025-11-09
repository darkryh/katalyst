package com.ead.katalyst.di.lifecycle

import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks and displays bootstrap phase progress in real-time.
 *
 * **Purpose**: Show structured progress through each initialization phase
 * with real-time status updates (⏳ running, ✓ completed, ✗ failed).
 *
 * **Display Format**:
 * ```
 * ╔════════════════════════════════════════════════════════════╗
 * ║ PHASE 1: KOIN DI BOOTSTRAP (Component Scanning)            ║
 * ╚════════════════════════════════════════════════════════════╝
 * ⏳ Scanning packages...
 * ✓  Completed in 234ms
 * ```
 */
class BootstrapProgressLogger {
    private val logger = LoggerFactory.getLogger("BootstrapProgressLogger")

    data class PhaseInfo(
        val phase: Int,
        val name: String,
        val description: String,
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

    private val phases = mutableListOf<PhaseInfo>()
    private val phaseStartTimes = mutableMapOf<Int, Long>()

    init {
        // Initialize standard phases
        initializePhases()
    }

    private fun initializePhases() {
        phases.add(PhaseInfo(1, "Koin DI Bootstrap", "Component scanning and initialization"))
        phases.add(PhaseInfo(2, "Scheduler Method Discovery", "Discovering scheduled methods"))
        phases.add(PhaseInfo(3, "Component Discovery", "Auto-discovering repositories, services, components"))
        phases.add(PhaseInfo(4, "Database Schema Initialization", "Creating database schema"))
        phases.add(PhaseInfo(5, "Transaction Adapter Registration", "Registering transaction adapters"))
        phases.add(PhaseInfo(6, "Application Initialization Hooks", "Running custom initializers"))
        phases.add(PhaseInfo(7, "Ktor Engine Startup", "Starting HTTP server"))
    }

    /**
     * Mark a phase as started.
     */
    fun startPhase(phaseNumber: Int) {
        val phase = phases.getOrNull(phaseNumber - 1) ?: return

        val startTime = System.currentTimeMillis()
        phaseStartTimes[phaseNumber] = startTime

        phase.startTime = startTime
        phase.status = PhaseStatus.RUNNING

        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ PHASE {}: {} ║", phaseNumber, padRight(phase.name, 38))
        logger.info("║ {}", padRight(phase.description, 50) + "║")
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("⏳ Running...")
    }

    /**
     * Mark a phase as completed.
     */
    fun completePhase(phaseNumber: Int, message: String? = null) {
        val phase = phases.getOrNull(phaseNumber - 1) ?: return

        val endTime = System.currentTimeMillis()
        phase.endTime = endTime
        phase.status = PhaseStatus.COMPLETED
        phase.message = message

        val duration = endTime - phase.startTime
        logger.info("✓  Completed in {}ms", duration)
        message?.let { logger.info("   {}", it) }
        logger.info("")
    }

    /**
     * Mark a phase as failed.
     */
    fun failPhase(phaseNumber: Int, error: Throwable? = null) {
        val phase = phases.getOrNull(phaseNumber - 1) ?: return

        val endTime = System.currentTimeMillis()
        phase.endTime = endTime
        phase.status = PhaseStatus.FAILED
        phase.message = error?.message

        val duration = endTime - phase.startTime
        logger.error("✗  Failed after {}ms", duration)
        error?.let { logger.error("   Error: {}", it.message) }
        logger.error("")
    }

    /**
     * Mark a phase as skipped.
     */
    fun skipPhase(phaseNumber: Int, reason: String? = null) {
        val phase = phases.getOrNull(phaseNumber - 1) ?: return

        phase.status = PhaseStatus.SKIPPED
        phase.message = reason

        logger.info("⊘  Skipped")
        reason?.let { logger.info("   Reason: {}", it) }
        logger.info("")
    }

    /**
     * Display overall progress summary.
     */
    fun displayProgressSummary() {
        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ BOOTSTRAP PROGRESS SUMMARY                         ║")
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("")

        phases.forEach { phase ->
            val statusIcon = when (phase.status) {
                PhaseStatus.COMPLETED -> "✓"
                PhaseStatus.FAILED -> "✗"
                PhaseStatus.SKIPPED -> "⊘"
                PhaseStatus.RUNNING -> "⏳"
                PhaseStatus.PENDING -> "○"
            }

            val duration = if (phase.endTime > 0 && phase.startTime > 0) {
                "${phase.endTime - phase.startTime}ms"
            } else {
                "-"
            }

            val statusName = phase.status.name.padEnd(10)
            logger.info("│ {} Phase {}: {} [{}] │", statusIcon, phase.phase, padRight(phase.name, 30), duration)
        }

        logger.info("")
    }

    /**
     * Get total bootstrap time in milliseconds.
     */
    fun getTotalBootstrapTime(): Long {
        val firstStart = phases.filter { it.startTime > 0 }.minOfOrNull { it.startTime } ?: return 0L
        val lastEnd = phases.filter { it.endTime > 0 }.maxOfOrNull { it.endTime } ?: return 0L
        return if (lastEnd > firstStart) lastEnd - firstStart else 0L
    }

    /**
     * Get phase duration in milliseconds.
     */
    fun getPhaseDuration(phaseNumber: Int): Long {
        val phase = phases.getOrNull(phaseNumber - 1) ?: return 0L
        return if (phase.endTime > 0 && phase.startTime > 0) {
            phase.endTime - phase.startTime
        } else {
            0L
        }
    }

    /**
     * Get all phases and their status.
     */
    fun getPhases(): List<PhaseInfo> = phases.toList()

    /**
     * Clear all phase data (for testing).
     */
    fun clear() {
        phases.forEach {
            it.startTime = 0L
            it.endTime = 0L
            it.status = PhaseStatus.PENDING
            it.message = null
        }
        phaseStartTimes.clear()
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

    fun startPhase(phaseNumber: Int) = logger.startPhase(phaseNumber)

    fun completePhase(phaseNumber: Int, message: String? = null) = logger.completePhase(phaseNumber, message)

    fun failPhase(phaseNumber: Int, error: Throwable? = null) = logger.failPhase(phaseNumber, error)

    fun skipPhase(phaseNumber: Int, reason: String? = null) = logger.skipPhase(phaseNumber, reason)

    fun displayProgressSummary() = logger.displayProgressSummary()

    fun getTotalBootstrapTime(): Long = logger.getTotalBootstrapTime()

    fun getPhaseDuration(phaseNumber: Int): Long = logger.getPhaseDuration(phaseNumber)

    fun getPhases(): List<BootstrapProgressLogger.PhaseInfo> = logger.getPhases()

    fun clear() = logger.clear()
}
