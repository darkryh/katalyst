package com.ead.katalyst.di.config

import com.ead.katalyst.ktor.engine.KatalystKtorEngine
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
 * Server configuration container (Bridge Class).
 *
 * Acts as a bridge between the DI system and deployment configuration.
 * Holds all server-related configuration for Ktor applications,
 * including engine selection, full deployment configuration, and wrapping functions.
 *
 * This configuration is read by engine implementations via DI injection,
 * enabling runtime engine selection and configuration without code changes.
 *
 * **Architecture:**
 * - Embeds ServerDeploymentConfiguration for all Ktor deployment properties
 * - Provides convenience accessors for backward compatibility (host, port, workerThreads, connectionIdleTimeoutMs)
 * - Validates all deployment configuration on instantiation
 *
 * @param engine The engine instance to use (NettyEngine, JettyEngine, or CioEngine)
 * @param deployment Complete Ktor deployment configuration (see ServerDeploymentConfiguration)
 * @param serverWrapper Optional lambda to wrap/configure the server engine
 * @param applicationWrapper Optional lambda to wrap/configure the application
 */
data class ServerConfiguration(
    val engine: KatalystKtorEngine,
    val deployment: ServerDeploymentConfiguration,
    val serverWrapper: ServerWrapper? = null,
    val applicationWrapper: ApplicationWrapper? = null
) {
    // Convenience accessors for backward compatibility with existing code
    val host: String get() = deployment.host
    val port: Int get() = deployment.port
    val workerThreads: Int get() = deployment.workerGroupSize
    val connectionIdleTimeoutMs: Long get() = deployment.connectionIdleTimeoutMs

    init {
        // ServerDeploymentConfiguration validates itself in its init block
        // No additional validation needed here
    }

    companion object {
        private val logger = LoggerFactory.getLogger("ServerConfiguration.Companion")

        /**
         * Creates a default Netty server configuration with minimal Ktor defaults.
         *
         * Uses sensible defaults matching Ktor's standard configuration:
         * - host: "0.0.0.0" (all interfaces)
         * - port: 8080
         * - shutdownGracePeriod: 1000ms
         * - shutdownTimeout: 5000ms
         * - Thread pools: 8 threads each
         * - HTTP limits: standard safe limits
         * - connectionIdleTimeoutMs: 180000ms (3 minutes)
         *
         * @param serverWrapper Optional lambda to wrap/configure the server engine
         * @param applicationWrapper Optional lambda to wrap/configure the application
         * @return ServerConfiguration with Netty engine and default deployment config
         */
        fun netty(
            serverWrapper: ServerWrapper? = null,
            applicationWrapper: ApplicationWrapper? = null
        ): ServerConfiguration = ServerConfiguration(
            engine = loadEngineByName("netty"),
            deployment = ServerDeploymentConfiguration.createDefault(),
            serverWrapper = serverWrapper,
            applicationWrapper = applicationWrapper
        )

        /**
         * Creates a Jetty server configuration with minimal Ktor defaults.
         *
         * @param serverWrapper Optional lambda to wrap/configure the server engine
         * @param applicationWrapper Optional lambda to wrap/configure the application
         * @return ServerConfiguration with Jetty engine and default deployment config
         */
        fun jetty(
            serverWrapper: ServerWrapper? = null,
            applicationWrapper: ApplicationWrapper? = null
        ): ServerConfiguration = ServerConfiguration(
            engine = loadEngineByName("jetty"),
            deployment = ServerDeploymentConfiguration.createDefault(),
            serverWrapper = serverWrapper,
            applicationWrapper = applicationWrapper
        )

        /**
         * Creates a CIO (Coroutine-based I/O) server configuration with minimal Ktor defaults.
         *
         * @param serverWrapper Optional lambda to wrap/configure the server engine
         * @param applicationWrapper Optional lambda to wrap/configure the application
         * @return ServerConfiguration with CIO engine and default deployment config
         */
        fun cio(
            serverWrapper: ServerWrapper? = null,
            applicationWrapper: ApplicationWrapper? = null
        ): ServerConfiguration = ServerConfiguration(
            engine = loadEngineByName("cio"),
            deployment = ServerDeploymentConfiguration.createDefault(),
            serverWrapper = serverWrapper,
            applicationWrapper = applicationWrapper
        )

        /**
         * Load an engine object by name using reflection.
         * @param engineName The name of the engine (netty, jetty, cio)
         * @return The engine object singleton instance
         * @throws IllegalStateException if the engine cannot be loaded
         */
        fun loadEngineByName(engineName: String): KatalystKtorEngine {
            return try {
                val engineClassName = when (engineName) {
                    "netty" -> "com.ead.katalyst.ktor.engine.netty.NettyEngine"
                    "jetty" -> "com.ead.katalyst.ktor.engine.jetty.JettyEngine"
                    "cio" -> "com.ead.katalyst.ktor.engine.cio.CioEngine"
                    else -> throw IllegalStateException("Unknown engine name: $engineName")
                }

                val engineClass = Class.forName(engineClassName)
                val instanceField = engineClass.getDeclaredField("INSTANCE")
                instanceField.get(null) as KatalystKtorEngine
            } catch (e: ClassNotFoundException) {
                throw IllegalStateException(
                    "Engine '${engineName}' not found on classpath. " +
                    "Ensure katalyst-ktor-engine-${engineName} dependency is included.",
                    e
                )
            } catch (e: NoSuchFieldException) {
                throw IllegalStateException(
                    "Engine '${engineName}' INSTANCE field not found. " +
                    "Engine class may not be a Kotlin object.",
                    e
                )
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to load engine '${engineName}': ${e.message}",
                    e
                )
            }
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
