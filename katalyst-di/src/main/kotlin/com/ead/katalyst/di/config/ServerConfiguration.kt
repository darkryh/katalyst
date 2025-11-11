package com.ead.katalyst.di.config

import io.ktor.server.application.Application
import io.ktor.server.engine.ApplicationEngine
import org.slf4j.LoggerFactory

/**
 * Server engine type alias for Ktor application engines.
 *
 * Supports: Netty, Jetty, CIO, Tomcat, and any custom ApplicationEngine implementation.
 */
typealias ServerEngine = ApplicationEngine

/**
 * Server wrapper configuration lambda.
 *
 * Allows custom configuration of the server engine before it starts.
 * Use this to configure engine-specific settings like thread pools, SSL, etc.
 *
 * **Example - Netty Engine Configuration:**
 * ```kotlin
 * val nettyWrapper: ServerWrapper = { engine ->
 *     if (engine is NettyApplicationEngine) {
 *         // Configure Netty-specific settings
 *         println("Configured Netty engine")
 *     }
 *     engine
 * }
 * ```
 *
 * **Example - Thread Pool Configuration:**
 * ```kotlin
 * val customWrapper: ServerWrapper = { engine ->
 *     // Custom configuration logic
 *     when (engine) {
 *         is NettyApplicationEngine -> {
 *             // Configure Netty thread pools
 *         }
 *         else -> {
 *             // Other engine types
 *         }
 *     }
 *     engine
 * }
 * ```
 */
typealias ServerWrapper = (ServerEngine) -> ServerEngine

/**
 * Application wrapper configuration lambda.
 *
 * Allows custom configuration of the Application instance before module setup.
 * Use this to configure application-level settings.
 *
 * **Example:**
 * ```kotlin
 * val appWrapper: ApplicationWrapper = { app ->
 *     println("Application configured: ${app.javaClass.simpleName}")
 *     app
 * }
 * ```
 */
typealias ApplicationWrapper = (Application) -> Application

/**
 * Server configuration container.
 *
 * Holds all server-related configuration for Ktor applications,
 * including engine selection, binding address, port, and wrapping functions.
 *
 * This configuration is read by engine implementations via DI injection,
 * enabling runtime engine selection and configuration without code changes.
 *
 * @param engineType The engine type to use (netty, jetty, cio, etc.)
 * @param host Server bind address (default: "0.0.0.0" for all interfaces)
 * @param port Server listen port (default: 8080)
 * @param workerThreads Number of worker threads (default: 2 × CPU cores)
 * @param connectionIdleTimeoutMs Connection idle timeout in milliseconds (default: 180000ms)
 * @param serverWrapper Optional lambda to wrap/configure the server engine
 * @param applicationWrapper Optional lambda to wrap/configure the application
 */
data class ServerConfiguration(
    val engineType: String = "netty",
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val workerThreads: Int = Runtime.getRuntime().availableProcessors() * 2,
    val connectionIdleTimeoutMs: Long = 180000L,
    val serverWrapper: ServerWrapper? = null,
    val applicationWrapper: ApplicationWrapper? = null
) {
    init {
        require(engineType.isNotBlank()) { "engineType must not be blank" }
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be in range 1-65535" }
        require(workerThreads > 0) { "workerThreads must be positive" }
        require(connectionIdleTimeoutMs > 0) { "connectionIdleTimeoutMs must be positive" }
    }
    companion object {
        /**
         * Creates a default Netty server configuration.
         */
        fun netty(
            serverWrapper: ServerWrapper? = null,
            applicationWrapper: ApplicationWrapper? = null
        ): ServerConfiguration = ServerConfiguration(
            engineType = "netty",
            serverWrapper = serverWrapper,
            applicationWrapper = applicationWrapper
        )

        /**
         * Creates a Jetty server configuration.
         */
        fun jetty(
            serverWrapper: ServerWrapper? = null,
            applicationWrapper: ApplicationWrapper? = null
        ): ServerConfiguration = ServerConfiguration(
            engineType = "jetty",
            serverWrapper = serverWrapper,
            applicationWrapper = applicationWrapper
        )

        /**
         * Creates a CIO (Coroutine-based I/O) server configuration.
         */
        fun cio(
            serverWrapper: ServerWrapper? = null,
            applicationWrapper: ApplicationWrapper? = null
        ): ServerConfiguration = ServerConfiguration(
            engineType = "cio",
            serverWrapper = serverWrapper,
            applicationWrapper = applicationWrapper
        )
    }
}

/**
 * Server configuration builder for fluent API style configuration.
 *
 * **Usage Example:**
 * ```kotlin
 * val serverConfig = ServerConfigurationBuilder()
 *     .engineType("jetty")
 *     .host("127.0.0.1")
 *     .port(9090)
 *     .workerThreads(50)
 *     .connectionIdleTimeout(300000)
 *     .withServerWrapper { engine ->
 *         // Configure server engine
 *         engine
 *     }
 *     .withApplicationWrapper { app ->
 *         // Configure application
 *         app
 *     }
 *     .build()
 * ```
 */
class ServerConfigurationBuilder {
    private val logger = LoggerFactory.getLogger("ServerConfigurationBuilder")

    private var engineType: String = "netty"
    private var host: String = "0.0.0.0"
    private var port: Int = 8080
    private var workerThreads: Int = Runtime.getRuntime().availableProcessors() * 2
    private var connectionIdleTimeoutMs: Long = 180000L
    private var serverWrapper: ServerWrapper? = null
    private var applicationWrapper: ApplicationWrapper? = null

