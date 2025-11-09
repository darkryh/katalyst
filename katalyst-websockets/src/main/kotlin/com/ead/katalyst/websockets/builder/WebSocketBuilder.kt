package com.ead.katalyst.websockets.builder

import com.ead.katalyst.ktor.builder.verifyKoin
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WebSocketBuilder")
/**
 * DSL function to configure WebSocket routes using the real Ktor [Route] context.
 *
 * The provided [block] executes within the original [Route], so the standard
 * `webSocket("/path") {}` extension remains available for testing or manual wiring.
 */
fun Route.katalystWebSockets(block: Route.() -> Unit) {
    logger.info("Starting WebSocket configuration on Route: {} ({})", this::class.simpleName, this::class.java.name)
    try {
        verifyKoin()

        val koin = GlobalContext.get()
        val isWebSocketsEnabled = runCatching {
            koin.get<Boolean>(qualifier = named("enableWebSockets"))
        }.getOrDefault(false)

        if (!isWebSocketsEnabled) {
            throw IllegalStateException(
                """
                WebSocket support is not enabled.
                Please call enableWebSockets() in your katalystApplication block:

                fun main(args: Array<String>) = katalystApplication(args) {
                    database(DatabaseConfigFactory.config())
                    scanPackages("com.example.app")
                    enableWebSockets()  // <- Add this
                }
                """.trimIndent()
            )
        }

        this.block()
        logger.info("WebSocket configuration completed for route {}", this::class.simpleName)
    } catch (e: Exception) {
        logger.error("Error during WebSocket configuration", e)
        throw e
    }
}
