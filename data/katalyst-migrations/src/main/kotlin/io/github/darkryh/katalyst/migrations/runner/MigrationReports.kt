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
    /**
     * Backwards-compatible checksum view. For a source migration this is the checksum shipped by
     * code; for [MigrationState.UNKNOWN_APPLIED] it is the checksum stored in history.
     *
     * New operational consumers should prefer [checksumCode] and [checksumDb], which never change
     * meaning based on state.
     */
    val checksum: String?,
    val tags: Set<String>,
    val state: MigrationState,
    val historyStatus: String? = null,
    val executionTimeMs: Long? = null,
    val executedAtEpochMs: Long? = null,
    /** Checksum shipped by the registered source migration, or null for a database-only row. */
    val checksumCode: String? = null,
    /** Checksum stored in migration history, or null when the migration has no history row. */
    val checksumDb: String? = null,
)

/**
 * Full migration status report suitable for CLI, Gradle task, or health check
 * integration.
 */
data class MigrationStatusReport(
    val migrations: List<MigrationStatus>,
    /** False only when history could not be queried; a not-yet-created history table is readable-empty. */
    val historyReadable: Boolean = true,
    /** Guarded diagnostic for [historyReadable] == false. */
    val historyError: String? = null,
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
