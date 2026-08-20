package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Resource-exhaustion tests (Phase 2): drive far more concurrent work than the pool has
 * connections and assert the system **degrades gracefully** — requests queue and all complete,
 * the pool never exceeds its configured size, no deadlock, and connections are fully reclaimed
 * afterwards (no leak).
 */
class ConnectionPoolSaturationTest {

    private companion object {
        const val POOL = 4
        const val CONCURRENT = 40 // 10x the pool
    }

    @Test
    fun `demand far exceeding pool size still completes without deadlock or leak`() = runBlocking {
        factory(POOL).use { factory ->
            val sql = factory.createSqlExecutor()
            sql.executeUpdate("CREATE TABLE work (id INT AUTO_INCREMENT PRIMARY KEY, tag VARCHAR(32))")

            val completed = AtomicInteger(0)
            val peakActive = AtomicInteger(0)

            val finished = withTimeoutOrNull(30_000) {
                coroutineScope {
                    (0 until CONCURRENT).map { i ->
                        async(Dispatchers.Default) {
                            sql.withConnection { conn ->
                                // Hold the connection briefly so demand genuinely exceeds the pool.
                                conn.prepareStatement("INSERT INTO work (tag) VALUES (?)").use { ps ->
                                    ps.setString(1, "job-$i")
                                    ps.executeUpdate()
                                }
                                recordPeak(factory, peakActive)
                                Thread.sleep(20)
                            }
                            completed.incrementAndGet()
                        }
                    }.awaitAll()
                }
                true
            }

            assertTrue(finished == true, "pool saturation deadlocked / did not drain within timeout")
            assertEquals(CONCURRENT, completed.get(), "not every queued job completed")

            val rows = sql.queryOne("SELECT COUNT(*) AS c FROM work") { it.getInt("c") } ?: -1
            assertEquals(CONCURRENT, rows)

            // The pool must never have exceeded its configured ceiling...
            assertTrue(peakActive.get() <= POOL, "pool exceeded maxPoolSize: ${peakActive.get()} > $POOL")
            // ...and must reclaim every connection once the surge ends.
            assertNoLeak(factory)
        }
    }

    private fun recordPeak(factory: DatabaseFactory, peak: AtomicInteger) {
        val active = factory.poolSnapshot().active
        peak.updateAndGet { prev -> maxOf(prev, active) }
    }

    private suspend fun assertNoLeak(factory: DatabaseFactory) {
        var snapshot = factory.poolSnapshot()
        repeat(50) {
            if (snapshot.active == 0 && snapshot.pending == 0) return
            delay(20)
            snapshot = factory.poolSnapshot()
        }
        assertTrue(snapshot.active == 0 && snapshot.pending == 0, "connection leak after surge: $snapshot")
    }

    private fun factory(poolSize: Int): DatabaseFactory =
        DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:pool_sat_${System.nanoTime()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                maxPoolSize = poolSize,
                // Fail fast if the pool genuinely can't hand out a connection, so a real
                // starvation bug surfaces as a timeout instead of hanging the suite.
                connectionTimeout = 10_000L
            )
        )
}
