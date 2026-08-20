package io.github.darkryh.katalyst.ktor.websocket

import io.github.darkryh.katalyst.ktor.KtorModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import kotlin.time.Duration.Companion.ZERO
import io.github.darkryh.katalyst.ktor.extension.getKatalystContainer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WebSocketPluginModule")

/**
 * Auto-discovered KtorModule that installs WebSocket plugin support.
 *
 * This module installs Ktor WebSockets only when `features { enableWebSockets() }`
 * is used in the Katalyst application builder.
 */
class WebSocketPluginModule : KtorModule {
    override val order: Int = -100

    override fun install(application: Application) {
        logger.debug("WebSocketPluginModule.install() called - order={}", order)

        try {
            val container = getKatalystContainer()
            val isWebSocketsEnabled = runCatching {
                container.get(Boolean::class, qualifier = "enableWebSockets")
            }.getOrDefault(false)

            logger.debug("WebSocket plugin flag enableWebSockets={}", isWebSocketsEnabled)

            if (isWebSocketsEnabled) {
                val options = runCatching {
                    container.get(WebSocketOptions::class)
                }.getOrDefault(WebSocketOptions())

                logger.debug("Installing Ktor WebSocket plugin to application")
                application.install(WebSockets) {
                    pingPeriodMillis = options.pingPeriod?.inWholeMilliseconds ?: 0L
                    timeoutMillis = options.timeout.coerceAtLeast(ZERO).inWholeMilliseconds
                    maxFrameSize = options.maxFrameSize
                    masking = options.masking
                }
                logger.info("WebSocket plugin installed successfully")
            } else {
                logger.warn("WebSocket plugin NOT installed (features { enableWebSockets() } was not called)")
            }
        } catch (e: Exception) {
            logger.error("Error installing WebSocket plugin", e)
            throw e
        }
    }
}
