package com.ead.katalyst.ktor.engine.cio

import com.ead.katalyst.di.config.ServerConfiguration
import com.ead.katalyst.ktor.engine.KatalystKtorEngine
import com.ead.katalyst.ktor.engine.KtorEngineFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for CIO engine components.
 * Registers both configuration and factory for CIO-backed Ktor servers.
 *
 * This module registers:
 * - CioEngineConfiguration: CIO-specific settings (timeouts, etc.)
 * - KtorEngineFactory: Factory implementation for creating CIO servers
 *
 * Configuration is injected from ServerConfiguration, enabling:
 * - Runtime engine selection
 * - Host/port configuration from ServerConfiguration
 * - Timeout configuration from ServerConfiguration
 *
 * To switch to a different engine (e.g., Netty):
 * 1. Remove this module (don't include katalyst-ktor-engine-cio)
 * 2. Include the alternative engine module (katalyst-ktor-engine-netty)
 * 3. No code changes needed - DI handles everything
 */
/**
 * Get the CioEngineModule for reflection-based loading.
 * This is used by EngineRegistrar to automatically discover and load
 * the engine module when katalyst-ktor-engine-cio is on the classpath.
 */
fun getCioEngineModule(): Module = module {
    // Engine: Singleton instance of CioEngine
    single<KatalystKtorEngine> {
        CioEngine
    }

    // Configuration: Create from ServerConfiguration's deployment config
    single<CioEngineConfiguration> {
        val serverConfig = get<ServerConfiguration>()
        CioEngineConfiguration(
            deployment = serverConfig.deployment
        )
    }

    // Factory: Uses the configuration
    single<KtorEngineFactory> {
        CioEngineFactory(get())
    }
}
