package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getAll
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.di.lifecycle.LifecycleStatusReport
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationStatus
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import io.github.darkryh.katalyst.migrations.telemetry.MigrationTelemetry
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
 * history table. A missing history table is readable-empty; an unavailable database is reported
 * explicitly and never converted into fabricated pending states.
 *
 * [MigrationSnapshot.recentFailures] is also read live, straight from
 * `MigrationTelemetry.failures()`'s bounded ring, independently of the DB-status cache below — so a
 * recent failure is visible even before the next cached read.
 *
 * When the container or the runner bean is absent the provider returns `null`.
 */
class MigrationCapturer : SubsystemCapturer {

    private var sourceSetReady: () -> Boolean = ::componentDiscoveryComplete
    private var statusRevision: () -> Long = { MigrationTelemetry.statusRevision }

    override val id: String = "migrations"

    override fun install(store: TelemetryStore) {
        store.migrationProvider = ::capture
    }

    // status()/validateMigrations() read the app's HikariCP pool, and the WS /stream loop calls
    // snapshot() ~1/s per client. Migrations are effectively immutable after boot, so cache behind a
    // TTL: at most one DB read per minute regardless of poll rate — never per-snapshot JDBC.
    private var statusCache = RevisionAwareMigrationSnapshotCache(DEFAULT_CACHE_TTL_MS, System::currentTimeMillis)

    private fun capture(): MigrationSnapshot? {
        // Live in-flight marker (NOT cached — it changes independently of the status TTL).
        val runningId = MigrationTelemetry.runningId
        val runningElapsed = MigrationTelemetry.runningElapsedMs()
        // Also live (NOT cached): the bounded ring of recent failures the runner records as it goes.
        val recentFailures = MigrationTelemetry.failures().map {
            MigrationFailure(epochMs = it.epochMs, id = it.id, message = it.message)
        }

        // A container becomes globally visible before component discovery starts. Until discovery
        // completes, getAll<KatalystMigration>() is an empty or partial set and cannot be reconciled
        // against history without fabricating database-only/orphaned rows. Keep the transport live,
        // but expose an explicit loading state and never cache the incomplete view.
        if (!sourceSetReady()) {
            return MigrationSnapshot(
                runningId = runningId,
                runningElapsedMs = runningElapsed,
                statusReady = false,
                recentFailures = recentFailures,
            )
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

    private fun cachedStatus(): MigrationSnapshot? =
        statusCache.get(statusRevision = statusRevision, loader = ::readFromDb)

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

        if (!report.historyReadable) {
            return MigrationSnapshot(
                validationErrors = validationErrors,
                historyReadable = false,
                historyError = report.historyError,
                runAtStartup = options?.runAtStartup ?: true,
            )
        }

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
            historyReadable = report.historyReadable,
            historyError = report.historyError,
            runAtStartup = options?.runAtStartup ?: true,
        )
    }

    private fun toEntry(
        status: MigrationStatus,
        transactionalById: Map<String, Boolean>,
        validationErrors: List<String>,
    ): MigrationEntry {
        val drift = validationErrors.any {
            it.startsWith("Checksum mismatch for migration ${status.id}.")
        }
        return MigrationEntry(
            id = status.id,
            state = mapState(status.state),
            versionKey = status.version?.toString(),
            durationMs = status.executionTimeMs,
            executedAtEpochMs = status.executedAtEpochMs,
            checksumDb = status.checksumDb,
            checksumCode = status.checksumCode,
            transactional = transactionalById[status.id],
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

    internal companion object {
        private const val DEFAULT_CACHE_TTL_MS = 60_000L

        internal fun forTest(
            sourceSetReady: () -> Boolean,
            statusRevision: () -> Long = { MigrationTelemetry.statusRevision },
            nowMs: () -> Long = System::currentTimeMillis,
            cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
        ): MigrationCapturer = MigrationCapturer().apply {
            this.sourceSetReady = sourceSetReady
            this.statusRevision = statusRevision
            this.statusCache = RevisionAwareMigrationSnapshotCache(cacheTtlMs, nowMs)
        }
    }
}

private const val COMPONENT_DISCOVERY_REF = "LIFECYCLE_COMPONENT_DISCOVERY_REGISTRATION"
private const val COMPLETED_STATUS = "COMPLETED"

/** True only when the migration source set can no longer be empty or partial because of discovery. */
private fun componentDiscoveryComplete(): Boolean =
    LifecycleStatusReport.snapshot().lifecycles
        .firstOrNull { it.lifecycleRef == COMPONENT_DISCOVERY_REF }
        ?.status == COMPLETED_STATUS

/**
 * One cached database snapshot keyed by both time and the runner's committed-history revision.
 * Revision changes bypass the TTL immediately; stable revisions keep the one-minute JDBC bound.
 */
internal class RevisionAwareMigrationSnapshotCache(
    private val ttlMs: Long,
    private val nowMs: () -> Long,
) {
    private data class Cached(
        val snapshot: MigrationSnapshot,
        val revision: Long,
        val capturedAtMs: Long,
    )

    private val lock = Any()

    @Volatile
    private var cached: Cached? = null

    fun get(
        statusRevision: () -> Long,
        loader: () -> MigrationSnapshot?,
    ): MigrationSnapshot? {
        val requestedRevision = statusRevision()
        cached?.takeIf { it.isFresh(requestedRevision, nowMs()) }?.let { return it.snapshot }

        synchronized(lock) {
            val revisionBeforeRead = statusRevision()
            cached?.takeIf { it.isFresh(revisionBeforeRead, nowMs()) }?.let { return it.snapshot }

            var fresh = loader() ?: return cached?.snapshot
            var revisionForFresh = revisionBeforeRead
            val revisionAfterRead = statusRevision()

            // A migration may have committed while JDBC was being read. Re-read once against the
            // new revision so we never stamp an old result with a new coherency token.
            if (revisionAfterRead != revisionBeforeRead) {
                loader()?.let { refreshed ->
                    fresh = refreshed
                    revisionForFresh = revisionAfterRead
                }
            }

            cached = Cached(fresh, revisionForFresh, nowMs())
            return fresh
        }
    }

    private fun Cached.isFresh(revision: Long, now: Long): Boolean {
        val age = now - capturedAtMs
        return this.revision == revision && age >= 0L && age < ttlMs
    }
}
