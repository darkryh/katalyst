package io.github.darkryh.katalyst.tui.attach

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.darkryh.katalyst.telemetry.model.DescriptorStatus
import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The half of `/shutdown` that lives on the wire: turning what a backend answered into something the
 * inspector can tell a human.
 *
 * [ShutdownCoordinatorTest] covers the decisions; this covers the translation, because that is where
 * a refusal silently becomes a success. Every status the transport can produce is exercised against
 * a real socket, plus the two a *different* backend could produce — a 404 from a build that predates
 * the endpoint, and nothing at all from one that is already gone.
 *
 * Served by the JDK's own HTTP server: this module has no server dependency, and standing up the
 * real transport here would test the transport rather than the client reading it.
 */
class TelemetryClientShutdownTest {

    private lateinit var server: HttpServer
    private lateinit var client: TelemetryClient
    private val shutdownStatus = AtomicReference(202)
    private val lastShutdownQuery = AtomicReference<String?>(null)
    private val snapshotStatus = AtomicReference(200)

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/shutdown") { exchange ->
            lastShutdownQuery.set(exchange.requestURI.query)
            respond(exchange, shutdownStatus.get(), exchange.requestMethod)
        }
        server.createContext("/snapshot") { exchange ->
            respond(exchange, snapshotStatus.get(), "{}")
        }
        server.start()
        client = TelemetryClient()
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.stop(0)
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun descriptor(port: Int = server.address.port) = RunDescriptor(
        appName = "TestApp",
        pid = 4242,
        katalystVersion = "test",
        host = "127.0.0.1",
        telemetryPort = port,
        wsToken = "token",
        snapshotPath = null,
        startedAtEpochMs = 0,
        status = DescriptorStatus.READY,
    )

    @Test
    fun `maps every status the transport can answer`() = runBlocking {
        mapOf(
            202 to ShutdownRequestOutcome.Accepted,
            401 to ShutdownRequestOutcome.Unauthorized,
            403 to ShutdownRequestOutcome.Disabled,
            503 to ShutdownRequestOutcome.Unsupported,
        ).forEach { (status, expected) ->
            shutdownStatus.set(status)

            assertEquals(expected, client.requestShutdown(descriptor()), "status $status")
        }
    }

    @Test
    fun `treats a backend without the endpoint as unsupported, not unreachable`() = runBlocking {
        // A 404 comes from a backend that is running and answering — it simply predates the
        // endpoint. Reporting it as unreachable would tell the user to go looking for a dead
        // process that is very much alive.
        shutdownStatus.set(404)

        assertEquals(ShutdownRequestOutcome.Unsupported, client.requestShutdown(descriptor()))
    }

    @Test
    fun `reports unreachable when nothing answers`() = runBlocking {
        // A port nothing is listening on: the backend crashed, or its transport is already down.
        val dead = descriptor(port = findClosedPort())

        assertEquals(ShutdownRequestOutcome.Unreachable, client.requestShutdown(dead))
    }

    @Test
    fun `sends the descriptor token so the transport can authorize it`() = runBlocking {
        shutdownStatus.set(202)

        client.requestShutdown(descriptor())

        assertEquals("token=token", lastShutdownQuery.get())
    }

    @Test
    fun `a live backend is reachable`() = runBlocking {
        assertTrue(client.isReachable(descriptor()))
    }

    @Test
    fun `a rejected token still counts as reachable`() = runBlocking {
        // Reachability answers "is something there", not "does it like us". Treating a 401 as gone
        // would make a stale token look like a completed shutdown.
        snapshotStatus.set(401)

        assertTrue(client.isReachable(descriptor()))
    }

    @Test
    fun `a backend that is not listening is not reachable`() = runBlocking {
        assertFalse(client.isReachable(descriptor(port = findClosedPort())))
    }

    /** A port that was bound long enough to be allocated, then released. */
    private fun findClosedPort(): Int =
        java.net.ServerSocket(0).use { it.localPort }
}
