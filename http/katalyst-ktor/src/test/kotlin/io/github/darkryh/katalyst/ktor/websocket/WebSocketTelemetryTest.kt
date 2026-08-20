package io.github.darkryh.katalyst.ktor.websocket

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.ktor.TestKatalystContainer
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The memory bounds of the process-global WebSocket registry, and the one judgement call it makes.
 *
 * [WebSocketTelemetry] is deliberately reset-free — it is written by every live socket in the JVM,
 * so every assertion here is a delta against a snapshot taken at the top of the test, and every
 * test hands the global counters back the way it found them.
 *
 * The registry documents three caps, and each is a different kind of promise:
 *  - `MAX_ROUTES = 128` bounds app-shape cardinality (how many socket routes were declared);
 *  - `MAX_TRACKED_SESSIONS = 250` bounds the *itemized* sessions while every session is still
 *    counted — the distinction that makes the registry safe at a million concurrent users;
 *  - `MAX_CLOSE_CODES = 16` bounds the outcome key space, folding the rest into "other".
 *
 * The judgement call lives in [KatalystWebSocketRoutes.webSocket]: a `CancellationException` is a
 * client disconnect or a server shutdown, not a handler bug. Counting it as a handler error would
 * make `handlerErrors` track connection churn instead of defects, which is precisely backwards.
 */
class WebSocketTelemetryTest {

