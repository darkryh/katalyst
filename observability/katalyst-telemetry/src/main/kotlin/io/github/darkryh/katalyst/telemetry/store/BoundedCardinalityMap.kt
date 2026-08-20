package io.github.darkryh.katalyst.telemetry.store

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A keyed accumulator with a hard cap on the number of distinct keys — the second memory-safety
 * primitive, and the one that guards the subtle trap.
 *
 * The dangerous unbounded growth in a telemetry layer is rarely the ring buffers; it is per-key
 * rollup maps. A route keyed by raw path (`/user/123`, `/user/124`, …) or an event keyed by a
 * high-cardinality id would grow the map forever. This map holds at most [maxKeys] entries — total,
 * counting the overflow bucket — so its memory is bounded no matter how adversarial the key space.
 * Callers that key by route should still pass a *template* (`/user/{id}`), not a raw path — this cap
 * is the backstop, not the plan.
 *
 * Three details make the cap real rather than nominal:
 *  - **The overflow bucket is one of the [maxKeys] slots, not an extra one.** Caller keys are
 *    therefore admitted up to `maxKeys - 1`; the last slot is reserved for [overflowKey]. A map that
 *    grew to `maxKeys + 1` the moment it overflowed would be bounded by accident, not by design.
 *  - **Admission is decided by an atomic reservation, not by reading the map's size.**
 *    `entries.size < maxKeys` is check-then-act: every request thread runs it, so N threads can all
 *    observe the same sub-cap size and all insert. The counter below is claimed by CAS *before* the
 *    entry is created, so the cap holds under any amount of concurrency.
 *  - **[overflowKey] is a reserved name.** A caller key spelled exactly like it shares the overflow
 *    bucket — it must, since they are one map entry — and [hasOverflowed] then reports `true`. The
 *    bug worth avoiding is not the sharing but the silence: an operator reading `other = 4_211` has
 *    to be able to tell a genuine key from a landfill.
 *
 * [V] values are created lazily by [newValue] on first admission of a key.
 */
class BoundedCardinalityMap<V : Any>(
    val maxKeys: Int = 200,
    val overflowKey: String = "other",
    private val newValue: () -> V,
) {
    init {
        require(maxKeys > 0) {
            "BoundedCardinalityMap maxKeys must be positive, was $maxKeys"
        }
    }

    /** Slots available to caller keys: the cap minus the one reserved for [overflowKey]. */
    private val keySlots = maxKeys - 1

    private val entries = ConcurrentHashMap<String, V>()

    /** Caller-key admissions granted so far. Claimed by CAS before the entry is created. */
    private val admitted = AtomicInteger(0)

    @Volatile private var overflowed = false

    /** Returns the value for [key], or the shared overflow bucket once the cap is reached. */
    fun get(key: String): V {
        if (key == overflowKey) {
            // Reserved name: this key IS the overflow bucket, so say the bucket is shared.
            overflowed = true
            return overflowBucket()
        }
        entries[key]?.let { return it }
        if (claimSlot()) {
            val candidate = newValue()
            val existing = entries.putIfAbsent(key, candidate)
            if (existing == null) return candidate
            // Lost the race for this same key; hand the slot back so the cap is not eaten by it.
            admitted.decrementAndGet()
            return existing
        }
        overflowed = true
        return overflowBucket()
    }

    /** Atomically claims one caller-key slot, or returns false when they are all spoken for. */
    private fun claimSlot(): Boolean {
        while (true) {
            val current = admitted.get()
            if (current >= keySlots) return false
            if (admitted.compareAndSet(current, current + 1)) return true
        }
    }

    private fun overflowBucket(): V = entries.computeIfAbsent(overflowKey) { newValue() }

    fun snapshot(): Map<String, V> = HashMap(entries)

    fun keyCount(): Int = entries.size

    /** True once at least one key was folded into the overflow bucket (surface this to the operator). */
    fun hasOverflowed(): Boolean = overflowed

    fun clear() {
        entries.clear()
        admitted.set(0)
        overflowed = false
    }
}
