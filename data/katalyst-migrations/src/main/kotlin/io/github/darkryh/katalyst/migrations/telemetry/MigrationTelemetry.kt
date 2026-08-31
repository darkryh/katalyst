package io.github.darkryh.katalyst.migrations.telemetry

import io.github.darkryh.katalyst.core.annotation.KatalystInternalApi
import java.util.concurrent.atomic.AtomicLong

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

    /**
     * Monotonic cache-coherency token for read-only migration status consumers.
     *
     * The TUI caches database status to keep its one-second stream from polling JDBC. Every
     * committed history mutation advances this revision so that cache can refresh immediately.
     */
    private val statusRevisionCounter = AtomicLong(0L)

    val statusRevision: Long
        get() = statusRevisionCounter.get()

    @KatalystInternalApi
    class Failure internal constructor(val epochMs: Long, val id: String, val message: String?)

    private val failures = ArrayDeque<Failure>()
    private val lock = Any()

    /** Mark [id] as the migration currently executing. */
    fun begin(id: String) {
        runningStartMs = System.currentTimeMillis()
        runningId = id
    }

    /** Backwards-compatible marker clear for internal callers that did not mutate history. */
    fun end() = end(statusChanged = false)

    /** Clear the in-flight marker after a migration completes and optionally advance status. */
    fun end(statusChanged: Boolean) {
        if (statusChanged) statusRevisionCounter.incrementAndGet()
        runningId = null
    }

    /** Advance the status revision after a history mutation that has no in-flight migration. */
    fun statusChanged() {
        statusRevisionCounter.incrementAndGet()
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

    /** Reset state owned by one application boot. */
    fun reset() {
        runningId = null
        runningStartMs = 0L
        synchronized(lock) { failures.clear() }
        statusRevisionCounter.incrementAndGet()
    }
}
