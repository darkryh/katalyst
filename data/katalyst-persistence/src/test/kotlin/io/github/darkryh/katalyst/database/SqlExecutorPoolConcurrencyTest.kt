package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Concurrency-correctness tests for pooled SQL execution via [DatabaseFactory] + [SqlExecutor].
 *
 * Runs concurrent CRUD through HikariCP against H2 and asserts durable-state invariants plus
 * **no connection leak** (the pool returns to zero active connections once work settles). No
 * wall-clock assertions, so the suite is deterministic.
 */
class SqlExecutorPoolConcurrencyTest {

    private companion object {
        const val WORKERS = 12
        const val PER_WORKER = 50 // 600 concurrent statements over a 6-connection pool
        const val POOL = 6
    }

    @Test
    fun `concurrent inserts through the pool all persist and leak no connections`() = runBlocking {
        factory(POOL).use { factory ->
            val sql = factory.createSqlExecutor()
            sql.executeUpdate("CREATE TABLE items (id INT AUTO_INCREMENT PRIMARY KEY, label VARCHAR(64))")

            parallel(WORKERS) { worker ->
                repeat(PER_WORKER) { i ->
                    sql.executeUpdate("INSERT INTO items (label) VALUES (?)", listOf("w$worker-e$i"))
                }
            }

            val count = sql.queryOne("SELECT COUNT(*) AS c FROM items") { it.getInt("c") } ?: -1
            assertEquals(WORKERS * PER_WORKER, count, "lost inserts under concurrent pooled writes")

            assertNoLeak(factory)
        }
    }

    @Test
    fun `concurrent read-modify-write on a counter row is serialized without lost updates`() = runBlocking {
        factory(POOL).use { factory ->
            val sql = factory.createSqlExecutor()
            sql.executeUpdate("CREATE TABLE counter (id INT PRIMARY KEY, total INT NOT NULL)")
            sql.executeUpdate("INSERT INTO counter (id, total) VALUES (1, 0)")

            val increments = WORKERS * PER_WORKER
            parallel(WORKERS) {
                repeat(PER_WORKER) {
                    // Atomic in-DB increment: the database, not the test, enforces no lost update.
                    // Under heavy single-row contention H2 raises a deadlock (SQLState 40001) instead
                    // of serializing transparently; retry until the increment commits.
                    retryOnDeadlock {
                        sql.executeUpdate("UPDATE counter SET total = total + 1 WHERE id = 1")
                    }
                }
            }

            val value = sql.queryOne("SELECT total AS v FROM counter WHERE id = 1") { it.getInt("v") } ?: -1
            assertEquals(increments, value, "lost updates under concurrent read-modify-write")
            assertNoLeak(factory)
        }
    }

    @Test
    fun `withConnection failures still return connections to the pool`() = runBlocking {
        factory(POOL).use { factory ->
            val sql = factory.createSqlExecutor()
            val failures = AtomicInteger(0)

            parallel(WORKERS) {
                repeat(PER_WORKER) {
                    runCatching {
                        sql.withConnection { conn ->
                            conn.createStatement().use { it.execute("SELECT 1") }
                            error("boom") // throw while holding a pooled connection
                        }
                    }.onFailure { failures.incrementAndGet() }
                }
            }

            assertEquals(WORKERS * PER_WORKER, failures.get(), "expected every block to throw")
            // The real assertion: thrown blocks must not leak — pool drains back to idle.
            assertNoLeak(factory)
        }
    }

    /** Retries [block] while the database reports a transient deadlock/serialization conflict. */
    private suspend fun retryOnDeadlock(block: suspend () -> Unit) {
        while (true) {
            try {
                block()
                return
            } catch (e: Throwable) {
                val deadlock = generateSequence<Throwable>(e) { it.cause }
                    .any { it.message?.contains("Deadlock", ignoreCase = true) == true }
                if (!deadlock) throw e
            }
        }
    }

    private fun assertNoLeak(factory: DatabaseFactory) {
        // Active connections must return to 0; give Hikari a brief moment to reclaim.
        var snapshot = factory.poolSnapshot()
        repeat(50) {
            if (snapshot.active == 0 && snapshot.pending == 0) return
            Thread.sleep(20)
            snapshot = factory.poolSnapshot()
        }
        assertTrue(
            snapshot.active == 0 && snapshot.pending == 0,
            "connection leak: $snapshot"
        )
    }

    private suspend fun parallel(count: Int, block: suspend (worker: Int) -> Unit) =
        coroutineScope {
            (0 until count).map { worker ->
                async(Dispatchers.Default) { block(worker) }
            }.awaitAll()
        }

    private fun factory(poolSize: Int): DatabaseFactory =
        DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:pool_conc_${System.nanoTime()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                maxPoolSize = poolSize
            )
        )
}
