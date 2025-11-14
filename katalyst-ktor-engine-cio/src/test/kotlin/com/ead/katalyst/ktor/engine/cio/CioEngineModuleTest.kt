package com.ead.katalyst.ktor.engine.cio

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class CioEngineModuleTest {

    @Test
    fun `cio configuration validates inputs`() {
        val deployment = ServerDeploymentConfiguration(
            host = "localhost",
            port = 8181,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 8,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 4000L
        )
        val config = CioEngineConfiguration(deployment = deployment)

        assertEquals("localhost", config.host)
        assertEquals(8181, config.port)
        assertEquals(4000L, config.connectionIdleTimeoutMs)

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
                connectionIdleTimeoutMs = 0
            )
        }
    }

    @Test
    fun `cio module registers configuration from server config`() {
        val deployment = ServerDeploymentConfiguration(
            host = "0.0.0.0",
            port = 8085,
            shutdownGracePeriod = 1000L,
            shutdownTimeout = 5000L,
            connectionGroupSize = 8,
            workerGroupSize = 8,
            callGroupSize = 8,
            maxInitialLineLength = 4096,
            maxHeaderSize = 8192,
            maxChunkSize = 8192,
            connectionIdleTimeoutMs = 5000L
        )
        val serverConfiguration = ServerConfiguration(
            engine = CioEngine,
            deployment = deployment
        )

        val koin = koinApplication {
            modules(
                module { single { serverConfiguration } },
                getCioEngineModule()
            )
        }.koin

        val cioConfig = koin.get<CioEngineConfiguration>()
        assertEquals(serverConfiguration.host, cioConfig.host)
        assertEquals(serverConfiguration.port, cioConfig.port)
        assertEquals(serverConfiguration.connectionIdleTimeoutMs, cioConfig.connectionIdleTimeoutMs)

        val factory = koin.get<KtorEngineFactory>()
        assertTrue(factory is CioEngineFactory)
    }
}
