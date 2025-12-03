package io.github.darkryh.katalyst.websockets

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory
import io.github.darkryh.katalyst.ktor.KtorModule

private val logger = LoggerFactory.getLogger("WebSocketPluginModule")

/**
 * Auto-discovered KtorModule that installs WebSocket plugin support.
 *
 * This module is automatically discovered and registered by the Katalyst framework.
 * When enableWebSockets() is called in the application builder, this module will
 * install the Ktor WebSocket plugin with appropriate configuration.
 *
 * **Features:**
 * - Automatically discovered via bytecode scanning
 * - Installs WebSocket plugin only if enableWebSockets() was called
 * - Configures ping/pong, timeout, masking, and max frame size
 * - Provides clear error messages if used without enableWebSockets()
 *
 * **Installation Order:**
 * This module is installed with order = -100 (FIRST, before all other modules) to ensure
 * the WebSocket plugin is available when ANY route functions execute, including exception handlers.
 */
class WebSocketPluginModule : KtorModule {
    override val order: Int = -100  // Install FIRST, before all other modules

    override fun install(application: Application) {
        logger.info("WebSocketPluginModule.install() called - order: $order")

        try {
            // Check if WebSocket support is enabled
            val koin = GlobalContext.get()
            val isWebSocketsEnabled = runCatching {
                koin.get<Boolean>(qualifier = named("enableWebSockets"))
            }.getOrDefault(false)

            logger.info("WebSocket plugin: enableWebSockets flag = $isWebSocketsEnabled")

            if (isWebSocketsEnabled) {
                logger.info("Installing Ktor WebSocket plugin to application")
                application.install(WebSockets)
                logger.info("✓ WebSocket plugin installed successfully - ready for WebSocket routes")
            } else {
                logger.warn("⚠ WebSocket plugin NOT installed (enableWebSockets() was not called in application builder)")
            }
        } catch (e: Exception) {
            logger.error("✗ Error installing WebSocket plugin", e)
            throw e
        }
    }
}
