package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.telemetry.model.PersistenceSnapshot
import io.github.darkryh.katalyst.telemetry.model.PoolSnapshot
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore

/**
 * Taps the persistence subsystem and reports the HikariCP connection-pool gauge — the core
 * saturation/leak view that the framework already computes and otherwise discards.
 *
 * The provider is read-only and side-effect-free:
 * - It resolves the [DatabaseFactory] bean lazily at capture time via the active container. Before
 *   boot completes, or when persistence is disabled (no DB configured), that bean is absent and the
 *   capturer reports [PersistenceSnapshot.databaseConfigured] = `false`.
 * - When the bean is present it reads [DatabaseFactory.poolSnapshot] (a cheap MXBean read that never
 *   exposes the datasource) and maps it into a [PoolSnapshot].
 *
 * Pool max/min bounds come from the sibling [DatabaseConfig] bean when available; if it cannot be
 * resolved those two fields stay at their model defaults.
 *
 * Repo op tallies, audit-table distributions, connection-timeout/leak counters, and the
 * orphaned-duplicate-pool count are deepen-pass instrumentation and are left at their defaults here.
 */
class PersistenceCapturer : SubsystemCapturer {

    override val id: String = "persistence"

    override fun install(store: TelemetryStore) {
        store.persistenceProvider = ::snapshot
    }

    private fun snapshot(): PersistenceSnapshot {
        val container = KatalystContainerProvider.currentOrNull()
            ?: return PersistenceSnapshot(databaseConfigured = false)

        // Absent when the DB is not configured or the container is not yet fully wired.
        val factory = container.getOrNull<DatabaseFactory>()
            ?: return PersistenceSnapshot(databaseConfigured = false)

        val config = container.getOrNull<DatabaseConfig>()
        val pool = factory.poolSnapshot()

        return PersistenceSnapshot(
            databaseConfigured = true,
            pool = PoolSnapshot(
                active = pool.active,
                idle = pool.idle,
                pending = pool.pending,
                total = pool.total,
                maxPoolSize = config?.maxPoolSize ?: 0,
                minIdle = config?.minIdleConnections ?: 0,
                closed = pool.closed,
            ),
        )
    }
}
