package com.ead.katalyst.websockets

import com.ead.katalyst.ktor.KtorModule
import org.koin.dsl.module

/**
 * Koin module that exposes the WebSocket plugin installer as a [KtorModule].
 *
 * Loaded when WebSocket support is enabled so the automatic DI bootstrap can
 * install the Ktor WebSockets plugin ahead of any user-defined routes.
 */
fun webSocketDIModule() = module {
    single<KtorModule> { WebSocketPluginModule() }
}
