package io.github.darkryh.katalyst.telemetry.store

/**
 * Fixed-capacity, drop-oldest ring buffer — the primary memory-safety primitive.
 *
 * The whole telemetry layer runs inside a backend that executes indefinitely, so NOTHING may grow
 * without bound. A [RingBuffer] holds at most [capacity] elements: once full, each new element
 * overwrites the oldest and increments [droppedCount], so truncation is *counted and visible*
 * rather than silent. Memory is bounded by construction — `capacity` slots, forever.
 *
 * Add/read are O(1) and guarded by a short monitor (no I/O, no allocation on the hot path beyond the
 * element itself), so app producer threads are never blocked on anything but a microsecond critical
 * section. This is deliberately simple and correct rather than lock-free; a debug store values
 * "provably bounded and obviously correct" over shaving nanoseconds.
 */
class RingBuffer<T>(val capacity: Int) {
    init {
        require(capacity > 0) { "RingBuffer capacity must be positive, was $capacity" }
    }

    private val slots = arrayOfNulls<Any?>(capacity)
    private val lock = Any()
    private var head = 0 // index of the next write
    private var count = 0
    private var dropped = 0L

    /** Append [item]; if the buffer is full this overwrites (and counts) the oldest element. */
    fun add(item: T) {
        synchronized(lock) {
            if (count == capacity) dropped++
            slots[head] = item
            head = (head + 1) % capacity
            if (count < capacity) count++
        }
    }

    /** Immutable copy of the current contents, oldest first. */
    fun snapshot(): List<T> = synchronized(lock) {
        val out = ArrayList<T>(count)
        val start = (head - count + capacity) % capacity
        for (i in 0 until count) {
            @Suppress("UNCHECKED_CAST")
            out.add(slots[(start + i) % capacity] as T)
        }
        out
    }

    /** The most recent [n] elements, oldest first. */
    fun latest(n: Int): List<T> {
        val all = snapshot()
        return if (all.size <= n) all else all.subList(all.size - n, all.size)
    }

    fun clear() = synchronized(lock) {
        for (i in slots.indices) slots[i] = null
        head = 0
        count = 0
    }

    /** Elements currently held (0..capacity). */
    fun size(): Int = synchronized(lock) { count }

    /** Total elements discarded because the buffer was full — surfaced in TelemetryMeta. */
    fun droppedCount(): Long = synchronized(lock) { dropped }
}
