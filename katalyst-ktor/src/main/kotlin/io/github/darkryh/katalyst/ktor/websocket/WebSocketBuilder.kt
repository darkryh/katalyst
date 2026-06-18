package io.github.darkryh.katalyst.ktor.websocket

import io.github.darkryh.katalyst.ktor.builder.verifyKatalystContainer
import io.github.darkryh.katalyst.ktor.extension.getKatalystContainer
import io.ktor.server.routing.Route
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WebSocketBuilder")

/**
 * Configures WebSocket routes using the real Ktor [Route] context.
 *
 * The provided [block] executes within the original [Route], so the standard
 * `webSocket("/path") {}` extension remains available.
 */
fun Route.katalystWebSockets(block: Route.() -> Unit) {
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
                    enableYamlConfiguration()
                    database { fromConfiguration() }
                    scanPackages("com.example.app")
                    features {
                        enableWebSockets()
                    }
                }
                """.trimIndent(),
            )
        }

        this.block()
        logger.debug("WebSocket configuration completed for route {}", this::class.simpleName)
    } catch (e: Exception) {
        logger.error("Error during WebSocket configuration", e)
        throw e
    }
}
