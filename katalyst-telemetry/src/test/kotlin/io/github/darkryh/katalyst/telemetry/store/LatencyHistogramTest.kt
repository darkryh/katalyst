package io.github.darkryh.katalyst.telemetry.store

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bounds and the arithmetic of the fixed-bucket latency histogram.
 *
 * Two properties carry the type. First, memory is a constant `bucketCount` longs no matter how many
 * samples arrive — the reason percentiles are approximated at all. Second, the numbers it reports
 * have to be *true*: this is what an operator reads to decide whether a service is slow, and the
 * unit it measures (HTTP handler latency) is routinely sub-millisecond, so a mean that floors every
 * sample to a whole millisecond reports 0 ms for a service doing real work.
 *
 * The constructor is checked too, for the same reason [RingBuffer] checks its capacity: an
 * unusable shape must fail where it is created, not silently produce NaN percentiles for the life
 * of the process.
 */
class LatencyHistogramTest {

    // ==================== CONSTRUCTION ====================

    @Test
    fun `rejects a bucket count too small to form a boundary ladder`() {
        // bucketCount == 1 divides by (bucketCount - 1) == 0, making every boundary NaN and every
        // percentile NaN forever; bucketCount == 0 defers the failure to the first record().
        assertFailsWith<IllegalArgumentException>("bucketCount=1 yields NaN boundaries") {
            LatencyHistogram(bucketCount = 1)
        }
        assertFailsWith<IllegalArgumentException>("bucketCount=0 has no bucket to record into") {
            LatencyHistogram(bucketCount = 0)
        }
        assertFailsWith<IllegalArgumentException> { LatencyHistogram(bucketCount = -3) }
    }

    @Test
    fun `rejects a range that cannot produce an increasing boundary ladder`() {
        assertFailsWith<IllegalArgumentException>("minMs=0 makes the ratio infinite") {
            LatencyHistogram(minMs = 0.0)
        }
        assertFailsWith<IllegalArgumentException> { LatencyHistogram(minMs = -1.0) }
        assertFailsWith<IllegalArgumentException>("maxMs must exceed minMs") {
            LatencyHistogram(minMs = 10.0, maxMs = 10.0)
        }
        assertFailsWith<IllegalArgumentException> { LatencyHistogram(minMs = 10.0, maxMs = 1.0) }
    }

    @Test
    fun `the smallest legal shape is usable`() {
        val h = LatencyHistogram(bucketCount = 2, minMs = 1.0, maxMs = 100.0)
        h.record(0.5)
        h.record(50.0)

        assertEquals(2L, h.count())
        assertFalse(h.p50().isNaN(), "a legal shape must never report NaN")
        assertFalse(h.p99().isNaN())
    }

    // ==================== ARITHMETIC ====================

    @Test
    fun `mean keeps sub-millisecond samples`() {
        val h = LatencyHistogram()
        repeat(1_000) { h.record(0.9) }

        assertEquals(1_000L, h.count())
        assertEquals(
            0.9,
            h.mean(),
            1e-9,
            "HTTP latency is routinely sub-millisecond; truncating each sample to a whole ms " +
                "reports a busy service as 0.0 ms",
        )
    }

    @Test
    fun `mean is the true arithmetic mean for random sub-millisecond samples`() {
        repeat(1_000) {
            val h = LatencyHistogram()
            val n = Random.nextInt(1, 50)
            var sum = 0.0
            repeat(n) {
                val v = Random.nextDouble(0.0, 2.0)
                sum += v
                h.record(v)
            }

            assertEquals(sum / n, h.mean(), 1e-6, "mean over $n samples summing to $sum")
        }
    }

    @Test
    fun `negative durations clamp to zero and never distort the mean or the max`() {
        val h = LatencyHistogram()
        h.record(-5.0)
        h.record(1234.5)
        h.record(7.0)

        assertEquals(3L, h.count())
        assertEquals(1234.5, h.max(), 0.0, "max must be the true observed maximum")
        assertEquals((0.0 + 1234.5 + 7.0) / 3.0, h.mean(), 1e-9)
    }

    @Test
    fun `the Long overload agrees with the Double overload`() {
        val fromLong = LatencyHistogram()
        val fromDouble = LatencyHistogram()
        repeat(1_000) {
            val v = Random.nextLong(0, 5_000)
            fromLong.record(v)
            fromDouble.record(v.toDouble())
        }

        assertEquals(fromDouble.count(), fromLong.count())
        assertEquals(fromDouble.mean(), fromLong.mean(), 1e-9)
        assertEquals(fromDouble.p95(), fromLong.p95(), 0.0)
    }

    @Test
    fun `an empty histogram reports zeros rather than NaN`() {
        val h = LatencyHistogram()

        assertEquals(0L, h.count())
        assertEquals(0.0, h.mean(), 0.0)
        assertEquals(0.0, h.max(), 0.0)
        assertEquals(0.0, h.p50(), 0.0)
        assertEquals(0.0, h.p99(), 0.0)
    }

    // ==================== BOUNDS ====================

    @Test
    fun `memory is a constant number of buckets no matter how many samples arrive`() {
        val h = LatencyHistogram(bucketCount = 8)
        // Far past any plausible retention: 200k samples into 8 longs.
        repeat(200_000) { h.record(Random.nextDouble(0.0, 400_000.0)) }

        assertEquals(200_000L, h.count(), "every sample must be counted")
        assertTrue(h.p50() <= h.p95(), "p50=${h.p50()} p95=${h.p95()}")
        assertTrue(h.p95() <= h.p99(), "p95=${h.p95()} p99=${h.p99()}")
        assertTrue(h.max() <= 400_000.0)
    }

    @Test
    fun `percentiles are monotonic, finite and inside the boundary ladder for any legal shape`() {
        repeat(1_000) {
            val buckets = Random.nextInt(2, 40)
            val h = LatencyHistogram(bucketCount = buckets, minMs = 1.0, maxMs = 300_000.0)
            val n = Random.nextInt(1, 200)
            repeat(n) { h.record(Random.nextDouble(0.0, 500_000.0)) }

            val p50 = h.p50()
            val p95 = h.p95()
            val p99 = h.p99()
            assertFalse(p50.isNaN(), "p50 NaN for bucketCount=$buckets")
            assertFalse(p99.isNaN(), "p99 NaN for bucketCount=$buckets")
            assertTrue(p50 <= p95 && p95 <= p99, "not monotonic: $p50 / $p95 / $p99 ($buckets buckets)")
            assertTrue(p50 >= 0.0 && p99 <= 300_000.0, "outside the ladder: $p50 .. $p99")
        }
    }

    @Test
    fun `anything larger than the top boundary lands in the top bucket`() {
        val h = LatencyHistogram(bucketCount = 4, minMs = 1.0, maxMs = 1_000.0)
        repeat(100) { h.record(9_999_999.0) }

        assertEquals(1_000.0, h.p99(), 0.0, "over-range samples must saturate at the top boundary")
        assertEquals(9_999_999.0, h.max(), 0.0, "but the true max is still reported")
    }

    // ==================== CONCURRENCY ====================

    @Test
    fun `is safe under concurrent recorders and keeps an exact count and sum`() {
        val h = LatencyHistogram()
        val threads = (0 until 8).map {
            Thread { repeat(20_000) { h.record(0.25) } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(160_000L, h.count(), "every concurrent record must be counted exactly once")
        assertEquals(0.25, h.mean(), 1e-9, "concurrent sub-ms samples must not be lost from the sum")
        assertEquals(0.25, h.max(), 0.0)
    }
}
