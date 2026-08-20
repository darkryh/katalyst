package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.ktor.websocket.WebSocketOptions
import io.github.darkryh.katalyst.ktor.websocket.WebSocketPluginModule
import io.github.darkryh.katalyst.ktor.websocket.katalystWebSockets
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WebSocketBuilderTest {

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

    @Test
    fun `websocket plugin applies configured options`() = testApplication {
        startKoinWithFlag(
            enabled = true,
            options = WebSocketOptions(
                pingPeriod = 5.seconds,
                timeout = 20.seconds,
                maxFrameSize = 1024L,
                masking = true,
            ),
        )

        application {
            val module = WebSocketPluginModule()
            module.install(this)

            val plugin = pluginOrNull(WebSockets)
            assertNotNull(plugin)
            assertEquals(5_000L, plugin.pingIntervalMillis)
            assertEquals(20_000L, plugin.timeoutMillis)
            assertEquals(1024L, plugin.maxFrameSize)
            assertTrue(plugin.masking)
        }
    }

    private fun startKoinWithFlag(
        enabled: Boolean,
        options: WebSocketOptions = WebSocketOptions(),
    ) {
        KatalystContainerProvider.set(
            TestKatalystContainer(
                mapOf(
                    TestKatalystContainer.Key(Boolean::class, "enableWebSockets") to enabled,
                    TestKatalystContainer.Key(WebSocketOptions::class) to options,
                )
            )
        )
    }

    private fun stopKoinSafely() {
        KatalystContainerProvider.reset()
    }
}
