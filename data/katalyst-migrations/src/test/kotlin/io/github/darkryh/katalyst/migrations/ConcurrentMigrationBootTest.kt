package io.github.darkryh.katalyst.migrations

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.migrations.options.MigrationOptions
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two instances booting against one database at the same time.
 *
 * This is the ordinary shape of a rolling deploy, and the runner has no lock: it reads the history,
 * decides what is pending, applies it, and records it, with nothing serialising the three steps
 * across processes. Nothing in the suite covered it, so what actually happens was unknown.
 *
 * These tests establish it. The invariant that matters to a user is **not** "only one instance runs
 * the body" — without a lock that cannot be guaranteed — but the weaker and still essential
 * "the database ends up correct exactly once": one history row, one set of effects, and any loser
 * failing cleanly rather than leaving a mess behind.
 *
 * The history table's primary key on `migration_id` is what enforces this, and it only works
 * because the body and the record now commit together: the loser's duplicate-key rejection rolls
 * its body back with it. Under the previous split-transaction runner the loser's body had already
 * committed by the time its record was rejected, so its effects survived — duplicated data with a
 * single history row to explain it.
 */
class ConcurrentMigrationBootTest {

    private lateinit var url: String
    private lateinit var keepAlive: DatabaseFactory

    @BeforeTest
    fun setup() {
        // One shared in-memory database, reachable from several independent factories. The
        // keep-alive factory holds it open for the duration of the test.
        url = "jdbc:h2:mem:katalyst-concurrent-${System.nanoTime()};DB_CLOSE_DELAY=-1"
        keepAlive = factory()
        transaction(keepAlive.database) { exec("CREATE TABLE payload (note VARCHAR(64))") }
    }

    @AfterTest
    fun tearDown() {
        runCatching { keepAlive.close() }
    }

    private fun factory() = DatabaseFactory.create(
        DatabaseConfig(url = url, driver = "org.h2.Driver", username = "sa", password = "")
    )

    private fun payloadCount(): Int = transaction(keepAlive.database) {
        var n = 0
        exec("SELECT COUNT(*) FROM payload") { rs -> rs.next(); n = rs.getInt(1) }
        n
    }

    private fun historyIds(): List<String> = transaction(keepAlive.database) {
        val ids = mutableListOf<String>()
        exec("SELECT migration_id FROM katalyst_schema_migrations") { rs ->
            while (rs.next()) ids += rs.getString(1)
        }
        ids
    }

    private class InsertsPayload(private val started: AtomicInteger) : KatalystMigration {
        override val id = "V1__inserts_payload"
        override fun up() {
            started.incrementAndGet()
            transaction { exec("INSERT INTO payload (note) VALUES ('applied')") }
        }
    }

    private class BaselineOnly(private val started: AtomicInteger) : KatalystMigration {
        override val id = "1_baseline_only"
        override fun up() {
            started.incrementAndGet()
        }
    }

    @Test
    fun `two instances booting together leave exactly one history row and one set of effects`() {
        val started = AtomicInteger(0)
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val failures = java.util.Collections.synchronizedList(mutableListOf<String>())

        val threads = (1..2).map {
            thread {
                val f = factory()
                try {
                    ready.countDown()
                    go.await(10, TimeUnit.SECONDS)
                    MigrationRunner(f, MigrationOptions()).runMigrations(
                        listOf(InsertsPayload(started))
                    )
                } catch (e: Exception) {
                    // A loser is allowed to fail — it must not corrupt anything.
                    failures += (e.message ?: e::class.simpleName ?: "unknown")
                } finally {
                    runCatching { f.close() }
                }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "both instances failed to reach the start line")
        go.countDown()
        threads.forEach { it.join(30_000) }

        assertEquals(
            listOf("V1__inserts_payload"),
            historyIds(),
            "the migration must be recorded exactly once, got ${historyIds()}",
        )
        assertEquals(
            1,
            payloadCount(),
            "the migration's effects must appear exactly once — a duplicate means the loser's " +
                "body committed despite losing the record. Bodies started: ${started.get()}, " +
                "failures: $failures",
        )
        assertTrue(failures.size <= 1, "at most one instance may fail, got $failures")
    }

    @Test
    fun `an instance booting after another has finished applies nothing and succeeds`() {
        // The sequential case, which is what a staggered rolling deploy actually looks like.
        val started = AtomicInteger(0)
        val first = factory()
        MigrationRunner(first, MigrationOptions()).runMigrations(listOf(InsertsPayload(started)))
        first.close()

        val second = factory()
        MigrationRunner(second, MigrationOptions()).runMigrations(listOf(InsertsPayload(started)))
        second.close()

        assertEquals(1, started.get(), "the second instance re-ran an already-applied migration")
        assertEquals(1, payloadCount(), "effects were duplicated by the second boot")
        assertEquals(listOf("V1__inserts_payload"), historyIds())
    }

    @Test
    fun `four instances booting together still converge on one applied migration`() {
        val started = AtomicInteger(0)
        val ready = CountDownLatch(4)
        val go = CountDownLatch(1)
        val failures = java.util.Collections.synchronizedList(mutableListOf<String>())

        val threads = (1..4).map {
            thread {
                val f = factory()
                try {
                    ready.countDown()
                    go.await(10, TimeUnit.SECONDS)
                    MigrationRunner(f, MigrationOptions()).runMigrations(
                        listOf(InsertsPayload(started))
                    )
                } catch (e: Exception) {
                    failures += (e.message ?: e::class.simpleName ?: "unknown")
                } finally {
                    runCatching { f.close() }
                }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS))
        go.countDown()
        threads.forEach { it.join(30_000) }

        assertEquals(listOf("V1__inserts_payload"), historyIds(), "recorded more than once")
        assertEquals(
            1,
            payloadCount(),
            "effects duplicated across ${started.get()} bodies; failures: $failures",
        )
    }

    @Test
    fun `concurrent baseline runners converge without executing the migration`() {
        val started = AtomicInteger(0)
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val failures = java.util.Collections.synchronizedList(mutableListOf<String>())

        val threads = (1..2).map {
            thread {
                val f = factory()
                try {
                    ready.countDown()
                    go.await(10, TimeUnit.SECONDS)
                    MigrationRunner(
                        f,
                        MigrationOptions(baselineVersion = "1_baseline_only"),
                    ).runMigrations(listOf(BaselineOnly(started)))
                } catch (e: Exception) {
                    failures += (e.message ?: e::class.simpleName ?: "unknown")
                } finally {
                    runCatching { f.close() }
                }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS))
        go.countDown()
        threads.forEach { it.join(30_000) }

        assertTrue(failures.isEmpty(), "both baseline runners should converge, got $failures")
        assertEquals(0, started.get(), "a baselined migration body must never execute")
        assertEquals(listOf("1_baseline_only"), historyIds())
        val status = transaction(keepAlive.database) {
            var value = ""
            exec("SELECT status FROM katalyst_schema_migrations WHERE migration_id = '1_baseline_only'") { rs ->
                rs.next()
                value = rs.getString(1)
            }
            value
        }
        assertEquals("BASELINED", status)
    }
}
