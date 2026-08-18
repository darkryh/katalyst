package io.github.darkryh.katalyst.ktor.telemetry

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [HttpTelemetry] is a process-global `object` with no reset hook, and it stays that way: it is a
 * side-channel written by every request in the JVM, so a reset method would exist only for tests and
 * would be a live footgun in production. Every assertion here is therefore a **delta** — snapshot the
 * counters, act, assert what changed — which is also the only form that would survive the counters
 * being fed by an unrelated test running before this one.
 *
 * What is worth pinning down: the status-class bucketing (including the codes that fall outside
 * 2xx..5xx and the `null` status of a request that never produced a response), the two explicitly
 * recorded events (`recordAbort`, `recordException`), the in-flight gauge returning to where it
 * started — a leaked in-flight slot is permanent and makes the gauge useless for the rest of the
 * process — and the documented memory bound: "a fixed ~6-entry status-class map".
 */
class HttpTelemetryTest {

    /** The complete set of keys the status-class map is allowed to contain. */
    private val statusClasses = setOf("2xx", "3xx", "4xx", "5xx", "other")

    private fun statusDelta(before: Map<String, Long>, after: Map<String, Long>, key: String): Long =
        (after[key] ?: 0L) - (before[key] ?: 0L)

    // ==================== COUNTERS ====================

    @Test
    fun `every response is counted into its status class`() {
        val before = HttpTelemetry.statusClassCounts()

        listOf(200, 204, 299, 301, 404, 499, 500, 503).forEach {
            HttpTelemetry.onStart()
            HttpTelemetry.onComplete(it, 1)
        }
        // A request that never produced a response, and a code outside 2xx..5xx.
        HttpTelemetry.onStart()
        HttpTelemetry.onComplete(null, 1)
        HttpTelemetry.onStart()
        HttpTelemetry.onComplete(101, 1)

        val after = HttpTelemetry.statusClassCounts()
        assertEquals(3L, statusDelta(before, after, "2xx"), "200/204/299")
        assertEquals(1L, statusDelta(before, after, "3xx"), "301")
        assertEquals(2L, statusDelta(before, after, "4xx"), "404/499")
        assertEquals(2L, statusDelta(before, after, "5xx"), "500/503")
        assertEquals(
            2L,
            statusDelta(before, after, "other"),
            "a null status and a code outside 2xx..5xx both belong in 'other'",
        )
    }

    @Test
    fun `recordAbort counts middleware short-circuits without touching the other counters`() {
        val abortsBefore = HttpTelemetry.abortedByMiddleware.get()
        val exceptionsBefore = HttpTelemetry.exceptionsSeen.get()
        val totalBefore = HttpTelemetry.total.get()

        repeat(7) { HttpTelemetry.recordAbort() }

        assertEquals(7L, HttpTelemetry.abortedByMiddleware.get() - abortsBefore)
        assertEquals(
            exceptionsBefore,
            HttpTelemetry.exceptionsSeen.get(),
            "an abort is a policy decision, not a failure",
        )
        assertEquals(totalBefore, HttpTelemetry.total.get(), "recordAbort must not invent throughput")
    }

    @Test
    fun `recordException counts failures without touching the other counters`() {
        val exceptionsBefore = HttpTelemetry.exceptionsSeen.get()
        val abortsBefore = HttpTelemetry.abortedByMiddleware.get()
        val inFlightBefore = HttpTelemetry.inFlight.get()

        repeat(5) { HttpTelemetry.recordException() }

        assertEquals(5L, HttpTelemetry.exceptionsSeen.get() - exceptionsBefore)
        assertEquals(abortsBefore, HttpTelemetry.abortedByMiddleware.get())
        assertEquals(
            inFlightBefore,
            HttpTelemetry.inFlight.get(),
            "recording a failure must not release an in-flight slot — onComplete does that",
        )
    }

    // ==================== THE IN-FLIGHT GAUGE ====================

    @Test
    fun `in-flight rises with open requests and falls back as they complete`() {
        val before = HttpTelemetry.inFlight.get()

        repeat(50) { HttpTelemetry.onStart() }
        assertEquals(50, HttpTelemetry.inFlight.get() - before, "50 requests are open")

        repeat(50) { HttpTelemetry.onComplete(200, 3) }
        assertEquals(before, HttpTelemetry.inFlight.get(), "every completion releases its slot")
    }

    @Test
    fun `in-flight clamps at zero rather than going negative`() {
        val before = HttpTelemetry.inFlight.get()

        // More completions than there are open requests — the shape produced by a completion that
        // fires twice, or by telemetry installed mid-flight.
        repeat(before + 25) { HttpTelemetry.onComplete(null, 0) }

        assertEquals(
            0,
            HttpTelemetry.inFlight.get(),
            "a negative in-flight gauge would never recover; it must floor at zero",
        )

        // Put the gauge back exactly where this test found it.
        repeat(before) { HttpTelemetry.onStart() }
        assertEquals(before, HttpTelemetry.inFlight.get())
    }

