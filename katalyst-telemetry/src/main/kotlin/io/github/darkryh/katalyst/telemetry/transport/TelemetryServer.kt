package io.github.darkryh.katalyst.telemetry.transport

import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Loopback-only telemetry transport. Serves the current [TelemetryStore] snapshot over
 * `GET /snapshot` (JSON) and pushes it periodically over `WS /stream?token=<wsToken>` (text frames).
 *
 * The server binds `127.0.0.1` only and never crashes the app: [start] wraps binding in a guard and
 * returns the actually-bound port (via `resolvedConnectors()`), or `null` if the transport failed.
 */
class TelemetryServer(
    private val store: TelemetryStore,
    private val host: String,
    private val requestedPort: Int,
    private val wsToken: String,
    private val pollIntervalMs: Long = 1_000L,
) {
    private val logger = LoggerFactory.getLogger("TelemetryServer")
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Volatile
    private var engine: EmbeddedServer<*, *>? = null

    @Volatile
    var boundPort: Int = 0
        private set

    private fun encode(): String =
        json.encodeToString(TelemetrySnapshot.serializer(), store.snapshot())

    /**
     * Enforce loopback in code: the snapshot exposes app internals, so a misconfigured routable host
     * (a common `0.0.0.0` reflex) must never win. A non-loopback request is refused and clamped.
     */
    private fun loopbackOrDefault(requested: String): String {
        val isLoopback = requested == "127.0.0.1" || requested == "::1" ||
            requested.equals("localhost", ignoreCase = true) ||
            runCatching { java.net.InetAddress.getByName(requested).isLoopbackAddress }.getOrDefault(false)
        if (!isLoopback) {
            logger.warn(
                "Telemetry host '{}' is not a loopback address; forcing 127.0.0.1 (telemetry is loopback-only)",
                requested,
            )
            return "127.0.0.1"
        }
        return requested
    }

    /**
     * Start the loopback transport. Returns the actually-bound port, or `null` if start failed.
     * A transport failure is logged and swallowed so it can never break application boot.
     */
    fun start(): Int? = runCatching {
        val effectiveHost = loopbackOrDefault(host)
        val server = embeddedServer(CIO, host = effectiveHost, port = requestedPort) {
            install(WebSockets)
            routing {
                get("/snapshot") {
                    // Same token gate as /stream — both serve the identical full snapshot.
                    if (call.request.queryParameters["token"] != wsToken) {
                        call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
                        return@get
                    }
                    call.respondText(encode(), ContentType.Application.Json)
                }
                webSocket("/stream") {
                    val token = call.request.queryParameters["token"]
                    if (token == null || token != wsToken) {
                        close()
                        return@webSocket
                    }
                    while (isActive) {
                        send(Frame.Text(encode()))
                        delay(pollIntervalMs)
                    }
                }
            }
        }
        server.start(wait = false)
        engine = server
        // Resolve the OS-assigned port (0 -> ephemeral). resolvedConnectors is suspend.
        val port = runBlocking { server.engine.resolvedConnectors() }.firstOrNull()?.port ?: requestedPort
        boundPort = port
        logger.info("Telemetry transport listening on {}:{}", effectiveHost, port)
        port
    }.onFailure { error ->
        logger.warn("Telemetry transport failed to start; telemetry disabled: {}", error.message)
    }.getOrNull()

    /** Stop the transport. Never throws. */
    fun stop() {
        runCatching { engine?.stop(200, 500) }
            .onFailure { logger.debug("Telemetry transport stop failed: {}", it.message) }
        engine = null
    }
}