    @BeforeTest
    fun setUp() {
        KatalystContainerProvider.reset()
    }

    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
    }

    private fun enableWebSockets() {
        KatalystContainerProvider.set(
            TestKatalystContainer(
                mapOf(
                    TestKatalystContainer.Key(Boolean::class, "enableWebSockets") to true,
                    TestKatalystContainer.Key(WebSocketOptions::class) to WebSocketOptions(),
                ),
            ),
        )
    }

    /**
     * The server records the close in a `finally` on its own coroutine, so it can land after the
     * client call returns. Wait for it rather than racing it.
     */
    private fun awaitClosed(baseline: Long, expected: Long, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (WebSocketTelemetry.closed.get() - baseline < expected &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(5)
        }
        assertEquals(
            expected,
            WebSocketTelemetry.closed.get() - baseline,
            "the server never recorded the session close",
        )
    }

    // ==================== BOUNDS ====================

    @Test
    fun `route registration stops at the documented cap`() {
        // Far past any plausible app shape: 5_000 declared socket routes into a 128-slot registry.
        repeat(5_000) { WebSocketTelemetry.registerRoute("/ws/flood-$it") }

        assertEquals(
            128,
            WebSocketTelemetry.routes().size,
            "MAX_ROUTES = 128 — the route registry must saturate, never grow with the key space",
        )
    }

    @Test
    fun `session tracking stops at the documented cap while every session is still counted`() {
        val openedBefore = WebSocketTelemetry.opened.get()
        val closedBefore = WebSocketTelemetry.closed.get()
        val activeBefore = WebSocketTelemetry.active.get()
        val trackedBefore = WebSocketTelemetry.sessions().size
        assertTrue(trackedBefore <= 250, "precondition: the registry starts within its own cap")

        val sessions = 4_000
        val stats = (0 until sessions).map { WebSocketTelemetry.sessionOpened("/ws/cap", "10.0.0.$it") }

        assertTrue(
            WebSocketTelemetry.sessions().size <= 250,
            "MAX_TRACKED_SESSIONS = 250 — itemized stats must not grow with load; got " +
                "${WebSocketTelemetry.sessions().size}",
        )
        assertEquals(
            250 - trackedBefore,
            stats.count { it != null },
            "the registry itemizes exactly up to its cap and then returns null",
        )
        assertEquals(
            sessions.toLong(),
            WebSocketTelemetry.opened.get() - openedBefore,
            "every session must be COUNTED even when it is not itemized — that is the whole point " +
                "of separating the counters from the tracked map",
        )
        assertEquals(sessions, WebSocketTelemetry.active.get() - activeBefore)

        stats.forEachIndexed { i, stat ->
            WebSocketTelemetry.sessionClosed("/ws/cap", stat, if (i % 2 == 0) "normal" else "error")
        }

        assertEquals(activeBefore, WebSocketTelemetry.active.get(), "closing must release every slot")
        assertEquals(sessions.toLong(), WebSocketTelemetry.closed.get() - closedBefore)
        assertEquals(
            trackedBefore,
            WebSocketTelemetry.sessions().size,
            "closing must release the tracked slots, or the cap becomes a permanent ceiling",
        )
        assertFalse(
            WebSocketTelemetry.sessionsPerRoute().containsKey("/ws/cap"),
            "a route with no live session must drop out of the per-route view",
        )
    }

    @Test
    fun `close outcomes cap at sixteen keys and fold the rest into other`() {
        val before = WebSocketTelemetry.closeOutcomeCounts()
        val totalBefore = before.values.sum()
        val otherBefore = before["other"] ?: 0L

        // A close code per session: exactly the unbounded key space the cap exists for.
        repeat(1_000) {
            val stat = WebSocketTelemetry.sessionOpened("/ws/outcomes", null)
            WebSocketTelemetry.sessionClosed("/ws/outcomes", stat, "close-code-$it")
        }

        val after = WebSocketTelemetry.closeOutcomeCounts()
        assertTrue(
            after.size <= 16,
            "MAX_CLOSE_CODES = 16 — got ${after.size} keys: ${after.keys}",
        )
        assertTrue(
            (after["other"] ?: 0L) > otherBefore,
            "outcomes past the cap must fold into 'other', not be dropped",
        )
        assertEquals(
            1_000L,
            after.values.sum() - totalBefore,
            "folding must merge counts into 'other', never lose them",
        )
    }

    @Test
    fun `session tracking stays capped when sessions are opened concurrently at the boundary`() {
        val trackedBefore = WebSocketTelemetry.sessions().size
        val opened = java.util.Collections.synchronizedList(mutableListOf<WebSocketTelemetry.SessionStat?>())

        // Park the registry one slot below the cap, then have everybody cross it at once.
        repeat(250 - trackedBefore - 1) { opened += WebSocketTelemetry.sessionOpened("/ws/boundary", null) }

        val racers = 32
        val start = java.util.concurrent.CountDownLatch(1)
        val threads = (0 until racers).map {
            Thread {
                start.await()
                opened += WebSocketTelemetry.sessionOpened("/ws/boundary", null)
            }
        }
        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach(Thread::join)

        val trackedAtPeak = WebSocketTelemetry.sessions().size
        // Hand the global registry back before asserting, whatever the outcome.
        opened.forEach { WebSocketTelemetry.sessionClosed("/ws/boundary", it, "normal") }

        assertTrue(
            trackedAtPeak <= 250,
            "`tracked.size >= MAX_TRACKED_SESSIONS` is check-then-act on a ConcurrentHashMap, so " +
                "$racers threads crossing the boundary together must not each be itemized; got $trackedAtPeak",
        )
        assertEquals(trackedBefore, WebSocketTelemetry.sessions().size)
    }

    @Test
    fun `close outcomes stay capped for any random key space`() {
        // Generative: random outcome strings, so the bound does not depend on the shape of the keys.
        repeat(2_000) {
            val outcome = "o-" + Random.nextInt(0, 1_000_000)
            val stat = WebSocketTelemetry.sessionOpened("/ws/random-outcomes", null)
            WebSocketTelemetry.sessionClosed("/ws/random-outcomes", stat, outcome)
        }

        assertTrue(
            WebSocketTelemetry.closeOutcomeCounts().size <= 16,
            "got ${WebSocketTelemetry.closeOutcomeCounts().keys}",
        )
    }

    // ==================== COUNTERS ====================

    @Test
    fun `a non-normal outcome is the only thing that counts as a handler error`() {
        val errorsBefore = WebSocketTelemetry.handlerErrors.get()

        repeat(10) {
            val stat = WebSocketTelemetry.sessionOpened("/ws/outcome-kind", null)
            WebSocketTelemetry.sessionClosed("/ws/outcome-kind", stat, "normal")
        }
        assertEquals(
            errorsBefore,
            WebSocketTelemetry.handlerErrors.get(),
            "a clean close is not a handler error",
        )

        repeat(3) {
            val stat = WebSocketTelemetry.sessionOpened("/ws/outcome-kind", null)
            WebSocketTelemetry.sessionClosed("/ws/outcome-kind", stat, "error")
        }
        assertEquals(3L, WebSocketTelemetry.handlerErrors.get() - errorsBefore)
    }

    @Test
    fun `active returns to its starting value under concurrent sessions`() {
        val activeBefore = WebSocketTelemetry.active.get()
        val openedBefore = WebSocketTelemetry.opened.get()
        val closedBefore = WebSocketTelemetry.closed.get()
        val workers = 8
        val perWorker = 2_000

        val threads = (0 until workers).map { t ->
            Thread {
                repeat(perWorker) {
                    val stat = WebSocketTelemetry.sessionOpened("/ws/race-$t", null)
                    WebSocketTelemetry.sessionClosed("/ws/race-$t", stat, "normal")
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(
            activeBefore,
            WebSocketTelemetry.active.get(),
            "${workers * perWorker} concurrent session lifecycles must leak no active slot",
        )
        assertEquals((workers * perWorker).toLong(), WebSocketTelemetry.opened.get() - openedBefore)
        assertEquals((workers * perWorker).toLong(), WebSocketTelemetry.closed.get() - closedBefore)
        assertTrue(
            WebSocketTelemetry.sessions().size <= 250,
            "the tracked map must stay capped under concurrency; got ${WebSocketTelemetry.sessions().size}",
        )
    }

    @Test
    fun `sessionsPerRoute reports only routes with live sessions`() {
        val a = WebSocketTelemetry.sessionOpened("/ws/live", null)
        val b = WebSocketTelemetry.sessionOpened("/ws/live", null)
        WebSocketTelemetry.sessionOpened("/ws/live", null).let {
            WebSocketTelemetry.sessionClosed("/ws/live", it, "normal")
        }

        assertEquals(2, WebSocketTelemetry.sessionsPerRoute()["/ws/live"])

        WebSocketTelemetry.sessionClosed("/ws/live", a, "normal")
        WebSocketTelemetry.sessionClosed("/ws/live", b, "normal")

        assertFalse(WebSocketTelemetry.sessionsPerRoute().containsKey("/ws/live"))
    }

    // ==================== THE INSTRUMENTED HANDLER ====================

    @Test
    fun `a live session is opened, counted and released by the instrumented handler`() = testApplication {
        enableWebSockets()
        application {
            install(WebSockets)
            routing {
                katalystWebSockets {
                    webSocket("/ws/greeter") {
                        send(Frame.Text("hello"))
                        close()
                    }
                }
            }
        }
        val client = createClient { install(ClientWebSockets) }

        val openedBefore = WebSocketTelemetry.opened.get()
        val closedBefore = WebSocketTelemetry.closed.get()
        val activeBefore = WebSocketTelemetry.active.get()
        val errorsBefore = WebSocketTelemetry.handlerErrors.get()

        client.webSocket("/ws/greeter") { incoming.receiveCatching() }
        awaitClosed(closedBefore, 1)

        assertEquals(1L, WebSocketTelemetry.opened.get() - openedBefore)
        assertEquals(activeBefore, WebSocketTelemetry.active.get(), "the session must be released")
        assertEquals(errorsBefore, WebSocketTelemetry.handlerErrors.get(), "a clean session is not an error")
    }

    @Test
    fun `a handler that throws is counted as a handler error`() = testApplication {
        enableWebSockets()
        application {
            install(WebSockets)
            routing {
                katalystWebSockets {
                    webSocket("/ws/boom") { throw IllegalStateException("handler bug") }
                }
            }
        }
        val client = createClient { install(ClientWebSockets) }

        val errorsBefore = WebSocketTelemetry.handlerErrors.get()
        val closedBefore = WebSocketTelemetry.closed.get()
        val activeBefore = WebSocketTelemetry.active.get()

        runCatching { client.webSocket("/ws/boom") { incoming.receiveCatching() } }
        awaitClosed(closedBefore, 1)

        assertEquals(
            1L,
            WebSocketTelemetry.handlerErrors.get() - errorsBefore,
            "a handler that throws is exactly what handlerErrors is for",
        )
        assertEquals(activeBefore, WebSocketTelemetry.active.get(), "a failing session still releases its slot")
    }

    @Test
    fun `a cancelled session is a disconnect, not a handler error`() = testApplication {
        enableWebSockets()
        application {
            install(WebSockets)
            routing {
                katalystWebSockets {
                    // Exactly what a client disconnect or a server shutdown throws into a handler.
                    webSocket("/ws/cancelled") { throw CancellationException("peer went away") }
                }
            }
        }
        val client = createClient { install(ClientWebSockets) }

        val errorsBefore = WebSocketTelemetry.handlerErrors.get()
        val closedBefore = WebSocketTelemetry.closed.get()
        val activeBefore = WebSocketTelemetry.active.get()

        runCatching { client.webSocket("/ws/cancelled") { incoming.receiveCatching() } }
        awaitClosed(closedBefore, 1)

        assertEquals(
            errorsBefore,
            WebSocketTelemetry.handlerErrors.get(),
            "counting cancellations as handler errors would make the metric track connection churn " +
                "instead of defects — every disconnect would look like a bug",
        )
        assertEquals(activeBefore, WebSocketTelemetry.active.get(), "and the slot is still released")
    }
}
