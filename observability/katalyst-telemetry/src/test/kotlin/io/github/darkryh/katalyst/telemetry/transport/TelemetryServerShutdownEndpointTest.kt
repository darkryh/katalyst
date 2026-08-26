package io.github.darkryh.katalyst.telemetry.transport

import io.github.darkryh.katalyst.core.lifecycle.ApplicationShutdown
import io.github.darkryh.katalyst.telemetry.store.TelemetryIdentity
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `POST /shutdown` is the only endpoint on the telemetry transport that *changes* anything, so every
 * way it can be refused is pinned here, over a real socket.
 *
 * The gate matters as much as the happy path. This transport is loopback-only and token-gated
 * because a snapshot exposes the application's internals; adding "and stop me" to that surface is
 * only defensible while the gate holds. So: no token is a 401 and must not stop anything, a
 * disabled switch is a 403, and an application that never published a shutdown action is a 503
 * rather than a silent success.
 *
 * Driven with the JDK's HTTP client rather than Ktor's: this module has no client dependency, and
 * the test is about status codes on the wire, not about how they are read.
 */
class TelemetryServerShutdownEndpointTest {

    private val token = "test-token"
    private var server: TelemetryServer? = null

    private val http: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    @AfterTest
    fun tearDown() {
        server?.stop()
        server = null
        // Process-global: leaving an action installed would make the next test's 503 case pass for
        // the wrong reason, or worse, stop something real.
        ApplicationShutdown.uninstall()
    }

    private fun start(shutdownControlEnabled: Boolean = true): Int {
        val store = TelemetryStore(
            TelemetryIdentity(
                appName = "shutdown-endpoint-test",
                pid = ProcessHandle.current().pid(),
                katalystVersion = "test",
                startedAtEpochMs = System.currentTimeMillis(),
                host = "127.0.0.1",
                port = 0,
                snapshotPath = null,
                memoryBudgetBytes = 1024 * 1024,
            )
        )
        val telemetryServer = TelemetryServer(
            store = store,
            host = "127.0.0.1",
            requestedPort = 0,
            wsToken = token,
            shutdownControlEnabled = shutdownControlEnabled,
        )
        server = telemetryServer
        return requireNotNull(telemetryServer.start()) { "telemetry transport did not bind" }
    }

    private fun postShutdown(port: Int, token: String?): HttpResponse<String> {
        val query = token?.let { "?token=$it" } ?: ""
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/shutdown$query"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `accepts a token-bearing request and runs the installed shutdown`() {
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("test server") { ran.countDown() }
        val port = start()

        val response = postShutdown(port, token)

        // 202, not 200: the transport dies with the application it just agreed to stop, so it can
        // only ever report that the request was taken.
        assertEquals(202, response.statusCode())
        assertTrue(ran.await(5, TimeUnit.SECONDS), "the shutdown action never ran")
    }

    @Test
    fun `the response is written before the shutdown takes the server down`() {
        // The action blocks, standing in for a real drain. If the endpoint asked before responding,
        // the client would be left waiting on a socket the shutdown is closing.
        val release = CountDownLatch(1)
        ApplicationShutdown.install("slow server") { release.await() }
        val port = start()

        val response = postShutdown(port, token)

        assertEquals(202, response.statusCode())
        assertEquals("stopping", response.body())
        release.countDown()
    }

    @Test
    fun `rejects a request with no token and stops nothing`() {
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("test server") { ran.countDown() }
        val port = start()

        val response = postShutdown(port, token = null)

        assertEquals(401, response.statusCode())
        assertFalse(ran.await(500, TimeUnit.MILLISECONDS), "an unauthorized request stopped the application")
        assertFalse(ApplicationShutdown.isRequested)
    }

    @Test
    fun `rejects a request with the wrong token and stops nothing`() {
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("test server") { ran.countDown() }
        val port = start()

        val response = postShutdown(port, token = "not-the-token")

        assertEquals(401, response.statusCode())
        assertFalse(ran.await(500, TimeUnit.MILLISECONDS))
        assertFalse(ApplicationShutdown.isRequested)
    }

    @Test
    fun `refuses when shutdown control is switched off`() {
        val ran = CountDownLatch(1)
        ApplicationShutdown.install("test server") { ran.countDown() }
        val port = start(shutdownControlEnabled = false)

        val response = postShutdown(port, token)

        // 403 and not 404: the endpoint exists and the caller was allowed to ask — the operator has
        // turned the capability off. The inspector reports that distinctly.
        assertEquals(403, response.statusCode())
        assertFalse(ran.await(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `reports unsupported when the application published no shutdown action`() {
        val port = start()

        val response = postShutdown(port, token)

        assertEquals(503, response.statusCode())
    }

    @Test
    fun `a disabled endpoint still serves snapshots`() {
        val port = start(shutdownControlEnabled = false)

        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/snapshot?token=$token"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
    }
}
