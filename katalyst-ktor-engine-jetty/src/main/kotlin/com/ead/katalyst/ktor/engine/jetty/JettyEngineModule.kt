package com.ead.katalyst.ktor.engine.jetty

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for Jetty engine components.
 * Registers both configuration and factory for Jetty-backed Ktor servers.
 *
 * This module registers:
 * - JettyEngineConfiguration: Jetty-specific settings (thread pools, timeouts, etc.)
 * - KtorEngineFactory: Factory implementation for creating Jetty servers
 *
 * Configuration is injected from ServerConfiguration, enabling:
 * - Runtime engine selection
 * - Host/port configuration from ServerConfiguration
 * - Timeout configuration from ServerConfiguration
 *
 * To switch to a different engine (e.g., Netty):
 * 1. Remove this module (don't include katalyst-ktor-engine-jetty)
 * 2. Include the alternative engine module (katalyst-ktor-engine-netty)
 * 3. No code changes needed - DI handles everything
 */
/**
 * Get the JettyEngineModule for reflection-based loading.
 * This is used by EngineRegistrar to automatically discover and load
 * the engine module when katalyst-ktor-engine-jetty is on the classpath.
 */
fun getJettyEngineModule(): Module = module {
    // Configuration: Get values from ServerConfiguration
    single<JettyEngineConfiguration> {
        val serverConfig = get<ServerConfiguration>()
        JettyEngineConfiguration(
            host = serverConfig.host,
            port = serverConfig.port,
            maxThreads = 100,  // Jetty-specific default
            minThreads = 10,   // Jetty-specific default
            connectionIdleTimeoutMs = serverConfig.connectionIdleTimeoutMs
        )
    }

    // Factory: Uses the configuration
    single<KtorEngineFactory> {
        JettyEngineFactory(get())
    }
}
