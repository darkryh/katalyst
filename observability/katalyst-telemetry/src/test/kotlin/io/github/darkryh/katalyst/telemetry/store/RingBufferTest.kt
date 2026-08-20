package io.github.darkryh.katalyst.telemetry.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingBufferTest {

    @Test
    fun `never holds more than capacity regardless of how much is added`() {
        val ring = RingBuffer<Int>(capacity = 100)
        // Simulate an indefinitely-running producer: a million events into a 100-slot ring.
        repeat(1_000_000) { ring.add(it) }

        assertEquals(100, ring.size(), "size must be clamped to capacity — the memory ceiling")
        assertEquals(1_000_000L - 100L, ring.droppedCount(), "every overwrite must be counted")
    }

    @Test
    fun `keeps the newest elements, oldest-first, dropping the oldest`() {
        val ring = RingBuffer<Int>(capacity = 3)
        listOf(1, 2, 3, 4, 5).forEach(ring::add)

        assertEquals(listOf(3, 4, 5), ring.snapshot())
        assertEquals(listOf(4, 5), ring.latest(2))
    }

    @Test
    fun `does not count drops until it is actually full`() {
        val ring = RingBuffer<String>(capacity = 4)
        ring.add("a")
        ring.add("b")
        assertEquals(0L, ring.droppedCount())
        assertEquals(2, ring.size())
    }

    @Test
    fun `is safe under concurrent producers and stays bounded`() {
        val ring = RingBuffer<Int>(capacity = 256)
        val threads = (0 until 8).map { t ->
            Thread {
                repeat(50_000) { ring.add(t * 100_000 + it) }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(256, ring.size())
        assertTrue(ring.droppedCount() > 0, "concurrent flood must have overflowed the ring")
        // 8 * 50_000 total adds, 256 retained.
        assertEquals(8L * 50_000L - 256L, ring.droppedCount())
    }
}
