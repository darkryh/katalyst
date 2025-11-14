package com.ead.katalyst.ktor.engine

import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.testing.TestApplicationEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class KtorEngineContractsTest {

    @Test
    fun `ktor engine configuration executes application block`() {
        var executed = false

        val configuration = object : KtorEngineConfiguration {
            override val host: String = "127.0.0.1"
            override val port: Int = 8081

            override fun createServer(block: suspend () -> Unit): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration> {
                return embeddedServer(TestEngineFactory, host = host, port = port) {
                    runBlocking {
                        block()
                        executed = true
                    }
                }
            }
        }

        val server = configuration.createServer { }
        server.start(wait = false)
        assertTrue(executed, "Configuration should execute provided block exactly once")
        server.stop(0L, 0L)
    }

    @Test
    fun `ktor engine factory captures host port and timeout`() {
        val factory = RecordingEngineFactory()

        val server = factory.createServer(host = "0.0.0.0", port = 9090, connectingIdleTimeoutMs = 1234L) { }
        server.start(wait = false)

        assertEquals("0.0.0.0", factory.host)
        assertEquals(9090, factory.port)
        assertEquals(1234L, factory.timeout)
        assertEquals(1, factory.executions)
        assertNotNull(server)
        server.stop(0L, 0L)
    }

    private class RecordingEngineFactory : KtorEngineFactory {
        override val engineType: String = "recording"
        var host: String? = null
        var port: Int? = null
        var timeout: Long? = null
        var executions: Int = 0

        override fun createServer(
            host: String,
            port: Int,
            connectingIdleTimeoutMs: Long,
            block: suspend () -> Unit
        ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration> {
            this.host = host
            this.port = port
            this.timeout = connectingIdleTimeoutMs
            return embeddedServer(TestEngineFactory, host = host, port = port) {
                runBlocking {
                    block()
                    executions++
                }
            }
        }
    }

    private object TestEngineFactory : ApplicationEngineFactory<TestApplicationEngine, TestApplicationEngine.Configuration> {
        override fun configuration(block: TestApplicationEngine.Configuration.() -> Unit): TestApplicationEngine.Configuration =
            TestApplicationEngine.Configuration().apply(block)

        override fun create(
            environment: ApplicationEnvironment,
            monitor: Events,
            developmentMode: Boolean,
            configuration: TestApplicationEngine.Configuration,
            configure: () -> Application
        ): TestApplicationEngine {
            return TestApplicationEngine(
                environment = environment,
                monitor = monitor,
                developmentMode = developmentMode,
                applicationProvider = configure,
                configuration = configuration
            )
        }
    }
}
