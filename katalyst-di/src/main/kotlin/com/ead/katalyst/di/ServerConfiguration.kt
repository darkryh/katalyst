package com.ead.katalyst.di

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
 * including engine wrapping and application setup functions.
 *
 * @param engineType The engine type to use (Netty, Jetty, CIO, etc.)
 * @param serverWrapper Optional lambda to wrap/configure the server engine
 * @param applicationWrapper Optional lambda to wrap/configure the application
 */
data class ServerConfiguration(
    val engineType: String = "netty",
    val serverWrapper: ServerWrapper? = null,
    val applicationWrapper: ApplicationWrapper? = null
) {
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
 *     .netty()
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
    private var serverWrapper: ServerWrapper? = null
    private var applicationWrapper: ApplicationWrapper? = null

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
        logger.info("Building server configuration: engine={}", engineType)
        return ServerConfiguration(
            engineType = engineType,
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