    /**
     * Sets the engine type explicitly.
     *
     * @param type Engine type (netty, jetty, cio, etc.)
     */
    fun engineType(type: String): ServerConfigurationBuilder {
        logger.debug("Configuring engine type: {}", type)
        this.engineType = type
        return this
    }

    /**
     * Sets the engine type to Netty.
     */
    fun netty(): ServerConfigurationBuilder {
        logger.debug("Configuring Netty engine")
        engineType = "netty"
        return this
    }

    /**
     * Sets the engine type to Jetty.
     */
    fun jetty(): ServerConfigurationBuilder {
        logger.debug("Configuring Jetty engine")
        engineType = "jetty"
        return this
    }

    /**
     * Sets the engine type to CIO (Coroutine-based I/O).
     */
    fun cio(): ServerConfigurationBuilder {
        logger.debug("Configuring CIO engine")
        engineType = "cio"
        return this
    }

    /**
     * Sets the server bind address.
     *
     * @param host Server bind address (e.g., "0.0.0.0", "127.0.0.1", "localhost")
     */
    fun host(host: String): ServerConfigurationBuilder {
        logger.debug("Setting host: {}", host)
        this.host = host
        return this
    }

    /**
     * Sets the server listen port.
     *
     * @param port Server port (1-65535)
     */
    fun port(port: Int): ServerConfigurationBuilder {
        logger.debug("Setting port: {}", port)
        this.port = port
        return this
    }

    /**
     * Sets the number of worker threads.
     *
     * @param threads Number of worker threads (must be positive)
     */
    fun workerThreads(threads: Int): ServerConfigurationBuilder {
        logger.debug("Setting worker threads: {}", threads)
        this.workerThreads = threads
        return this
    }

    /**
     * Sets the connection idle timeout in milliseconds.
     *
     * @param ms Timeout in milliseconds (must be positive)
     */
    fun connectionIdleTimeout(ms: Long): ServerConfigurationBuilder {
        logger.debug("Setting connection idle timeout: {}ms", ms)
        this.connectionIdleTimeoutMs = ms
        return this
    }

    /**
     * Sets a custom server wrapper for engine configuration.
     *
     * @param wrapper Lambda function to configure the server engine
     */
    fun withServerWrapper(wrapper: ServerWrapper): ServerConfigurationBuilder {
        logger.debug("Setting custom server wrapper")
        this.serverWrapper = wrapper
        return this
    }

    /**
     * Sets a custom application wrapper for application configuration.
     *
     * @param wrapper Lambda function to configure the application
     */
    fun withApplicationWrapper(wrapper: ApplicationWrapper): ServerConfigurationBuilder {
        logger.debug("Setting custom application wrapper")
        this.applicationWrapper = wrapper
        return this
    }

    /**
     * Builds the final ServerConfiguration.
     */
    fun build(): ServerConfiguration {
        logger.info(
            "Building server configuration: engine={}, host={}, port={}, workers={}",
            engineType, host, port, workerThreads
        )
        return ServerConfiguration(
            engineType = engineType,
            host = host,
            port = port,
            workerThreads = workerThreads,
            connectionIdleTimeoutMs = connectionIdleTimeoutMs,
            serverWrapper = serverWrapper,
            applicationWrapper = applicationWrapper
        )
    }
}

/**
 * Engine-specific configuration.
 *
 * Provides factory functions for common server engine configurations.
 */
object ServerEngines {
    private val logger = LoggerFactory.getLogger("ServerEngines")

    /**
     * Creates a Netty engine wrapper with thread pool configuration.
     *
     * **Example:**
     * ```kotlin
     * val nettyWrapper = ServerEngines.nettyWithThreads(
     *     workerThreads = 8,
     *     ioThreads = 4
     * )
     * ```
     */
    fun nettyWithThreads(
        workerThreads: Int = 8,
        ioThreads: Int = 4
    ): ServerWrapper {
        return { engine ->
            logger.info("Configuring Netty engine: workerThreads={}, ioThreads={}", workerThreads, ioThreads)
            // Check engine class name to avoid direct import dependency
            if (engine::class.simpleName?.contains("Netty") == true) {
                // Thread pool configuration would go here
                // This is a placeholder for actual Netty configuration
                logger.debug("Netty engine configured")
            }
            engine
        }
    }

    /**
     * Creates a wrapper that logs engine information.
     */
    fun withLogging(): ServerWrapper {
        return { engine ->
            logger.info("Server engine type: {}", engine::class.simpleName)
            logger.info("Server engine: {}", engine)
            engine
        }
    }

    /**
     * Creates a wrapper that combines multiple wrappers.
     */
    fun combine(vararg wrappers: ServerWrapper): ServerWrapper {
        return { engine ->
            var result = engine
            for (wrapper in wrappers) {
                result = wrapper(result)
            }
            result
        }
    }
}

/**
 * Extension function to apply server wrapper to an engine.
 *
 * **Usage:**
 * ```kotlin
 * val wrappedEngine = engine.wrap(serverWrapper)
 * ```
 */
fun ApplicationEngine.wrap(wrapper: ServerWrapper?): ApplicationEngine {
    return wrapper?.invoke(this) ?: this
}

/**
 * Extension function to apply application wrapper to the Application.
 *
 * **Usage:**
 * ```kotlin
 * val wrappedApp = app.wrap(applicationWrapper)
 * ```
 */
fun Application.wrap(wrapper: ApplicationWrapper?): Application {
    return wrapper?.invoke(this) ?: this
}
