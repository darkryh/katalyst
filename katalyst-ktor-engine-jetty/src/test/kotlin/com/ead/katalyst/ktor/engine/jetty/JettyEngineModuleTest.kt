package com.ead.katalyst.ktor.engine.jetty

import com.ead.katalyst.di.config.ServerConfiguration
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
        val config = JettyEngineConfiguration(
            host = "localhost",
            port = 9000,
            maxThreads = 50,
            minThreads = 5,
            connectionIdleTimeoutMs = 3000L
        )

        assertEquals("localhost", config.host)
        assertEquals(9000, config.port)
        assertEquals(50, config.maxThreads)
        assertEquals(5, config.minThreads)
        assertEquals(3000L, config.connectionIdleTimeoutMs)

        assertFailsWith<IllegalArgumentException> { JettyEngineConfiguration(host = "") }
        assertFailsWith<IllegalArgumentException> { JettyEngineConfiguration(port = 70000) }
        assertFailsWith<IllegalArgumentException> { JettyEngineConfiguration(maxThreads = 0) }
        assertFailsWith<IllegalArgumentException> { JettyEngineConfiguration(minThreads = 0) }
        assertFailsWith<IllegalArgumentException> { JettyEngineConfiguration(minThreads = 5, maxThreads = 2) }
        assertFailsWith<IllegalArgumentException> { JettyEngineConfiguration(connectionIdleTimeoutMs = 0) }
    }

    @Test
    fun `jetty module provides configuration and factory`() {
        val serverConfiguration = ServerConfiguration(
            engine = JettyEngine,
            host = "0.0.0.0",
            port = 7070,
            connectionIdleTimeoutMs = 4500L
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
