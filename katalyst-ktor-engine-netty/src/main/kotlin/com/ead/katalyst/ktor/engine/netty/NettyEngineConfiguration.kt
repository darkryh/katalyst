package com.ead.katalyst.ktor.engine.netty

/**
 * Netty-specific engine configuration.
 * Holds Netty-specific settings like worker threads.
 *
 * Netty provides high-performance, non-blocking I/O with excellent throughput
 * and is the recommended choice for production deployments.
 *
 * Performance Characteristics:
 * - Throughput: Very High (10,000+ req/s typical)
 * - Latency: Low
 * - Memory: Moderate
 * - Concurrency: Excellent (millions of connections possible)
 * - Best For: Production services requiring high throughput
 */
data class NettyEngineConfiguration(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val workerThreads: Int = Runtime.getRuntime().availableProcessors() * 2,
    val connectionIdleTimeoutMs: Long = 180000L // 3 minutes
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be in range 1-65535" }
        require(workerThreads > 0) { "workerThreads must be positive" }
        require(connectionIdleTimeoutMs > 0) { "connectionIdleTimeoutMs must be positive" }
    }
}
