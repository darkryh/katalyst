package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.koin.core.component.get
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class NettyEngineModuleTest {

    @Test
    fun `netty configuration validates arguments`() {
        val config = NettyEngineConfiguration(
            host = "127.0.0.1",
            port = 8080,
            workerThreads = 4,
            connectionIdleTimeoutMs = 1000L
        )

        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
        assertEquals(4, config.workerThreads)
        assertEquals(1000L, config.connectionIdleTimeoutMs)

        assertFailsWith<IllegalArgumentException> { NettyEngineConfiguration(host = "") }
        assertFailsWith<IllegalArgumentException> { NettyEngineConfiguration(port = 0) }
        assertFailsWith<IllegalArgumentException> { NettyEngineConfiguration(workerThreads = 0) }
        assertFailsWith<IllegalArgumentException> { NettyEngineConfiguration(connectionIdleTimeoutMs = 0) }
    }

    @Test
    fun `koin module exposes netty configuration and factory`() {
        val serverConfiguration = ServerConfiguration(
            engine = NettyEngine,
            host = "0.0.0.0",
            port = 9090,
            workerThreads = 16,
            connectionIdleTimeoutMs = 2000L
        )

        val koin = koinApplication {
            modules(
                module { single { serverConfiguration } },
                getNettyEngineModule()
            )
        }.koin

        val nettyConfig = koin.get<NettyEngineConfiguration>()
        assertEquals(serverConfiguration.host, nettyConfig.host)
        assertEquals(serverConfiguration.port, nettyConfig.port)
        assertEquals(serverConfiguration.workerThreads, nettyConfig.workerThreads)
        assertEquals(serverConfiguration.connectionIdleTimeoutMs, nettyConfig.connectionIdleTimeoutMs)

        val factory = koin.get<KtorEngineFactory>()
        assertTrue(factory is NettyEngineFactory)
    }
}
