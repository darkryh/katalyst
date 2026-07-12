package io.github.darkryh.katalyst.telemetry.model

import kotlinx.serialization.Serializable

/** Operational state of a single migration. */
@Serializable
enum class MigrationState { PENDING, APPLIED, BASELINED, FILTERED, UNKNOWN_APPLIED }

/** One migration with its state, timing, and checksum (for drift detection). */
@Serializable
data class MigrationEntry(
    val id: String,
    val state: MigrationState,
    val versionKey: String? = null,
    val durationMs: Long? = null,
    val executedAtEpochMs: Long? = null,
    val checksumDb: String? = null,
    val checksumCode: String? = null,
    val transactional: Boolean = true,
    /** True when the stored checksum differs from the shipped code checksum. */
    val checksumDrift: Boolean = false,
)

/** One recorded migration failure from the live process — see `MigrationTelemetry.Failure`. */
@Serializable
data class MigrationFailure(
    val epochMs: Long,
    val id: String,
    val message: String? = null,
)

/**
 * Migrations: which migration is running now, applied vs pending vs baselined vs orphaned, checksum
 * drift, and live schema drift. Fed from status()/validateMigrations()/dryRun() — already-free.
 */
@Serializable
data class MigrationSnapshot(
    val entries: List<MigrationEntry> = emptyList(),
    val tallies: Map<String, Int> = emptyMap(),
    val runningId: String? = null,
    val runningElapsedMs: Long? = null,
    val validationErrors: List<String> = emptyList(),
    /** Statements the live DB still needs vs code — non-zero means drift even if history says applied. */
    val schemaDriftStatements: Int = 0,
    val historyReadable: Boolean = true,
    val runAtStartup: Boolean = true,
    /** Bounded ring of recent migration failures for this process — see `MigrationTelemetry.failures()`. */
    val recentFailures: List<MigrationFailure> = emptyList(),
)
