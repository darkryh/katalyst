package io.github.darkryh.katalyst.websockets

import io.github.darkryh.katalyst.di.KatalystFeaturesBuilder
import io.github.darkryh.katalyst.di.feature.KatalystBeanContext
import io.github.darkryh.katalyst.di.feature.KatalystBeanModule
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import io.github.darkryh.katalyst.di.feature.katalystBeanModule
import io.github.darkryh.katalyst.ktor.KtorModule
import io.github.darkryh.katalyst.ktor.websocket.WebSocketOptions
import io.github.darkryh.katalyst.ktor.websocket.WebSocketOptionsBuilder
import io.github.darkryh.katalyst.ktor.websocket.WebSocketPluginModule
import org.slf4j.LoggerFactory

/**
 * Public feature object so applications can opt in through the Katalyst application builder.
 *
 * The underlying WebSocket route DSL, options, and plugin installer now live in `katalyst-ktor`.
 * This feature remains in `katalyst-websockets` because it depends on the DI application builder.
 */
object WebSocketFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("WebSocketFeature")
    override val id: String = "websockets"
    private var options: WebSocketOptions =
        WebSocketOptions()

    fun configure(options: WebSocketOptions) {
        this.options = options
    }

    override fun provideBeanModules(): List<KatalystBeanModule> {
        logger.info("Loading WebSocket feature modules")
        val configuredOptions = options
        val webSocketModule = katalystBeanModule {
            single<Boolean>(qualifier = "enableWebSockets") { true }
            single<WebSocketOptions> { configuredOptions }
            single<KtorModule> { WebSocketPluginModule() }
        }
        return listOf(webSocketModule)
    }

    override fun onReady(context: KatalystBeanContext) {
        logger.info("WebSocket feature ready (flag registered for plugin module)")
    }
}

/**
 * Enables WebSocket support by registering the WebSocket feature with the builder.
 */
fun KatalystFeaturesBuilder.enableWebSockets(
    configure: WebSocketOptionsBuilder.() -> Unit = {},
): KatalystFeaturesBuilder {
    val options = WebSocketOptionsBuilder().apply(configure).build()
    WebSocketFeature.configure(options)
    return feature(WebSocketFeature)
}
