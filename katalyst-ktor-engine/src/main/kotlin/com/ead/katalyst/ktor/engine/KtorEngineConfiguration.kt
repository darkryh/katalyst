package com.ead.katalyst.ktor.engine

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer

/**
 * Abstract configuration for pluggable Ktor engine implementations.
 *
 * This interface defines the contract that all Ktor engine implementations must follow.
 * Implementations (Netty, Jetty, CIO) provide concrete engine creation and configuration.
 *
 * The abstraction layer contains ZERO knowledge of specific engines - it only defines
 * the contract that implementations must satisfy. This enables:
 * - True plugin architecture (drop in/out engine implementations)
 * - Zero coupling between framework and specific engine
 * - Engine selection via dependency injection
 * - Easy switching between engines (just change classpath)
 */
interface KtorEngineConfiguration {
    /**
     * Server host address to bind to.
     * Default: "0.0.0.0" (all interfaces)
     */
    val host: String

    /**
     * Server port to listen on.
     * Default: 8080
     */
    val port: Int

    /**
     * Create an embedded Ktor server with this engine configuration.
     *
     * @param block The Ktor application configuration block
     * @return Configured EmbeddedServer ready to start
     */
    fun createServer(
        block: suspend () -> Unit
    ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration>
}
