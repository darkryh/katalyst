package com.ead.katalyst.ktor.engine.cio

import com.ead.katalyst.di.config.ServerConfiguration
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
        val config = CioEngineConfiguration(
            host = "localhost",
            port = 8181,
            connectionIdleTimeoutMs = 4000L
        )

        assertEquals("localhost", config.host)
        assertEquals(8181, config.port)
        assertEquals(4000L, config.connectionIdleTimeoutMs)

        assertFailsWith<IllegalArgumentException> { CioEngineConfiguration(host = "") }
        assertFailsWith<IllegalArgumentException> { CioEngineConfiguration(port = 70000) }
        assertFailsWith<IllegalArgumentException> { CioEngineConfiguration(connectionIdleTimeoutMs = 0) }
    }

    @Test
    fun `cio module registers configuration from server config`() {
        val serverConfiguration = ServerConfiguration(
            engine = CioEngine,
            host = "0.0.0.0",
            port = 8085,
            connectionIdleTimeoutMs = 5000L
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
