package io.github.darkryh.katalyst.database

import io.github.darkryh.katalyst.config.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins [DatabaseFactory.quiesce] — the last thing standing between a still-busy background worker
 * and a connection pool that is about to be closed underneath it.
 *
 * The guarantee is deliberately modest, and both halves of it matter: wait while the pool is still
 * being used, and stop waiting when that stops being true or takes too long. A quiesce that never
 * gave up would turn a noisy shutdown into one that never finishes.
 */
class DatabaseQuiesceTest {

    private val factories = mutableListOf<DatabaseFactory>()
    private val latches = mutableListOf<CountDownLatch>()
    private val threads = mutableListOf<Thread>()

    @AfterTest
    fun tearDown() {
        latches.forEach { it.countDown() }
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }
        factories.forEach { runCatching { it.close() } }
        factories.clear()
        latches.clear()
        threads.clear()
    }

    private fun freshFactory(): DatabaseFactory =
        DatabaseFactory.create(
            DatabaseConfig(
                url = "jdbc:h2:mem:quiesce_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = "",
                maxPoolSize = 4,
                minIdleConnections = 1,
            )
        ).also { factories += it }

    /**
     * Checks out a pooled connection and keeps it until the returned latch is released, the way an
     * in-flight statement holds one during a shutdown.
     */
    private fun DatabaseFactory.holdAConnection(): CountDownLatch {
        val release = CountDownLatch(1).also { latches += it }
        val acquired = CountDownLatch(1)
        val thread = Thread({
            transaction(database) {
                exec("SELECT 1")
                acquired.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }, "quiesce-test-connection-holder").apply { isDaemon = true }
        threads += thread
        thread.start()
        assertTrue(acquired.await(10, TimeUnit.SECONDS), "the holder never got a connection")
        return release
    }

    @Test
    fun `returns straight away when nothing is using the pool`() {
        val factory = freshFactory()

        // A long budget it must not spend: the assertion is "did not wait", with enough headroom
        // that a slow machine cannot turn it into "waited a bit".
        val result = factory.quiesce(30.seconds)

        assertTrue(result.drained)
        assertEquals(0, result.activeAtStart)
        assertTrue(result.waitedMillis < 5_000, "waited ${result.waitedMillis} ms for an idle pool")
    }

    @Test
    fun `waits for an in-flight connection and returns once it is handed back`() {
        // Driven by the connection's actual lifetime rather than by the clock. An earlier version
        // released on a timer and asserted quiesce had waited at least that long, which is only true
        // when the two threads are scheduled in the expected order - on a loaded machine the release
        // can land before quiesce even looks, and the test fails for a reason unrelated to quiesce.
        val factory = freshFactory()
        val release = factory.holdAConnection()

        val result = AtomicReference<DatabaseQuiesceResult?>(null)
        val quiescing = Thread({ result.set(factory.quiesce(30.seconds)) }, "quiesce-under-test")
            .apply { isDaemon = true }
        threads += quiescing
        quiescing.start()

        // A connection is genuinely checked out and stays that way until this test says otherwise,
        // so a correct quiesce cannot possibly be finished. Nothing here bounds how long it takes.
        Thread.sleep(200)
        assertTrue(quiescing.isAlive, "quiesce returned while a connection was still checked out")
        assertNull(result.get())

        release.countDown()
        quiescing.join(TimeUnit.SECONDS.toMillis(30))

        val outcome = assertNotNull(result.get(), "quiesce never returned after the connection came back")
        assertTrue(outcome.drained, "the pool went quiet but quiesce did not notice")
        assertTrue(outcome.activeAtStart >= 1, "the holder should have been counted as active")
        assertEquals(0, outcome.activeAtEnd)
    }

    @Test
    fun `gives up at the timeout and reports what is still using the pool`() {
        // The case the WARN exists for: something is still talking to the database and no amount of
        // waiting will change that. Bounded, and honest about what it found.
        val factory = freshFactory()
        factory.holdAConnection()

        val result = factory.quiesce(300.milliseconds)

        assertFalse(result.drained)
        assertTrue(result.activeAtEnd >= 1, "a connection is still checked out and must be reported")
        assertTrue(
            result.waitedMillis < 5_000,
            "quiesce waited ${result.waitedMillis} ms past a 300 ms budget",
        )
    }

    @Test
    fun `is a no-op once the factory is closed`() {
        val factory = freshFactory()
        factory.close()

        val result = factory.quiesce(30.seconds)

        assertTrue(result.drained)
        assertTrue(result.waitedMillis < 5_000, "waited ${result.waitedMillis} ms on a closed factory")
    }

    @Test
    fun `leaves the pool usable - it waits, it does not close anything`() {
        val factory = freshFactory()

        factory.quiesce(1.seconds)

        assertEquals(1, transaction(factory.database) { exec("SELECT 1") { rs -> rs.next(); rs.getInt(1) } })
        assertFalse(factory.poolSnapshot().closed)
    }
}
