package io.github.darkryh.katalyst.telemetry

/**
 * Static, environment-driven configuration for the telemetry layer. Telemetry is opt-OUT: it is on by
 * default and disabled only by an explicit `-Dkatalyst.telemetry.enabled=false` JVM property or a
 * `KATALYST_TELEMETRY_ENABLED=false` environment variable. All reads are guarded so a locked-down
 * SecurityManager never breaks boot.
 */
data class TelemetryConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val memoryBudgetBytes: Long,
    val snapshotSpillEnabled: Boolean,
    /**
     * Opt-in (`-Dkatalyst.telemetry.quiet=true`): once telemetry attaches, raise the root logger to
     * WARN so the console stops flooding and the TUI inspector becomes the primary view. Warnings
     * and errors still print; loggers pinned explicitly in the app's logback config are untouched.
     */
    val quiet: Boolean,
) {
    companion object {
        /** ~12 MB default budget for the bounded store. */
        const val DEFAULT_MEMORY_BUDGET_BYTES: Long = 12L * 1024 * 1024

        /** Loopback host — telemetry never binds a routable interface. */
        const val DEFAULT_HOST: String = "127.0.0.1"

        private fun prop(name: String): String? = runCatching { System.getProperty(name) }.getOrNull()
        private fun env(name: String): String? = runCatching { System.getenv(name) }.getOrNull()

        /** JVM prop takes precedence over env; env over the supplied default. */
        private fun resolve(propName: String, envName: String, default: String? = null): String? =
            prop(propName) ?: env(envName) ?: default

        fun fromEnvironment(): TelemetryConfig {
            val enabled = resolve("katalyst.telemetry.enabled", "KATALYST_TELEMETRY_ENABLED")
                ?.let { !it.equals("false", ignoreCase = true) } ?: true

            val host = resolve("katalyst.telemetry.host", "KATALYST_TELEMETRY_HOST", DEFAULT_HOST)
                ?: DEFAULT_HOST

            // 0 = OS-assigned ephemeral port (the default), otherwise the configured fixed port.
            val port = resolve("katalyst.telemetry.port", "KATALYST_TELEMETRY_PORT")
                ?.toIntOrNull()?.coerceAtLeast(0) ?: 0

            val budget = resolve("katalyst.telemetry.memoryBudgetBytes", "KATALYST_TELEMETRY_MEMORY_BUDGET_BYTES")
                ?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_MEMORY_BUDGET_BYTES

            val spill = resolve("katalyst.telemetry.spill", "KATALYST_TELEMETRY_SPILL")
                ?.let { it.equals("true", ignoreCase = true) } ?: false

            val quiet = resolve("katalyst.telemetry.quiet", "KATALYST_TELEMETRY_QUIET")
                ?.let { it.equals("true", ignoreCase = true) } ?: false

            return TelemetryConfig(
                enabled = enabled,
                host = host,
                port = port,
                memoryBudgetBytes = budget,
                snapshotSpillEnabled = spill,
                quiet = quiet,
            )
        }
    }
}
