package com.ead.katalyst.ktor.engine.netty

import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import com.ead.katalyst.di.config.SslConfiguration

/**
 * Netty-specific engine configuration.
 *
 * Extends ServerDeploymentConfiguration with complete Ktor deployment properties
 * for use by the Netty engine module.
 *
 * **Netty Performance Characteristics:**
 * - Throughput: Very High (10,000+ req/s typical)
 * - Latency: Low
 * - Memory: Moderate
 * - Concurrency: Excellent (millions of connections possible)
 * - Best For: Production services requiring high throughput
 *
 * **Configuration Sources:**
 * - All Ktor deployment properties from application.yaml (ktor.deployment.*)
 * - SSL configuration from application.yaml (ktor.security.ssl.*) if configured
 * - Validation performed in ServerDeploymentConfiguration.init block
 *
 * @param deployment Complete Ktor server deployment configuration
 */
data class NettyEngineConfiguration(
    val deployment: ServerDeploymentConfiguration
) {
    // Convenient accessors for Netty-specific usage
    val host: String get() = deployment.host
    val port: Int get() = deployment.port
    val workerGroupSize: Int get() = deployment.workerGroupSize
    val workerThreads: Int get() = workerGroupSize  // Backward compatibility
    val connectionIdleTimeoutMs: Long get() = deployment.connectionIdleTimeoutMs
    val connectionGroupSize: Int get() = deployment.connectionGroupSize
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
         * Create NettyEngineConfiguration with default deployment settings.
         *
         * Uses sensible Ktor defaults - can be overridden by application.yaml via
         * ServerDeploymentConfigurationLoader.
         *
         * @return NettyEngineConfiguration with default deployment config
         */
        fun default(): NettyEngineConfiguration {
            return NettyEngineConfiguration(
                deployment = ServerDeploymentConfiguration.createDefault()
            )
        }
    }
}
