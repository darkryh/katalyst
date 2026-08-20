package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.transactions.config.BackoffStrategy
import io.github.darkryh.katalyst.transactions.config.RetryPolicy
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.exception.TransactionTimeoutException
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager as JdbcTxManager
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Phase 2 — resource-exhaustion edge: a transaction that exceeds its timeout must **release its
 * pooled connection**, not leak it. We time out several transactions in a row against a tiny pool
 * and assert the pool drains back to idle and remains usable — a leak here would exhaust the pool
 * after `maxPoolSize` timeouts and wedge the application.
 */
class TransactionTimeoutConnectionReleaseTest {

    @Test
    fun `timed-out transactions release their connections and the pool stays usable`() = runBlocking {
        factory(poolSize = 3).use { factory ->
            val manager = DatabaseTransactionManager(factory.database)
            val config = TransactionConfig(
                timeout = 50.milliseconds,
                retryPolicy = RetryPolicy(maxRetries = 0, backoffStrategy = BackoffStrategy.IMMEDIATE)
            )

            // Time out more transactions than the pool has connections: a per-timeout leak would
            // exhaust the pool well before this loop finishes.
            repeat(10) {
                assertFailsWith<TransactionTimeoutException> {
                    manager.transaction(config = config) {
                        delay(500) // far exceeds the 50ms timeout
                    }
                }
            }

            assertNoLeak(factory)

            // The pool must still be fully functional after all those timeouts.
            val ok = manager.transaction {
                JdbcTxManager.current().exec("SELECT 1")
                "ok"
            }
            assertTrue(ok == "ok", "pool unusable after timeouts")
            assertNoLeak(factory)
        }
    }

    private suspend fun assertNoLeak(factory: DatabaseFactory) {
        var snapshot = factory.poolSnapshot()
        repeat(50) {
            if (snapshot.active == 0 && snapshot.pending == 0) return
            delay(20)
            snapshot = factory.poolSnapshot()
        }
        assertTrue(snapshot.active == 0 && snapshot.pending == 0, "connection leak after timeout: $snapshot")
    }

    private fun factory(poolSize: Int): DatabaseFactory =
        DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:tx_timeout_${System.nanoTime()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                maxPoolSize = poolSize
            )
        )
}
