package io.github.darkryh.katalyst.tui.attach

import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.io.Closeable
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loopback attach client for a single running backend. Talks to the backend's 127.0.0.1-only
 * telemetry transport described by a [RunDescriptor]:
 *
 *  - `GET  http://host:port/snapshot?token=` -> one [TelemetrySnapshot]
 *  - `WS   ws://host:port/stream?token=`     -> periodic [TelemetrySnapshot] text frames
 *
 * Every call is fully guarded. A dead backend, a wrong token (401), a closed socket, or a malformed
 * body all resolve to `null` / a completed flow rather than an exception, so the view model can
 * surface a clean "detached" state.
 */
class TelemetryClient : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient(CIO) {
        install(WebSockets)
        // A backend that completes the TCP handshake but stalls the body must degrade to Detached
        // promptly rather than hang the poll on CIO's multi-second engine defaults.
        install(HttpTimeout) {
            connectTimeoutMillis = 1_000
            requestTimeoutMillis = 2_000
            socketTimeoutMillis = 2_000
        }
    }

    /** Fetch a single snapshot over HTTP. Returns `null` on any failure (dead backend, 401, bad body). */
    suspend fun fetchSnapshot(descriptor: RunDescriptor): TelemetrySnapshot? = runCatching {
        val url = "http://${descriptor.host}:${descriptor.telemetryPort}/snapshot?token=${descriptor.wsToken}"
        val response: HttpResponse = client.get(url)
        if (!response.status.isSuccess()) return null
        json.decodeFromString(TelemetrySnapshot.serializer(), response.bodyAsText())
    }.getOrNull()

    /**
     * Poll [fetchSnapshot] on a fixed interval. Emits `null` for each failed attempt so the caller
     * can flip to a detached state and recover when the backend comes back.
     */
    fun pollSnapshots(descriptor: RunDescriptor, intervalMs: Long = 1_500): Flow<TelemetrySnapshot?> = flow {
        while (true) {
            emit(fetchSnapshot(descriptor))
            delay(intervalMs.milliseconds)
        }
    }

    /**
     * Open the live `WS /stream` and emit each decoded snapshot frame. The flow completes when the
     * socket closes or the backend is unreachable; malformed frames are skipped. Guarded end to end.
     */
    fun streamSnapshots(descriptor: RunDescriptor): Flow<TelemetrySnapshot> = channelFlow {
        runCatching {
            val url = "ws://${descriptor.host}:${descriptor.telemetryPort}/stream?token=${descriptor.wsToken}"
            client.webSocket(urlString = url) {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val snapshot = runCatching {
                            json.decodeFromString(TelemetrySnapshot.serializer(), frame.readText())
                        }.getOrNull()
                        if (snapshot != null) trySend(snapshot)
                    }
                }
            }
        }
        awaitClose { }
    }

    override fun close() {
        runCatching { client.close() }
    }
}
