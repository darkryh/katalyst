package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.di.config.ServerDeploymentConfiguration
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
        val deployment = ServerDeploymentConfiguration(
            host = "127.0.0.1",
            port = 8080,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 4,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 1000L
        )
        val config = NettyEngineConfiguration(deployment = deployment)

        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
        assertEquals(4, config.workerThreads)
        assertEquals(1000L, config.connectionIdleTimeoutMs)

        assertFailsWith<IllegalArgumentException> {
            ServerDeploymentConfiguration(
                host = "",
                port = 8080,
                shutdownGracePeriod = 1000L,
                shutdownTimeout = 5000L,
                connectionGroupSize = 8,
                workerGroupSize = 8,
                callGroupSize = 8,
                maxInitialLineLength = 4096,
                maxHeaderSize = 8192,
                maxChunkSize = 8192,
                connectionIdleTimeoutMs = 60000L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ServerDeploymentConfiguration(
                host = "localhost",
                port = 0,
                shutdownGracePeriod = 1000L,
                shutdownTimeout = 5000L,
                connectionGroupSize = 8,
                workerGroupSize = 8,
                callGroupSize = 8,
                maxInitialLineLength = 4096,
                maxHeaderSize = 8192,
                maxChunkSize = 8192,
                connectionIdleTimeoutMs = 60000L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ServerDeploymentConfiguration(
                host = "localhost",
                port = 8080,
                shutdownGracePeriod = 1000L,
                shutdownTimeout = 5000L,
                connectionGroupSize = 8,
                workerGroupSize = 0,
                callGroupSize = 8,
                maxInitialLineLength = 4096,
                maxHeaderSize = 8192,
                maxChunkSize = 8192,
                connectionIdleTimeoutMs = 60000L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ServerDeploymentConfiguration(
                host = "localhost",
                port = 8080,
                shutdownGracePeriod = 1000L,
                shutdownTimeout = 5000L,
                connectionGroupSize = 8,
                workerGroupSize = 8,
                callGroupSize = 8,
                maxInitialLineLength = 4096,
                maxHeaderSize = 8192,
                maxChunkSize = 8192,
                connectionIdleTimeoutMs = 0
            )
        }
    }

    @Test
    fun `koin module exposes netty configuration and factory`() {
        val deployment = ServerDeploymentConfiguration(
            host = "0.0.0.0",
            port = 9090,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 16,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 2000L
        )
        val serverConfiguration = ServerConfiguration(
            engine = NettyEngine,
            deployment = deployment
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
