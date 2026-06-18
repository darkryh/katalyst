package io.github.darkryh.katalyst.transactions.metrics

import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsRetentionTest {
    @Test
    fun `transaction metrics remain within configured capacity`() {
        val collector = DefaultMetricsCollector(maxTransactions = 3)

        repeat(10) { index -> collector.startTransaction("transaction-$index") }

        assertEquals(3, collector.getTransactionCount())
    }

    @Test
    fun `concurrent transaction starts remain within configured capacity`() = runBlocking {
        val collector = DefaultMetricsCollector(maxTransactions = 16)

        (0 until 1_000).map { index ->
            async(Dispatchers.Default) { collector.startTransaction("transaction-$index") }
        }.awaitAll()

        assertEquals(16, collector.getTransactionCount())
    }

    @Test
    fun `errors and stack traces are capped`() {
        val collector = DefaultMetricsCollector(
            maxErrorsPerTransaction = 2,
            maxStackTraceChars = 64,
        )
        collector.startTransaction("transaction")

        repeat(10) {
            collector.recordError("transaction", TransactionPhase.BEFORE_COMMIT, RuntimeException("failure-$it"), false)
        }

        val errors = collector.getMetrics("transaction")!!.errors
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.stackTrace.length <= 64 })
    }
}
