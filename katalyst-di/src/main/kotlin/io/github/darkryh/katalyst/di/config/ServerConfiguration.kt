package io.github.darkryh.katalyst.di.config

import io.ktor.server.application.*
import io.ktor.server.engine.*

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
    val engine: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>? = null,
    val deployment: ServerDeploymentConfiguration,
    val serverWrapper: ServerWrapper? = null,
    val applicationWrapper: ApplicationWrapper? = null
) {
    // Convenience accessors for backward compatibility with existing code
    val host: String get() = deployment.host
    val port: Int get() = deployment.port
    val workerThreads: Int get() = deployment.workerGroupSize
    val connectionIdleTimeoutMs: Long get() = deployment.connectionIdleTimeoutMs

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