    @Test
    fun `in-flight returns to its starting value under concurrent traffic`() {
        val inFlightBefore = HttpTelemetry.inFlight.get()
        val totalBefore = HttpTelemetry.total.get()
        val workers = 8
        val perWorker = 5_000

        val threads = (0 until workers).map {
            Thread {
                repeat(perWorker) {
                    HttpTelemetry.onStart()
                    HttpTelemetry.onComplete(200, Random.nextLong(0, 50))
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(
            inFlightBefore,
            HttpTelemetry.inFlight.get(),
            "${workers * perWorker} concurrent request lifecycles must leak no in-flight slot",
        )
        assertEquals(
            (workers * perWorker).toLong(),
            HttpTelemetry.total.get() - totalBefore,
            "throughput must be exact under concurrency",
        )
    }

    // ==================== BOUNDS ====================

    @Test
    fun `the status-class map holds a fixed handful of keys for any status code`() {
        // Generative: hammer the bucketing with arbitrary codes, including nonsense ones, and prove
        // the map's key space is the documented fixed set rather than the caller's input space.
        repeat(100_000) {
            HttpTelemetry.onStart()
            val code = if (Random.nextInt(10) == 0) null else Random.nextInt(-10_000, 10_000)
            HttpTelemetry.onComplete(code, Random.nextLong(0, 400_000))
        }

        val keys = HttpTelemetry.statusClassCounts().keys
        assertTrue(
            keys.all { it in statusClasses },
            "the status-class map must never key on caller input; got $keys",
        )
        assertTrue(keys.size <= statusClasses.size, "at most ${statusClasses.size} keys, got $keys")
    }

    @Test
    fun `latency stats stay ordered and keep the true max no matter how many requests arrive`() {
        repeat(50_000) {
            HttpTelemetry.onStart()
            HttpTelemetry.onComplete(200, Random.nextLong(0, 1_000))
        }
        HttpTelemetry.onStart()
        HttpTelemetry.onComplete(200, 250_000)

        val (p50, p95, max) = HttpTelemetry.latencyStats()
        assertTrue(p50 <= p95, "percentiles must be monotonic, got p50=$p50 p95=$p95")
        assertTrue(
            max >= 250_000.0,
            "the histogram is bucketed but the max is exact; got $max",
        )
    }

    // ==================== THE INSTALLED INTERCEPTOR ====================

    @Test
    fun `the installed interceptor records throughput, status classes and exceptions`() = testApplication {
        val totalBefore = HttpTelemetry.total.get()
        val inFlightBefore = HttpTelemetry.inFlight.get()
        val exceptionsBefore = HttpTelemetry.exceptionsSeen.get()
        val statusBefore = HttpTelemetry.statusClassCounts()

        application {
            installKatalystHttpTelemetry(this)
            routing {
                get("/ok") { call.respondText("ok") }
                get("/missing") { call.respond(HttpStatusCode.NotFound) }
                get("/boom") { throw IllegalStateException("handler bug") }
            }
        }

        client.get("/ok")
        client.get("/missing")
        runCatching { client.get("/boom") }

        val statusAfter = HttpTelemetry.statusClassCounts()
        assertEquals(3L, HttpTelemetry.total.get() - totalBefore, "three requests entered the pipeline")
        assertEquals(
            inFlightBefore,
            HttpTelemetry.inFlight.get(),
            "the finally block must release the in-flight slot even for the failing request",
        )
        assertEquals(
            1L,
            HttpTelemetry.exceptionsSeen.get() - exceptionsBefore,
            "exactly one handler threw",
        )
        assertEquals(1L, statusDelta(statusBefore, statusAfter, "2xx"), "/ok")
        assertEquals(1L, statusDelta(statusBefore, statusAfter, "4xx"), "/missing")
    }

    @Test
    fun `the installed interceptor re-throws the downstream failure unchanged`() = testApplication {
        val boom = IllegalStateException("must reach the caller unchanged")

        application {
            installKatalystHttpTelemetry(this)
            routing {
                get("/rethrow") { throw boom }
            }
        }

        val result = runCatching { client.get("/rethrow") }
        val cause = generateSequence(result.exceptionOrNull()) { it.cause }.firstOrNull { it === boom }
        // Ktor may translate the failure into a 500 rather than surfacing it to the client; either
        // way telemetry must not have swallowed or replaced it.
        if (result.isFailure) {
            assertTrue(cause === boom, "telemetry replaced the downstream failure: ${result.exceptionOrNull()}")
        } else {
            assertEquals(
                HttpStatusCode.InternalServerError,
                result.getOrThrow().status,
                "an unhandled handler failure must still surface as a 500",
            )
        }
    }
}
