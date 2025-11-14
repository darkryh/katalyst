package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.ktor.engine.KatalystKtorEngine
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for Netty engine components.
 * Registers both configuration and factory for Netty-backed Ktor servers.
 *
 * This module registers:
 * - NettyEngineConfiguration: Netty-specific settings (worker threads, timeouts, etc.)
 * - KtorEngineFactory: Factory implementation for creating Netty servers
 *
 * Configuration is injected from ServerConfiguration, enabling:
 * - Runtime engine selection
 * - Host/port configuration from ServerConfiguration
 * - Timeout configuration from ServerConfiguration
 *
 * To switch to a different engine (e.g., Jetty):
 * 1. Remove this module (don't include katalyst-ktor-engine-netty)
 * 2. Include the alternative engine module (katalyst-ktor-engine-jetty)
 * 3. No code changes needed - DI handles everything
 *
 * Accessed via getNettyEngineModule() for reflection-based discovery.
 */
fun getNettyEngineModule(): Module = module {
    // Engine: Singleton instance of NettyEngine
    single<KatalystKtorEngine> {
        NettyEngine
    }

    // Configuration: Create from ServerConfiguration's deployment config
    single<NettyEngineConfiguration> {
        val serverConfig = get<ServerConfiguration>()
        NettyEngineConfiguration(
            deployment = serverConfig.deployment
        )
    }

    // Factory: Uses the configuration
    single<KtorEngineFactory> {
        NettyEngineFactory(get())
    }
}
