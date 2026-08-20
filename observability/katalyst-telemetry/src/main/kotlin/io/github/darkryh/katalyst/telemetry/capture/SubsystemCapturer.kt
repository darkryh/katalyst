package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.telemetry.store.TelemetryStore

/**
 * A capturer taps ONE Katalyst subsystem and wires its snapshot section into the [TelemetryStore].
 *
 * The contract is deliberately tiny: [install] sets exactly one of the store's provider slots to a
 * lambda that reads the subsystem's *already-computed* runtime state on demand (the framework mostly
 * computes this data and then discards it into logs; the provider simply retains a view of it).
 *
 * Rules every capturer follows:
 * - Read only; never mutate framework state.
 * - Be null-safe: if the subsystem is disabled or not yet initialized, return `null` (or a snapshot
 *   with defaulted/empty fields). All model fields default, so a partial section is valid.
 * - Never throw out of the provider for an expected-absent case; the store guards against throws but
 *   a clean `null` is better than relying on the guard.
 * - Bound everything: if the capturer maintains its own counters/rings for a live stream, use the
 *   store's [TelemetryStore.ring] and the bounded primitives — never an unbounded collection.
 *
 * Capturers are instantiated and installed by the telemetry feature at boot, in any order.
 */
interface SubsystemCapturer {
    /** Stable id for logging (e.g. "scheduler", "http", "persistence"). */
    val id: String

    /** Point the store's matching provider slot at this subsystem's on-demand reader. */
    fun install(store: TelemetryStore)
}
