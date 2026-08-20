package io.github.darkryh.katalyst.transactions.manager

import io.github.darkryh.katalyst.transactions.config.RetryPolicy
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager as JdbcTxManager
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Real, DB-backed concurrency-correctness tests for [DatabaseTransactionManager].
 *
 * These run many `manager.transaction { }` blocks in parallel on [Dispatchers.Default] against a
 * shared in-memory H2 database and assert *durable-state invariants* — every committed transaction
 * persisted, every rolled-back transaction left nothing behind, and no transaction was lost to a
 * race or deadlock. There are no wall-clock assertions, so the suite is deterministic and runs as
 * part of the default CI gate.
 */
class DatabaseTransactionManagerConcurrencyTest {

    private companion object {
        const val WORKERS = 8
        const val PER_WORKER = 25 // 200 concurrent transactions total
    }

    @Test
    fun `concurrent independent transactions all commit and persist exactly once`() = runBlocking {
        val database = testDatabase("concurrent_commits")
        val manager = DatabaseTransactionManager(database)
        manager.transaction {
            JdbcTxManager.current().exec("CREATE TABLE ledger (entry VARCHAR(64) NOT NULL)")
        }

        parallel(WORKERS) { worker ->
            repeat(PER_WORKER) { i ->
                manager.transaction {
                    JdbcTxManager.current().exec("INSERT INTO ledger (entry) VALUES ('w$worker-e$i')")
                }
            }
        }

        // No lost writes under contention: every committed insert is durable.
        assertEquals(WORKERS * PER_WORKER, manager.rowCount("ledger"))
    }

    @Test
    fun `rolled-back transactions leave no partial state while committed ones persist`() = runBlocking {
        val database = testDatabase("concurrent_rollbacks")
        val manager = DatabaseTransactionManager(database)
        manager.transaction {
            JdbcTxManager.current().exec("CREATE TABLE ledger (entry VARCHAR(64) NOT NULL)")
        }
        val committed = AtomicInteger(0)

        parallel(WORKERS) { worker ->
            repeat(PER_WORKER) { i ->
                val shouldFail = i % 2 == 0
                runCatching {
                    manager.transaction(config = TransactionConfig(retryPolicy = RetryPolicy(maxRetries = 0))) {
                        JdbcTxManager.current().exec("INSERT INTO ledger (entry) VALUES ('w$worker-e$i')")
                        // Throwing after the write must roll the insert back atomically.
                        if (shouldFail) error("forced rollback")
                    }
                }.onSuccess { committed.incrementAndGet() }
            }
        }

        // Derived from the same predicate to avoid off-by-one: only odd `i` commit.
        val committedPerWorker = (0 until PER_WORKER).count { it % 2 != 0 }
        val expectedCommitted = WORKERS * committedPerWorker
        // Isolation under concurrency: the table holds exactly the committed half, nothing partial.
        assertEquals(expectedCommitted, committed.get())
        assertEquals(expectedCommitted, manager.rowCount("ledger"))
    }

    private suspend fun DatabaseTransactionManager.rowCount(table: String): Int =
        transaction {
            JdbcTxManager.current().exec("SELECT COUNT(*) AS cnt FROM $table") { rs: ResultSet ->
                if (rs.next()) rs.getInt("cnt") else 0
            }
        } ?: 0

    private suspend fun parallel(count: Int, block: suspend (worker: Int) -> Unit) =
        coroutineScope {
            (0 until count).map { worker ->
                async(Dispatchers.Default) { block(worker) }
            }.awaitAll()
        }

    private fun testDatabase(name: String): Database =
        Database.connect(
            url = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
}
