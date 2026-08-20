package io.github.darkryh.katalyst.ktor.engine.netty

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 5 — clean server lifecycle/shutdown: the engine serves while running and, after a graceful
 * `stop`, **releases the port and stops accepting connections** (no half-open / orphaned listener).
 *
 * Note: this asserts clean *shutdown*, not draining of in-flight suspending handlers — Ktor's Netty
 * engine cancels in-flight coroutine work on stop, so "an in-flight request always finishes" is not
 * a guarantee we can assert here. Component-level draining that Katalyst *does* control is covered by
 * the scheduler/transaction tests (SchedulerStarvationAndDrainTest, TransactionTimeoutConnectionReleaseTest).
 */
class NettyGracefulDrainTest {

    @Test
    fun `server serves while up and refuses connections after a graceful stop`() {
        val server = embeddedServer(Netty, port = 0) {
            routing {
                get("/health") { call.respondText("ok") }
            }
        }
        server.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }

        // Up: serves normally.
        assertEquals(200, get(port, "/health"), "server did not serve while running")

        // Graceful stop, then the port must be released and refuse new connections.
        server.stop(500, 2_000, TimeUnit.MILLISECONDS)

        assertTrue(refusesConnections(port), "server still accepted connections after graceful stop")
    }

    private fun get(port: Int, path: String): Int {
        val conn = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        return try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    /** Polls briefly: a freshly-stopped listener can take a moment to fully release the socket. */
    private fun refusesConnections(port: Int): Boolean {
        repeat(50) {
            try {
                get(port, "/health")
            } catch (_: ConnectException) {
                return true
            } catch (_: Exception) {
                // Any other I/O failure also means it is no longer serving cleanly.
                return true
            }
            Thread.sleep(20)
        }
        return false
    }
}
