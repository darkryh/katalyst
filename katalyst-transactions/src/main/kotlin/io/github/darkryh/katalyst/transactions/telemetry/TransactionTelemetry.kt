package io.github.darkryh.katalyst.transactions.telemetry

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/**
 * Process-global, bounded transaction outcome metrics. The framework's rich transaction metrics
 * (`DefaultMetricsCollector`, `TransactionMetricsSummary`) are `internal` and never wired to a live
 * read-model; this holder captures the outcomes the manager already decides — commit / rollback /
 * timeout — plus a bounded duration reservoir for percentiles. Written only by the transaction
 * manager, read only by the telemetry capturer; a pure read-only side-channel.
 *
 * (It is a plain `public` object rather than `@KatalystInternalApi` because `katalyst-transactions`
 * cannot depend on `katalyst-core` — core already depends on transactions — so the internal-API
 * marker is not available here.)
 *
 * Memory is bounded by construction: three counters and a fixed [RECENT]-slot duration ring.
 */
object TransactionTelemetry {

    private const val RECENT = 256

    private val committedCount = AtomicLong(0)
    private val rolledBackCount = AtomicLong(0)
    private val timedOutCount = AtomicLong(0)

    /** Snapshot of the current committed-transaction count. Read-only; does not expose internal state. */
    val committed: Long get() = committedCount.get()

    /** Snapshot of the current rolled-back-transaction count. Read-only; does not expose internal state. */
    val rolledBack: Long get() = rolledBackCount.get()

    /** Snapshot of the current timed-out-transaction count. Read-only; does not expose internal state. */
    val timedOut: Long get() = timedOutCount.get()

    private val durations = LongArray(RECENT)
    private var index = 0
    private var filled = 0
    private val lock = Any()

    private fun record(ms: Long) = synchronized(lock) {
        durations[index] = ms
        index = (index + 1) % RECENT
        if (filled < RECENT) filled++
    }

    fun recordCommit(durationMs: Long) {
        committedCount.incrementAndGet()
        record(durationMs)
    }

    fun recordRollback(durationMs: Long) {
        rolledBackCount.incrementAndGet()
        record(durationMs)
    }

    fun recordTimeout(durationMs: Long) {
        timedOutCount.incrementAndGet()
        record(durationMs)
    }

    /** (p50, p95, p99) transaction durations in ms over the recent-durations ring. */
    fun percentiles(): Triple<Double, Double, Double> = synchronized(lock) {
        if (filled == 0) return Triple(0.0, 0.0, 0.0)
        val copy = durations.copyOf(filled)
        copy.sort()
        fun pct(p: Double): Double = copy[(ceil(p * copy.size).toInt().coerceIn(1, copy.size)) - 1].toDouble()
        Triple(pct(0.50), pct(0.95), pct(0.99))
    }
}
