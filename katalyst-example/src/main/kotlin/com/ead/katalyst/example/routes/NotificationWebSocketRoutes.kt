package com.ead.katalyst.example.routes

import com.ead.katalyst.routes.katalystWebSockets
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

@Suppress("unused")
fun Route.notificationWebSocketRoutes() = katalystWebSockets {
    webSocket("/ws/users") {
        send(Frame.Text("""{"type":"welcome","message":"Watching for profile changes"}"""))
        for (frame in incoming) {
            if (frame is Frame.Text && frame.readText() == "ping") {
                send(Frame.Text("""{"type":"pong","timestamp":${System.currentTimeMillis()}}"""))
            }
        }
    }
}
