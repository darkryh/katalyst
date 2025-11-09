package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.ktor.engine.KtorEngineConfiguration
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for Netty engine configuration.
 *
 * This module registers the Netty engine implementation as the active
 * KtorEngineConfiguration. Including this module makes Netty the engine
 * for the entire application.
 *
 * To switch to a different engine (e.g., Jetty):
 * 1. Remove this module (don't include katalyst-ktor-engine-netty)
 * 2. Include the alternative engine module (katalyst-ktor-engine-jetty)
 * 3. No code changes needed - DI handles everything
 *
 * Usage in application:
 * ```kotlin
 * val koinApplication = KoinApplication().modules(
 *     NettyEngineModule,
 *     // ... other modules
 * )
 * ```
 */
val NettyEngineModule = module {
    single<KtorEngineConfiguration> {
        NettyEngineConfiguration(
            host = "0.0.0.0",
            port = 8080
        )
    }
}

/**
 * Function to get the NettyEngineModule for reflection-based loading.
 * This is used by DIConfiguration to automatically discover and load
 * the engine module when katalyst-ktor-engine-netty is on the classpath.
 */
@JvmName("getNettyEngineModuleInstance")
fun nettyEngineModuleInstance(): Module = NettyEngineModule
