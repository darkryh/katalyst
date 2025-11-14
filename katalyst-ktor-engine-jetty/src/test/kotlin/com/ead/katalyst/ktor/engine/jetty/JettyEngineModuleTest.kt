package com.ead.katalyst.ktor.engine.jetty

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class JettyEngineModuleTest {

    @Test
    fun `jetty configuration enforces invariants`() {
        val deployment = ServerDeploymentConfiguration(
            host = "localhost",
            port = 9000,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 8,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 3000L,
            maxThreads = 50,
            minThreads = 5
        )
        val config = JettyEngineConfiguration(deployment = deployment)

        assertEquals("localhost", config.host)
        assertEquals(9000, config.port)
        assertEquals(50, config.maxThreads)
        assertEquals(5, config.minThreads)
        assertEquals(3000L, config.connectionIdleTimeoutMs)

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
                port = 70000,
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
                workerGroupSize = 8,
                callGroupSize = 8,
                maxInitialLineLength = 4096,
                maxHeaderSize = 8192,
                maxChunkSize = 8192,
                connectionIdleTimeoutMs = 60000L,
                maxThreads = 0
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
                connectionIdleTimeoutMs = 60000L,
                minThreads = 0
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
                connectionIdleTimeoutMs = 60000L,
                minThreads = 5,
                maxThreads = 2
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
    fun `jetty module provides configuration and factory`() {
        val deployment = ServerDeploymentConfiguration(
            host = "0.0.0.0",
            port = 7070,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 8,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 4500L,
            maxThreads = 100,
            minThreads = 10
        )
        val serverConfiguration = ServerConfiguration(
            engine = JettyEngine,
            deployment = deployment
        )

        val koin = koinApplication {
            modules(
                module { single { serverConfiguration } },
                getJettyEngineModule()
            )
        }.koin

        val jettyConfig = koin.get<JettyEngineConfiguration>()
        assertEquals(serverConfiguration.host, jettyConfig.host)
        assertEquals(serverConfiguration.port, jettyConfig.port)
        assertEquals(100, jettyConfig.maxThreads)
        assertEquals(10, jettyConfig.minThreads)
        assertEquals(serverConfiguration.connectionIdleTimeoutMs, jettyConfig.connectionIdleTimeoutMs)

        val factory = koin.get<KtorEngineFactory>()
        assertTrue(factory is JettyEngineFactory)
    }
}
