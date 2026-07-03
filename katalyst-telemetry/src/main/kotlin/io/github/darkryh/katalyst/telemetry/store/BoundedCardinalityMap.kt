package io.github.darkryh.katalyst.telemetry.store

import java.util.concurrent.ConcurrentHashMap

/**
 * A keyed accumulator with a hard cap on the number of distinct keys — the second memory-safety
 * primitive, and the one that guards the subtle trap.
 *
 * The dangerous unbounded growth in a telemetry layer is rarely the ring buffers; it is per-key
 * rollup maps. A route keyed by raw path (`/user/123`, `/user/124`, …) or an event keyed by a
 * high-cardinality id would grow the map forever. This map admits at most [maxKeys] distinct keys;
 * every key beyond the cap is folded into a single [overflowKey] bucket (default `"other"`), so the
 * map's memory is bounded no matter how adversarial the key space. Callers that key by route should
 * still pass a *template* (`/user/{id}`), not a raw path — this cap is the backstop, not the plan.
 *
 * [V] values are created lazily by [newValue] on first admission of a key.
 */
class BoundedCardinalityMap<V : Any>(
    val maxKeys: Int = 200,
    val overflowKey: String = "other",
    private val newValue: () -> V,
) {
    private val entries = ConcurrentHashMap<String, V>()
    @Volatile private var overflowed = false

    /** Returns the value for [key], or the shared overflow bucket once the cap is reached. */
    fun get(key: String): V {
        entries[key]?.let { return it }
        // Fast path exhausted; decide admission under the map's own concurrency guarantees.
        if (entries.size < maxKeys) {
            return entries.computeIfAbsent(key) { newValue() }
        }
        overflowed = true
        return entries.computeIfAbsent(overflowKey) { newValue() }
    }

    fun snapshot(): Map<String, V> = HashMap(entries)

    fun keyCount(): Int = entries.size

    /** True once at least one key was folded into the overflow bucket (surface this to the operator). */
    fun hasOverflowed(): Boolean = overflowed

    fun clear() {
        entries.clear()
        overflowed = false
    }
}
