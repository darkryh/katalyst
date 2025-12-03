package io.github.darkryh.katalyst.websockets

import io.github.darkryh.katalyst.di.KatalystApplicationBuilder
import io.github.darkryh.katalyst.di.feature.KatalystFeature
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Public feature object so applications can opt in via [io.github.darkryh.katalyst.di.config.KatalystDIOptions].
 */
object WebSocketFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("WebSocketFeature")
    override val id: String = "websockets"

    override fun provideModules(): List<Module> {
        logger.info("Loading WebSocket feature modules")
        val flagModule = module {
            single<Boolean>(qualifier = named("enableWebSockets")) { true }
        }
        return listOf(flagModule, webSocketDIModule())
    }

    override fun onKoinReady(koin: Koin) {
        logger.info("WebSocket feature ready (flag registered for plugin module)")
    }
}

/**
 * Enables WebSocket support by registering the WebSocket feature with the builder.
 */
fun KatalystApplicationBuilder.enableWebSockets(): KatalystApplicationBuilder =
    feature(WebSocketFeature)
