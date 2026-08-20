package io.github.darkryh.katalyst.migrations.telemetry

import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi

/**
 * Process-global, bounded record of the migration run in progress.
 *
 * `MigrationRunner.status()` can report applied/pending after the fact, but it cannot answer the
 * question a developer asks during a slow or hung deploy: *which migration is running right now, and
 * for how long?* This holder retains exactly that — the currently-executing migration id and its
 * start time — plus a fixed-size ring of recent failures. Framework-internal
 * (`@KatalystInternalApi`), written only by the runner and read only by the telemetry capturer; a
 * pure side-channel that never affects migration execution.
 */
@KatalystInternalApi
object MigrationTelemetry {

    private const val MAX_FAILURES = 64

    @Volatile
    var runningId: String? = null
        private set

    @Volatile
    private var runningStartMs: Long = 0L

    @KatalystInternalApi
    class Failure internal constructor(val epochMs: Long, val id: String, val message: String?)

    private val failures = ArrayDeque<Failure>()
    private val lock = Any()

    /** Mark [id] as the migration currently executing. */
    fun begin(id: String) {
        runningStartMs = System.currentTimeMillis()
        runningId = id
    }

    /** Clear the in-flight marker after a migration completes. */
    fun end() {
        runningId = null
    }

    /** Record a failure into the bounded ring and clear the in-flight marker. */
    fun recordFailure(id: String, message: String?) {
        synchronized(lock) {
            failures.addLast(Failure(System.currentTimeMillis(), id, message))
            while (failures.size > MAX_FAILURES) failures.removeFirst()
        }
        runningId = null
    }

    /** Milliseconds the in-flight migration has been running, or null if none is running. */
    fun runningElapsedMs(): Long? = runningId?.let { System.currentTimeMillis() - runningStartMs }

    fun failures(): List<Failure> = synchronized(lock) { failures.toList() }
}
