package io.github.darkryh.katalyst.telemetry.capture

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.core.di.getOrNull
import io.github.darkryh.katalyst.di.config.ServerConfiguration
import io.github.darkryh.katalyst.ktor.websocket.WebSocketOptions
import io.github.darkryh.katalyst.ktor.websocket.WebSocketTelemetry
import io.github.darkryh.katalyst.telemetry.model.WebSocketSession
import io.github.darkryh.katalyst.telemetry.model.WebSocketSnapshot
import io.github.darkryh.katalyst.telemetry.store.TelemetryStore
import io.ktor.server.application.pluginOrNull
import io.ktor.server.websocket.WebSockets

/**
 * Taps the WEBSOCKETS subsystem: the resolved opt-in flag, and the plugin GROUND TRUTH read from
 * the running Ktor `Application` — the same `ServerConfiguration.engine.application` path the
 * [HttpCapturer] uses for its route walk, so `pluginInstalled` is a fact, not a default.
 *
 * Effective option values prefer the installed plugin instance (what the server actually runs
 * with) and fall back to the `WebSocketOptions` DI bean (what the feature registered) when the
 * plugin cannot be read. From those we surface the two footguns the model calls out:
 * `keepaliveDisabled` (no ping => half-open connections leak) and `frameSizeUnbounded`
 * (maxFrame == Long.MAX_VALUE => OOM risk).
 *
 * Live route/session/frame data comes from [WebSocketTelemetry], written by the instrumented
 * handler wrapper in `katalystWebSockets { }` — bounded registries, so the read is always cheap.
 */
class WebSocketCapturer : SubsystemCapturer {
    override val id: String = "websockets"

    override fun install(store: TelemetryStore) {
        store.webSocketProvider = provider@{
            val container = KatalystContainerProvider.currentOrNull() ?: return@provider null

            val enabledFlag = container.getOrNull<Boolean>(qualifier = "enableWebSockets") ?: false
            val optionsBean = container.getOrNull<WebSocketOptions>()

            val plugin = container.getOrNull<ServerConfiguration>()?.engine?.let { server ->
                runCatching { server.application.pluginOrNull(WebSockets) }.getOrNull()
            }

            val pingPeriodMs = plugin?.pingIntervalMillis ?: optionsBean?.pingPeriod?.inWholeMilliseconds
            val timeoutMs = plugin?.timeoutMillis ?: optionsBean?.timeout?.inWholeMilliseconds
            val maxFrameSizeBytes = plugin?.maxFrameSize ?: optionsBean?.maxFrameSize
            val masking = plugin?.masking ?: optionsBean?.masking ?: false

            // Footguns only apply when the subsystem is actually configured.
            val configured = plugin != null || optionsBean != null
            val keepaliveDisabled = configured && (pingPeriodMs == null || pingPeriodMs <= 0L)
            val frameSizeUnbounded = configured && maxFrameSizeBytes == Long.MAX_VALUE

            WebSocketSnapshot(
                enabledFlag = enabledFlag,
                pluginInstalled = plugin != null,
                pingPeriodMs = pingPeriodMs,
                timeoutMs = timeoutMs,
                maxFrameSizeBytes = maxFrameSizeBytes,
                masking = masking,
                keepaliveDisabled = keepaliveDisabled,
                frameSizeUnbounded = frameSizeUnbounded,
                routePaths = WebSocketTelemetry.routes().sorted(),
                activeSessions = WebSocketTelemetry.active.get(),
                sessionsPerRoute = WebSocketTelemetry.sessionsPerRoute(),
                opened = WebSocketTelemetry.opened.get(),
                closed = WebSocketTelemetry.closed.get(),
                handlerErrors = WebSocketTelemetry.handlerErrors.get(),
                closeCodeCounts = WebSocketTelemetry.closeOutcomeCounts(),
                sessions = WebSocketTelemetry.sessions().map { s ->
                    WebSocketSession(
                        id = s.id,
                        path = s.path,
                        remote = s.remote,
                        openedAtEpochMs = s.openedAtEpochMs,
                        framesIn = s.framesIn.get(),
                        framesOut = s.framesOut.get(),
                        bytesIn = s.bytesIn.get(),
                        bytesOut = s.bytesOut.get(),
                    )
                },
            )
        }
    }
}
