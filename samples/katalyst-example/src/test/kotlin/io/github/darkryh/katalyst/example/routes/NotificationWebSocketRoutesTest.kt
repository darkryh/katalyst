package io.github.darkryh.katalyst.example.routes

import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.test.Test
import kotlin.test.assertTrue

class NotificationWebSocketRoutesTest {

    @Test
    fun `websocket welcomes client and responds to ping`() = katalystTestApplication(
        configureEnvironment = {
            database(inMemoryDatabaseConfig())
            scan("io.github.darkryh.katalyst.example")
        }
    ) { 
        val socketClient = client.config { install(WebSockets) }
        socketClient.webSocket("/ws/users") {
            val welcome = (incoming.receive() as Frame.Text).readText()
            assertTrue(welcome.contains("\"type\":\"welcome\""))

            send(Frame.Text("ping"))
            val pong = (incoming.receive() as Frame.Text).readText()
            assertTrue(pong.contains("\"type\":\"pong\""))
        }
    }
}
