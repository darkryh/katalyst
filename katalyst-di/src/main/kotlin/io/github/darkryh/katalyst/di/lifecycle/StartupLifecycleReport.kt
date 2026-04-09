package io.github.darkryh.katalyst.di.lifecycle

/**
 * Structured startup report assembled from lifecycle trackers.
 */
data class StartupLifecycleReport(
    val totalBootstrapTimeMs: Long,
    val lifecycles: List<LifecycleReport>,
    val warningCounts: WarningCounts
) {
    data class LifecycleReport(
        val lifecycleRef: String,
        val name: String,
        val status: String,
        val durationMs: Long,
        val message: String?
    )

    data class WarningCounts(
        val critical: Int,
        val warning: Int,
        val info: Int
    )
}

/**
 * Facade for reading startup lifecycle state in a stable format.
 */
object LifecycleStatusReport {
    fun snapshot(): StartupLifecycleReport {
        val lifecycles = BootstrapProgress.getLifecycles().map { lifecycle ->
            StartupLifecycleReport.LifecycleReport(
                lifecycleRef = lifecycle.lifecycle.lifecycleRef,
                name = lifecycle.lifecycle.displayName,
                status = lifecycle.status.name,
                durationMs = if (lifecycle.endTime > 0 && lifecycle.startTime > 0) {
                    lifecycle.endTime - lifecycle.startTime
                } else {
                    0L
                },
                message = lifecycle.message
            )
        }
        return StartupLifecycleReport(
            totalBootstrapTimeMs = BootstrapProgress.getTotalBootstrapTime(),
            lifecycles = lifecycles,
            warningCounts = StartupLifecycleReport.WarningCounts(
                critical = StartupWarnings.countCritical(),
                warning = StartupWarnings.countWarning(),
                info = StartupWarnings.countInfo()
            )
        )
    }
}
