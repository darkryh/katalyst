package com.ead.katalyst.ktor.engine.jetty

import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import com.ead.katalyst.di.config.SslConfiguration

/**
 * Jetty-specific engine configuration.
 *
 * Extends ServerDeploymentConfiguration with complete Ktor deployment properties
 * for use by the Jetty engine module, with special emphasis on Jetty-specific settings.
 *
 * **Jetty Performance Characteristics:**
 * - Throughput: High (good req/s)
 * - Latency: Low
 * - Memory: Low to Moderate
 * - Concurrency: Good (thousands of connections)
 * - Best For: Compatibility with standard Java web libraries
 *
 * **Jetty-Specific Settings:**
 * - maxThreads: Maximum thread pool size (optional, uses default if not set)
 * - minThreads: Minimum thread pool size (optional, uses default if not set)
 *
 * **Configuration Sources:**
 * - All Ktor deployment properties from application.yaml (ktor.deployment.*)
 * - Jetty-specific maxThreads and minThreads from ktor.deployment.maxThreads and minThreads
 * - SSL configuration from application.yaml (ktor.security.ssl.*) if configured
 * - Validation performed in ServerDeploymentConfiguration.init block
 *
 * @param deployment Complete Ktor server deployment configuration including Jetty-specific settings
 */
data class JettyEngineConfiguration(
    val deployment: ServerDeploymentConfiguration
) {
    // Convenient accessors for Jetty-specific usage
    val host: String get() = deployment.host
    val port: Int get() = deployment.port
    val connectionIdleTimeoutMs: Long get() = deployment.connectionIdleTimeoutMs
    val maxThreads: Int? get() = deployment.maxThreads
    val minThreads: Int? get() = deployment.minThreads
    val connectionGroupSize: Int get() = deployment.connectionGroupSize
    val workerGroupSize: Int get() = deployment.workerGroupSize
    val callGroupSize: Int get() = deployment.callGroupSize
    val maxInitialLineLength: Int get() = deployment.maxInitialLineLength
    val maxHeaderSize: Int get() = deployment.maxHeaderSize
    val maxChunkSize: Int get() = deployment.maxChunkSize
    val shutdownGracePeriod: Long get() = deployment.shutdownGracePeriod
    val shutdownTimeout: Long get() = deployment.shutdownTimeout
    val sslPort: Int? get() = deployment.sslPort
    val sslConfig: SslConfiguration? get() = deployment.sslConfig

    init {
        // All validation is delegated to ServerDeploymentConfiguration
        // No additional validation needed here
    }

    companion object {
        /**
         * Create JettyEngineConfiguration with default deployment settings.
         *
         * Uses sensible Ktor defaults - can be overridden by application.yaml via
         * ServerDeploymentConfigurationLoader.
         *
         * @return JettyEngineConfiguration with default deployment config
         */
        fun default(): JettyEngineConfiguration {
            return JettyEngineConfiguration(
                deployment = ServerDeploymentConfiguration.createDefault()
            )
        }
    }
}
