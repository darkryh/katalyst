package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getAll
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import io.github.darkryh.katalyst.migrations.telemetry.MigrationTelemetry
import io.github.darkryh.katalyst.migrations.runner.MigrationStatus
import io.github.darkryh.katalyst.telemetry.model.MigrationEntry
import io.github.darkryh.katalyst.telemetry.model.MigrationFailure
import io.github.darkryh.katalyst.telemetry.model.MigrationSnapshot
import io.github.darkryh.katalyst.telemetry.model.MigrationState
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import io.github.darkryh.katalyst.migrations.runner.MigrationState as RunnerMigrationState

/**
 * Taps the MIGRATIONS subsystem and reports its already-computed status.
 *
 * Reads are lazy and read-only: the [MigrationRunner] bean is resolved from the active container at
 * capture time, and its public [MigrationRunner.status] / [MigrationRunner.validateMigrations]
 * read-models are mapped into a [MigrationSnapshot]. Neither read applies migrations nor creates the
 * history table — `status()` swallows an unavailable/unreadable database into an empty applied map,
 * so this capturer works before boot completes and when the subsystem is disabled.
 *
 * [MigrationSnapshot.recentFailures] is also read live, straight from
 * `MigrationTelemetry.failures()`'s bounded ring, independently of the DB-status cache below — so a
 * recent failure is visible even before the next cached read.
 *
 * When the container or the runner bean is absent the provider returns `null`.
 */
class MigrationCapturer : SubsystemCapturer {

    override val id: String = "migrations"

    override fun install(store: TelemetryStore) {
        store.migrationProvider = ::capture
    }

    // status()/validateMigrations() read the app's HikariCP pool, and the WS /stream loop calls
    // snapshot() ~1/s per client. Migrations are effectively immutable after boot, so cache behind a
    // TTL: at most one DB read per minute regardless of poll rate — never per-snapshot JDBC.
    private val cacheTtlMs = 60_000L
    private val lock = Any()
    @Volatile private var cachedAtMs = 0L
    @Volatile private var cached: MigrationSnapshot? = null

    private fun capture(): MigrationSnapshot? {
        // Live in-flight marker (NOT cached — it changes independently of the status TTL).
        val runningId = MigrationTelemetry.runningId
        val runningElapsed = MigrationTelemetry.runningElapsedMs()
        // Also live (NOT cached): the bounded ring of recent failures the runner records as it goes.
        val recentFailures = MigrationTelemetry.failures().map {
            MigrationFailure(epochMs = it.epochMs, id = it.id, message = it.message)
        }
        val base = cachedStatus()
        return when {
            base != null -> base.copy(runningId = runningId, runningElapsedMs = runningElapsed, recentFailures = recentFailures)
            // No DB status yet, but a migration may be executing or have just failed (during boot).
            runningId != null || recentFailures.isNotEmpty() ->
                MigrationSnapshot(runningId = runningId, runningElapsedMs = runningElapsed, recentFailures = recentFailures)
            else -> null
        }
    }

    private fun cachedStatus(): MigrationSnapshot? {
        val existing = cached
        if (existing != null && System.currentTimeMillis() - cachedAtMs < cacheTtlMs) return existing
        synchronized(lock) {
            val current = cached
            if (current != null && System.currentTimeMillis() - cachedAtMs < cacheTtlMs) return current
            val fresh = readFromDb() ?: return cached
            cached = fresh
            cachedAtMs = System.currentTimeMillis()
            return fresh
        }
    }

    private fun readFromDb(): MigrationSnapshot? {
        val container: KatalystContainer =
            KatalystContainerProvider.currentOrNull() ?: return null
        val runner: MigrationRunner =
            container.getOrNull<MigrationRunner>() ?: return null

        // Public marker interface; migrations are registered as beans and discovered by the runner.
        val migrations: List<KatalystMigration> = container.getAll<KatalystMigration>()
        val options: MigrationOptions? = container.getOrNull<MigrationOptions>()

        // status()/validateMigrations() are non-throwing read-models (unreadable DB -> empty applied).
        val report = runner.status(migrations)
        val validationErrors = runner.validateMigrations(migrations).errors

        val transactionalById: Map<String, Boolean> =
            migrations.associate { it.id to it.transactional }

        val entries = report.migrations.map { status ->
            toEntry(status, transactionalById, validationErrors)
        }

        val tallies = mapOf(
            "pending" to report.pending.size,
            "applied" to report.applied.size,
            "baselined" to report.baselined.size,
            "filtered" to report.filtered.size,
            "unknownApplied" to report.unknownApplied.size,
        )

        return MigrationSnapshot(
            entries = entries,
            tallies = tallies,
            validationErrors = validationErrors,
            runAtStartup = options?.runAtStartup ?: true,
        )
    }

    private fun toEntry(
        status: MigrationStatus,
        transactionalById: Map<String, Boolean>,
        validationErrors: List<String>,
    ): MigrationEntry {
        // For source migrations the read-model exposes the shipped code checksum; for orphaned
        // history rows (UNKNOWN_APPLIED) it exposes the checksum stored in the database.
        val orphaned = status.state == RunnerMigrationState.UNKNOWN_APPLIED
        val drift = validationErrors.any {
            it.startsWith("Checksum mismatch for migration ${status.id}.")
        }
        return MigrationEntry(
            id = status.id,
            state = mapState(status.state),
            versionKey = status.version?.toString(),
            durationMs = status.executionTimeMs,
            executedAtEpochMs = status.executedAtEpochMs,
            checksumDb = if (orphaned) status.checksum else null,
            checksumCode = if (orphaned) null else status.checksum,
            transactional = transactionalById[status.id] ?: true,
            checksumDrift = drift,
        )
    }

    private fun mapState(state: RunnerMigrationState): MigrationState = when (state) {
        RunnerMigrationState.PENDING -> MigrationState.PENDING
        RunnerMigrationState.APPLIED -> MigrationState.APPLIED
        RunnerMigrationState.BASELINED -> MigrationState.BASELINED
        RunnerMigrationState.FILTERED -> MigrationState.FILTERED
        RunnerMigrationState.UNKNOWN_APPLIED -> MigrationState.UNKNOWN_APPLIED
    }
}
