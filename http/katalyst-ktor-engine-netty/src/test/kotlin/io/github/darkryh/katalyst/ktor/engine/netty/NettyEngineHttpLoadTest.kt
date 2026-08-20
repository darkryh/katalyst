package io.github.darkryh.katalyst.ktor.engine.netty

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP-level load test (Phase 3) against the **real** Netty engine: many clients fire requests
 * simultaneously (released together by a barrier) and we assert every request returns 200 with the
 * expected body — i.e. **no dropped requests** under concurrent load. Tagged `load`, so excluded
 * from the default CI gate; run with `./gradlew loadTest`.
 */
@Tag("load")
class NettyEngineHttpLoadTest {

    @Test
    fun `serves many concurrent requests with no drops`() {
        val server = embeddedServer(Netty, port = 0) {
            routing {
                get("/echo/{n}") { call.respondText(call.parameters["n"] ?: "?") }
            }
        }
        val clients = 64
        val requestsPerClient = 50 // 3,200 concurrent-ish requests
        try {
            server.start(wait = false)
            val port = runBlocking { server.engine.resolvedConnectors().first().port }

            val barrier = CyclicBarrier(clients)
            val ok = AtomicInteger(0)
            val mismatches = ConcurrentLinkedQueue<String>()
            val pool = Executors.newFixedThreadPool(clients)
            try {
                val futures = (0 until clients).map { c ->
                    pool.submit {
                        barrier.await() // all clients start hammering together
                        repeat(requestsPerClient) { r ->
                            val token = "$c-$r"
                            val conn = URI("http://127.0.0.1:$port/echo/$token").toURL()
                                .openConnection() as HttpURLConnection
                            conn.connectTimeout = 5_000
                            conn.readTimeout = 5_000
                            try {
                                val code = conn.responseCode
                                val body = conn.inputStream.bufferedReader().readText()
                                if (code == 200 && body == token) ok.incrementAndGet()
                                else mismatches.add("code=$code body=$body expected=$token")
                            } finally {
                                conn.disconnect()
                            }
                        }
                    }
                }
                futures.forEach { it.get(60, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            assertTrue(mismatches.isEmpty(), "dropped/incorrect responses: ${mismatches.take(5)}")
            assertEquals(clients * requestsPerClient, ok.get(), "not every request succeeded")
        } finally {
            server.stop(0, 2_000)
        }
    }
}
