package io.github.darkryh.katalyst.tui.attach

import io.github.darkryh.katalyst.telemetry.model.RunDescriptor
import io.github.darkryh.katalyst.telemetry.model.TelemetrySnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
 * What a backend answered when asked to stop.
 *
 * Every case is distinct on purpose: "it refuses", "it cannot", and "it never heard me" call for
 * three different things to tell the user, and collapsing them into a boolean is how a command ends
 * up silently doing nothing.
 */
enum class ShutdownRequestOutcome {
    /** The backend took the request and is stopping. */
    Accepted,

    /** The token was rejected — the descriptor is stale or belongs to another run. */
    Unauthorized,

    /** The backend has remote shutdown control switched off. */
    Disabled,

    /** The backend answered but has no shutdown to offer (no server, or a build without the endpoint). */
    Unsupported,

    /** Nothing answered. */
    Unreachable,
}

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
class TelemetryClient : Closeable, BackendControl {

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
     * Ask the attached backend to stop itself, and report what it said.
     *
     * The status is the backend's *answer to the request*, never proof that it stopped — the
     * transport dies with the application it is stopping, so nothing it could send would mean
     * "done". Whether it actually stopped is decided by watching it go away ([isReachable]).
     */
    override suspend fun requestShutdown(descriptor: RunDescriptor): ShutdownRequestOutcome = runCatching {
        val url = "http://${descriptor.host}:${descriptor.telemetryPort}/shutdown?token=${descriptor.wsToken}"
        val response: HttpResponse = client.post(url)
        when (response.status) {
            HttpStatusCode.Accepted -> ShutdownRequestOutcome.Accepted
            HttpStatusCode.Unauthorized -> ShutdownRequestOutcome.Unauthorized
            HttpStatusCode.Forbidden -> ShutdownRequestOutcome.Disabled
            HttpStatusCode.ServiceUnavailable -> ShutdownRequestOutcome.Unsupported
            // 404 is the telling one: a backend built before the endpoint existed. It answers, so it
            // is not unreachable — it simply does not know how to be asked.
            HttpStatusCode.NotFound -> ShutdownRequestOutcome.Unsupported
            else -> ShutdownRequestOutcome.Unreachable
        }
    }.getOrElse { ShutdownRequestOutcome.Unreachable }

    /**
     * Whether the backend still answers on its transport.
     *
     * The authority on "has it stopped yet". A descriptor file can be stale — a crashed backend
     * leaves one behind, and a clean one deletes it only at the very end — whereas a socket that has
     * stopped accepting is the process actually being gone.
     */
    override suspend fun isReachable(descriptor: RunDescriptor): Boolean =
        runCatching {
            val url = "http://${descriptor.host}:${descriptor.telemetryPort}/snapshot?token=${descriptor.wsToken}"
            client.get(url).status.value in 100..599
        }.getOrDefault(false)

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
