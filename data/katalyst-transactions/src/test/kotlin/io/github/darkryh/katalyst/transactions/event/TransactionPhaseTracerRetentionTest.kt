package io.github.darkryh.katalyst.transactions.event

import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionPhaseTracerRetentionTest {
    @Test
    fun `trace history evicts oldest events at capacity`() {
        val tracer = TransactionPhaseTracer(maxEvents = 3)

        repeat(10) { index ->
            tracer.recordEventsValidated(TransactionPhase.BEFORE_COMMIT_VALIDATION, index, "transaction-$index")
        }

        assertEquals(3, tracer.size())
        assertEquals(listOf(7, 8, 9), tracer.getEvents().map { it.eventCount })
    }
}
