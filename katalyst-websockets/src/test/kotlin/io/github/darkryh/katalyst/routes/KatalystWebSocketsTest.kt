package io.github.darkryh.katalyst.routes

import io.github.darkryh.katalyst.websockets.WebSocketPluginModule
import io.github.darkryh.katalyst.websockets.builder.katalystWebSockets
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KatalystWebSocketsTest {

    @BeforeTest
    fun setUp() {
        stopKoinSafely()
    }

    @AfterTest
    fun tearDown() {
        stopKoinSafely()
    }

    @Test
    fun `katalystWebSockets throws when feature not enabled`() = testApplication {
        startKoinWithFlag(enabled = false)

        application {
            install(WebSockets)
            routing {
                val error = assertFailsWith<IllegalStateException> {
                    katalystWebSockets {
                        webSocket("/disabled") {}
                    }
                }
                assertTrue(error.message?.contains("enableWebSockets") == true)
            }
        }
    }

    @Test
    fun `katalystWebSockets registers routes when enabled`() = testApplication {
        startKoinWithFlag(enabled = true)

        application {
            install(WebSockets)
            routing {
                katalystWebSockets {
                    webSocket("/notifications") {
                        send(Frame.Text("welcome"))
                        close()
                    }
                }
            }
        }

        val client = createClient {
            install(ClientWebSockets)
        }

        client.webSocket("/notifications") {
            val frame = incoming.receive()
            assertTrue(frame is Frame.Text)
            assertTrue(frame.readText().contains("welcome"))
        }
    }

    @Test
    fun `websocket plugin installs only when enabled`() = testApplication {
        startKoinWithFlag(enabled = true)

        application {
            val module = WebSocketPluginModule()
            module.install(this)
            assertNotNull(pluginOrNull(WebSockets))
        }
    }

    @Test
    fun `websocket plugin skips install when disabled`() = testApplication {
        startKoinWithFlag(enabled = false)

        application {
            val module = WebSocketPluginModule()
            module.install(this)
            assertNull(pluginOrNull(WebSockets))
        }
    }

    private fun startKoinWithFlag(enabled: Boolean) {
        startKoin {
            modules(
                module {
                    single(qualifier = named("enableWebSockets")) { enabled }
                }
            )
        }
    }

    private fun stopKoinSafely() {
        runCatching { stopKoin() }
    }
}
