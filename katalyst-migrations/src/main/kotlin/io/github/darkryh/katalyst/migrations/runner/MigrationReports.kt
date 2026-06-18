package io.github.darkryh.katalyst.migrations.runner

/**
 * Operational state for a migration when comparing the source migration set
 * against the database migration history.
 */
enum class MigrationState {
    PENDING,
    APPLIED,
    BASELINED,
    FILTERED,
    UNKNOWN_APPLIED,
}

/**
 * Read-only description of a migration from source code or migration history.
 */
data class MigrationStatus(
    val id: String,
    val version: Long?,
    val description: String?,
    val checksum: String?,
    val tags: Set<String>,
    val state: MigrationState,
    val historyStatus: String? = null,
    val executionTimeMs: Long? = null,
    val executedAtEpochMs: Long? = null,
)

/**
 * Full migration status report suitable for CLI, Gradle task, or health check
 * integration.
 */
data class MigrationStatusReport(
    val migrations: List<MigrationStatus>,
) {
    val pending: List<MigrationStatus> get() = migrations.filter { it.state == MigrationState.PENDING }
    val applied: List<MigrationStatus> get() = migrations.filter { it.state == MigrationState.APPLIED }
    val baselined: List<MigrationStatus> get() = migrations.filter { it.state == MigrationState.BASELINED }
    val filtered: List<MigrationStatus> get() = migrations.filter { it.state == MigrationState.FILTERED }
    val unknownApplied: List<MigrationStatus> get() = migrations.filter { it.state == MigrationState.UNKNOWN_APPLIED }
}

/**
 * Non-throwing validation result for operational checks.
 */
data class MigrationValidationResult(
    val errors: List<String>,
) {
    val valid: Boolean get() = errors.isEmpty()

    fun throwIfInvalid() {
        require(valid) {
            errors.joinToString(separator = "\n")
        }
    }
}

/**
 * Read-only dry-run report. It contains the exact source migrations that would
 * execute with the current filters, target, and migration history.
 */
data class MigrationDryRunReport(
    val pending: List<MigrationStatus>,
) {
    val count: Int get() = pending.size
}
