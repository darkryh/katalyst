package io.github.darkryh.katalyst.telemetry.store

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The memory bound of the per-key rollup map, and what it reports when it hits it.
 *
 * The class promises "at most [BoundedCardinalityMap.maxKeys] distinct keys" — that is the whole
 * point of the type, because the map is fed by route templates and event ids that an adversarial
 * (or merely careless) caller can make unbounded. Three things have to hold for the promise to be
 * worth anything:
 *  - the overflow bucket is one of the capped keys, not a free extra slot;
 *  - the admission decision survives concurrent callers, since `size < maxKeys` on a
 *    ConcurrentHashMap is check-then-act and every request thread runs it;
 *  - a caller key that collides with the overflow bucket's own name is *reported*, because an
 *    operator reading `other = 4_211` needs to know whether that is a route or a landfill.
 */
class BoundedCardinalityMapTest {

    private fun counters(maxKeys: Int, overflowKey: String = "other") =
        BoundedCardinalityMap(maxKeys = maxKeys, overflowKey = overflowKey) { AtomicLong(0) }

    @Test
    fun `never holds more than maxKeys distinct keys, overflow bucket included`() {
        val map = counters(maxKeys = 64)
        // A raw-path key space: a hundred thousand distinct keys into a 64-key map.
        repeat(100_000) { map.get("/user/$it").incrementAndGet() }

        assertTrue(
            map.keyCount() <= 64,
            "the map admits at most maxKeys=64 distinct keys; the overflow bucket is one of them, " +
                "not a 65th slot. Got ${map.keyCount()}",
        )
        assertTrue(
            map.snapshot().size <= 64,
            "snapshot must not expose more keys than the cap, got ${map.snapshot().size}",
        )
        assertTrue(map.hasOverflowed(), "folding 100_000 keys into 64 slots must be reported")
    }

    @Test
    fun `the overflow bucket does not push the map one key past the cap`() {
        val map = counters(maxKeys = 2)
        map.get("a").incrementAndGet()
        map.get("b").incrementAndGet()
        map.get("c").incrementAndGet()

        assertTrue(
            map.keyCount() <= 2,
            "maxKeys=2 means two entries total; got ${map.keyCount()} -> ${map.snapshot().keys}",
        )
    }

    @Test
    fun `no observation is lost when a key is folded into the overflow bucket`() {
        val map = counters(maxKeys = 16)
        repeat(5_000) { map.get("k${it % 500}").incrementAndGet() }

        val retained = map.snapshot().values.sumOf { it.get() }
        assertEquals(
            5_000L,
            retained,
            "folding must merge counts into the overflow bucket, never drop them",
        )
        assertTrue(map.keyCount() <= 16, "got ${map.keyCount()}")
        assertTrue(map.hasOverflowed())
    }

    @Test
    fun `a caller key equal to the overflow key is reported as a fold, never silently merged`() {
        val map = counters(maxKeys = 8)

        // A legitimate, low-cardinality key that happens to be spelled like the overflow bucket.
        map.get("other").incrementAndGet()

        assertTrue(
            map.hasOverflowed(),
            "'other' names the overflow bucket, so a caller key spelled that way shares it. The " +
                "map must say so — otherwise the operator reads a landfill bucket as a genuine key",
        )
    }

    @Test
    fun `a caller key spelled like the overflow key shares the same bucket the cap folds into`() {
        val map = counters(maxKeys = 4)
        map.get("other").incrementAndGet() // one legitimate hit
        repeat(1_000) { map.get("route-$it").incrementAndGet() }

        assertTrue(map.keyCount() <= 4, "got ${map.keyCount()} -> ${map.snapshot().keys}")
        val folded = map.snapshot().getValue("other").get()
        assertTrue(
            folded > 1L,
            "the legitimate 'other' hit and the folded overflow share one bucket, so its count is " +
                "a mixture; got $folded",
        )
        assertTrue(map.hasOverflowed(), "and the mixture must be flagged")
    }

    @Test
    fun `a custom overflow key is the one that is reserved`() {
        val map = counters(maxKeys = 3, overflowKey = "__rest__")
        repeat(1_000) { map.get("key-$it").incrementAndGet() }

        assertTrue(map.keyCount() <= 3, "got ${map.keyCount()}")
        assertTrue(
            map.snapshot().containsKey("__rest__"),
            "overflow must land under the configured key, got ${map.snapshot().keys}",
        )
    }

    @Test
    fun `a cap that cannot hold even the overflow bucket is rejected at construction`() {
        assertFailsWith<IllegalArgumentException>("maxKeys=0 cannot honour 'at most 0 keys'") {
            counters(maxKeys = 0)
        }
        assertFailsWith<IllegalArgumentException> { counters(maxKeys = -5) }
    }

    @Test
    fun `clear releases the cap so the map can admit keys again`() {
        val map = counters(maxKeys = 8)
        repeat(1_000) { map.get("first-$it").incrementAndGet() }
        assertTrue(map.hasOverflowed())

        map.clear()

        assertEquals(0, map.keyCount(), "clear must empty the map")
        assertTrue(!map.hasOverflowed(), "clear must reset the overflow flag")

        map.get("second").incrementAndGet()
        assertEquals(
            1L,
            map.snapshot().getValue("second").get(),
            "after clear a fresh key must be admitted on its own, not folded into overflow",
        )
    }

    @Test
    fun `concurrent admissions at the boundary never overshoot the cap`() {
        // Generative: random caps and random pre-fills, so the racing threads land on the
        // admission boundary from every distance.
        repeat(60) { round ->
            val maxKeys = Random.nextInt(2, 12)
            val map = counters(maxKeys)
            val prefill = Random.nextInt(0, maxKeys)
            repeat(prefill) { map.get("seed-$it").incrementAndGet() }

            val racers = 24
            val start = CountDownLatch(1)
            val threads = (0 until racers).map { i ->
                Thread {
                    start.await()
                    map.get("racer-$round-$i").incrementAndGet()
                }
            }
            threads.forEach(Thread::start)
            start.countDown()
            threads.forEach(Thread::join)

            assertTrue(
                map.keyCount() <= maxKeys,
                "round $round: `size < maxKeys` is check-then-act on a ConcurrentHashMap, so " +
                    "$racers threads crossing the boundary together must not each be admitted " +
                    "(maxKeys=$maxKeys, prefill=$prefill, got ${map.keyCount()})",
            )
        }
    }

    @Test
    fun `concurrent admissions keep every observation and stay bounded`() {
        val map = counters(maxKeys = 32)
        val threads = (0 until 8).map { t ->
            Thread { repeat(20_000) { map.get("t$t-key-$it").incrementAndGet() } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertTrue(map.keyCount() <= 32, "got ${map.keyCount()}")
        assertEquals(
            8L * 20_000L,
            map.snapshot().values.sumOf { it.get() },
            "every increment must survive, whether admitted or folded",
        )
    }
}
