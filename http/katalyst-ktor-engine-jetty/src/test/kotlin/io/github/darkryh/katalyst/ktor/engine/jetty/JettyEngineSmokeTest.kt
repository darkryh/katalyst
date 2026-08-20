package io.github.darkryh.katalyst.ktor.engine.jetty

import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Engine-matrix smoke test (Phase 5): boots the **real** Jetty engine on an ephemeral port, serves a
 * request, and shuts down cleanly.
 */
class JettyEngineSmokeTest {

    @Test
    fun `jetty engine starts, serves a request, and stops cleanly`() {
        val server = embeddedServer(Jetty, port = 0) {
            routing {
                get("/health") { call.respondText("ok") }
            }
        }
        try {
            server.start(wait = false)
            val port = runBlocking { server.engine.resolvedConnectors().first().port }

            val connection = URI("http://127.0.0.1:$port/health").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            try {
                assertEquals(200, connection.responseCode)
                assertEquals("ok", connection.inputStream.bufferedReader().readText())
            } finally {
                connection.disconnect()
            }
        } finally {
            server.stop(0, 2_000)
        }
    }
}
