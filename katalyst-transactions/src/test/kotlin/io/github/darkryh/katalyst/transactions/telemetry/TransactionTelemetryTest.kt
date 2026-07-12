package io.github.darkryh.katalyst.transactions.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for [TransactionTelemetry].
 *
 * `committed` / `rolledBack` / `timedOut` used to expose the internal `AtomicLong` counters by
 * reference, letting any caller call `.set(...)` / `.incrementAndGet()` on them and silently
 * corrupt process-global metrics. They must now be read-only snapshots (`Long`) that cannot be
 * used to mutate internal state.
 */
class TransactionTelemetryTest {

    @Test
    fun `counters are plain Long snapshots, not mutable references`() {
        // The properties must be Long (a value type), not AtomicLong (a mutable reference type).
        // This is a compile-time guarantee: if this compiles, callers have no `.set`/`.incrementAndGet`
        // handle into TransactionTelemetry's internal state via these accessors.
        val committed: Long = TransactionTelemetry.committed
        val rolledBack: Long = TransactionTelemetry.rolledBack
        val timedOut: Long = TransactionTelemetry.timedOut

        assertEquals(committed, TransactionTelemetry.committed)
        assertEquals(rolledBack, TransactionTelemetry.rolledBack)
        assertEquals(timedOut, TransactionTelemetry.timedOut)
    }

    @Test
    fun `recordCommit increments the committed snapshot without external mutation`() {
        val before = TransactionTelemetry.committed

        TransactionTelemetry.recordCommit(5)

        assertEquals(before + 1, TransactionTelemetry.committed)
    }

    @Test
    fun `recordRollback and recordTimeout increment only their own counters`() {
        val committedBefore = TransactionTelemetry.committed
        val rolledBackBefore = TransactionTelemetry.rolledBack
        val timedOutBefore = TransactionTelemetry.timedOut

        TransactionTelemetry.recordRollback(3)
        assertEquals(rolledBackBefore + 1, TransactionTelemetry.rolledBack)
        assertEquals(committedBefore, TransactionTelemetry.committed)

        TransactionTelemetry.recordTimeout(7)
        assertEquals(timedOutBefore + 1, TransactionTelemetry.timedOut)
        assertEquals(committedBefore, TransactionTelemetry.committed)
    }
}
