package com.ead.katalyst.ktor.engine

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer

/**
 * Factory for creating Ktor embedded servers with specific engine implementations.
 *
 * This interface enables pluggable engine creation, allowing the framework to:
 * - Create engines dynamically at runtime
 * - Support multiple engine implementations (Netty, Jetty, CIO, etc.)
 * - Respect configuration from DI container
 * - Avoid hardcoding specific engine types
 *
 * Implementations are engine-specific (Netty, Jetty, CIO) and are discovered
 * and registered during DI bootstrap via their respective modules.
 *
 * Each implementation receives configuration via constructor injection from Koin.
 */
interface KtorEngineFactory {
    /**
     * The engine type identifier (e.g., "netty", "jetty", "cio")
     *
     * This must match the engine type name used in ServerConfiguration.engineType
     */
    val engineType: String

    /**
     * Create an EmbeddedServer with the given host and port.
     *
     * Implementation is responsible for:
     * - Creating the appropriate engine (Netty, Jetty, CIO, etc.)
     * - Applying engine-specific configurations
     * - Handling creation errors with clear exceptions
     * - Proper resource cleanup on failure
     *
     * @param host Bind address (e.g., "0.0.0.0", "127.0.0.1", "localhost")
     * @param port Bind port (e.g., 8080)
     * @param connectingIdleTimeoutMs Connection idle timeout in milliseconds (default: 180000ms)
     * @param block Ktor application configuration block to execute after server creation
     * @return Configured EmbeddedServer ready to start
     * @throws IllegalStateException if server creation fails
     */
    fun createServer(
        host: String,
        port: Int,
        connectingIdleTimeoutMs: Long = 180000L,
        block: suspend () -> Unit
    ): EmbeddedServer<out ApplicationEngine, out ApplicationEngine.Configuration>
}
