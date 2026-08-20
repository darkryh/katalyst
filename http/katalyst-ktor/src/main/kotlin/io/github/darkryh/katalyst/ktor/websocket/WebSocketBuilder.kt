package io.github.darkryh.katalyst.ktor.websocket

import io.github.darkryh.katalyst.ktor.builder.verifyKatalystContainer
import io.github.darkryh.katalyst.ktor.extension.getKatalystContainer
import io.ktor.server.plugins.origin
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ChannelIterator
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WebSocketBuilder")

/**
 * Configures WebSocket routes with Katalyst's instrumentation attached.
 *
 * The [block] runs against a [KatalystWebSocketRoutes] scope whose member [KatalystWebSocketRoutes.webSocket]
 * shadows Ktor's extension (member resolution wins), so existing `webSocket("/path") { }` call sites
 * keep compiling unchanged while gaining telemetry: route registration, live session tracking, frame
 * and byte counts, and close outcomes — all read by the inspector's WebSockets screen.
 */
fun Route.katalystWebSockets(block: KatalystWebSocketRoutes.() -> Unit) {
    logger.debug("Starting WebSocket configuration on Route: {} ({})", this::class.simpleName, this::class.java.name)
    try {
        verifyKatalystContainer()

        val container = getKatalystContainer()
        val isWebSocketsEnabled = runCatching {
            container.get(Boolean::class, qualifier = "enableWebSockets")
        }.getOrDefault(false)

        if (!isWebSocketsEnabled) {
            throw IllegalStateException(
                """
                WebSocket support is not enabled.
                Please call features { enableWebSockets() } in your katalystApplication block:

                fun main(args: Array<String>) = katalystApplication(args) {
                    database { fromConfiguration() }
                    scanPackages("com.example.app")
                    features {
                        enableYamlConfiguration()
                        enableWebSockets()
                    }
                }
                """.trimIndent(),
            )
        }

        KatalystWebSocketRoutes(this).block()
        logger.debug("WebSocket configuration completed for route {}", this::class.simpleName)
    } catch (e: Exception) {
        logger.error("Error during WebSocket configuration", e)
        throw e
    }
}

/**
 * Receiver scope of [katalystWebSockets]. Its [webSocket] member registers the route with
 * [WebSocketTelemetry] and wraps every session so the inspector sees it live. The underlying Ktor
 * [Route] is exposed for anything else a socket module needs.
 */
class KatalystWebSocketRoutes internal constructor(val route: Route) {

    /**
     * Instrumented drop-in for Ktor's `webSocket(path) { }`: same handler receiver, same behavior,
     * plus session/frame telemetry. Handler failures are counted, then rethrown untouched.
     */
    fun webSocket(path: String, handler: suspend DefaultWebSocketServerSession.() -> Unit) {
        WebSocketTelemetry.registerRoute(path)
        route.webSocket(path) {
            val remote = runCatching { call.request.origin.remoteHost }.getOrNull()
            val stat = WebSocketTelemetry.sessionOpened(path, remote)
            var outcome = "normal"
            try {
                val session = if (stat != null) CountingWebSocketSession(this, stat) else this
                session.handler()
            } catch (e: CancellationException) {
                throw e // disconnect/shutdown, not a handler failure
            } catch (e: Exception) {
                outcome = "error"
                throw e
            } finally {
                WebSocketTelemetry.sessionClosed(path, stat, outcome)
            }
        }
    }
}

/**
 * Pass-through session that counts frames and bytes into [stat]. Counting happens in the channel
 * wrappers plus an explicit [send] override, so every traffic pattern is covered:
 * `for (frame in incoming)`, `receive()`, `receiveCatching()`, `tryReceive()`,
 * `outgoing.send(...)`, and `send(...)` — the latter is a session MEMBER with a default body, so
 * interface delegation would route it to the delegate's own outgoing channel, silently bypassing
 * the wrapper (observed as "Out 0" in validation); the override closes that path.
 */
private class CountingWebSocketSession(
    private val delegate: DefaultWebSocketServerSession,
    private val stat: WebSocketTelemetry.SessionStat,
) : DefaultWebSocketServerSession by delegate {
    override val incoming: ReceiveChannel<Frame> = CountingReceiveChannel(delegate.incoming, stat)
    override val outgoing: SendChannel<Frame> = CountingSendChannel(delegate.outgoing, stat)

    override suspend fun send(frame: Frame) {
        delegate.send(frame)
        stat.framesOut.incrementAndGet()
        stat.bytesOut.addAndGet(frame.data.size.toLong())
    }
}

private class CountingReceiveChannel(
    private val delegate: ReceiveChannel<Frame>,
    private val stat: WebSocketTelemetry.SessionStat,
) : ReceiveChannel<Frame> by delegate {

    private fun count(frame: Frame) {
        stat.framesIn.incrementAndGet()
        stat.bytesIn.addAndGet(frame.data.size.toLong())
    }

    override suspend fun receive(): Frame = delegate.receive().also(::count)

    override suspend fun receiveCatching(): ChannelResult<Frame> =
        delegate.receiveCatching().also { it.getOrNull()?.let(::count) }

    override fun tryReceive(): ChannelResult<Frame> =
        delegate.tryReceive().also { it.getOrNull()?.let(::count) }

    override fun iterator(): ChannelIterator<Frame> = object : ChannelIterator<Frame> {
        private val inner = delegate.iterator()
        override suspend fun hasNext(): Boolean = inner.hasNext()
        override fun next(): Frame = inner.next().also(::count)
    }
}

private class CountingSendChannel(
    private val delegate: SendChannel<Frame>,
    private val stat: WebSocketTelemetry.SessionStat,
) : SendChannel<Frame> by delegate {

    private fun count(frame: Frame) {
        stat.framesOut.incrementAndGet()
        stat.bytesOut.addAndGet(frame.data.size.toLong())
    }

    override suspend fun send(element: Frame) {
        delegate.send(element)
        count(element)
    }

    override fun trySend(element: Frame): ChannelResult<Unit> =
        delegate.trySend(element).also { if (it.isSuccess) count(element) }
}
