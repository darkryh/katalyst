package io.github.darkryh.katalyst.telemetry.store

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.DoubleAdder
import kotlin.math.ceil

/**
 * Fixed-bucket latency histogram — bounded-memory percentiles.
 *
 * Percentiles (p50/p95/p99) must never require retaining every sample, or a long-running,
 * high-frequency subsystem (scheduler, events, HTTP) would grow without bound. This records each
 * observation into one of a fixed set of exponentially-spaced millisecond buckets, so memory is a
 * constant `bucketCount` longs regardless of how many billions of samples are recorded. Percentiles
 * are computed by interpolating within the bucket that contains the target rank — approximate, but
 * bounded and monotonic, which is exactly what a debug gauge needs.
 *
 * Boundaries default to ~0ms . ~5min across [bucketCount] buckets. Anything larger lands in the top
 * bucket. Thread-safe via atomics; recording is O(log bucketCount) and allocation-free.
 *
 * The constructor validates its shape for the same reason [RingBuffer] validates its capacity: a
 * ladder that cannot be built is not a degraded histogram, it is a broken one. `bucketCount = 1`
 * divides by `bucketCount - 1`, making every boundary `NaN` and every percentile `NaN` for the life
 * of the instance; `bucketCount = 0` defers the failure to the first `record`. Both fail here.
 *
 * [mean] accumulates in floating point on purpose. The unit is milliseconds and the thing most often
 * measured — an HTTP handler — routinely finishes in well under one, so summing truncated whole
 * milliseconds would report a busy service as `0.0 ms`.
 */
class LatencyHistogram(
    private val bucketCount: Int = 24,
    private val minMs: Double = 1.0,
    private val maxMs: Double = 300_000.0,
) {
    init {
        require(bucketCount > 1) {
            "LatencyHistogram bucketCount must be greater than 1 (the boundary ladder is spaced " +
                "across bucketCount - 1 steps), was $bucketCount"
        }
        require(minMs > 0.0) { "LatencyHistogram minMs must be positive, was $minMs" }
        require(maxMs > minMs) {
            "LatencyHistogram maxMs ($maxMs) must be greater than minMs ($minMs)"
        }
    }

    private val counts = AtomicLongArray(bucketCount)
    private val totalCount = AtomicLong(0)

    /**
     * Sum of every recorded duration, in floating-point milliseconds. A striped [DoubleAdder] rather
     * than a CAS loop so that concurrent request threads do not contend on a single cell.
     */
    private val sumMs = DoubleAdder()
    private val maxObservedBits = AtomicLong(java.lang.Double.doubleToLongBits(0.0))

    // Upper boundary (inclusive) of each bucket, exponentially spaced from minMs to maxMs.
    private val boundaries = DoubleArray(bucketCount) { i ->
        val f = i.toDouble() / (bucketCount - 1)
        minMs * Math.pow(maxMs / minMs, f)
    }

    fun record(durationMs: Double) {
        val v = if (durationMs < 0) 0.0 else durationMs
        counts.incrementAndGet(bucketOf(v))
        totalCount.incrementAndGet()
        sumMs.add(v)
        // Track the true max via CAS.
        while (true) {
            val curBits = maxObservedBits.get()
            val cur = java.lang.Double.longBitsToDouble(curBits)
            if (v <= cur) break
            if (maxObservedBits.compareAndSet(curBits, java.lang.Double.doubleToLongBits(v))) break
        }
    }

    fun record(durationMs: Long) = record(durationMs.toDouble())

    private fun bucketOf(v: Double): Int {
        // Linear scan is fine for ~24 buckets; keeps the code obvious.
        for (i in 0 until bucketCount) if (v <= boundaries[i]) return i
        return bucketCount - 1
    }

    fun count(): Long = totalCount.get()

    fun max(): Double = java.lang.Double.longBitsToDouble(maxObservedBits.get())

    fun mean(): Double {
        val n = totalCount.get()
        return if (n == 0L) 0.0 else sumMs.sum() / n
    }

    /** Approximate percentile in ms. [p] in 0.0..1.0. */
    fun percentile(p: Double): Double {
        val n = totalCount.get()
        if (n == 0L) return 0.0
        val rank = ceil(p.coerceIn(0.0, 1.0) * n).toLong().coerceAtLeast(1)
        var cumulative = 0L
        for (i in 0 until bucketCount) {
            cumulative += counts.get(i)
            if (cumulative >= rank) return boundaries[i]
        }
        return max()
    }

    fun p50(): Double = percentile(0.50)
    fun p95(): Double = percentile(0.95)
    fun p99(): Double = percentile(0.99)
}
