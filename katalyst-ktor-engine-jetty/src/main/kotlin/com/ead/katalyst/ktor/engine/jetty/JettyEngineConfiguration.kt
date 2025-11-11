package com.ead.katalyst.ktor.engine.jetty

/**
 * Jetty-specific engine configuration.
 * Holds Jetty-specific settings like thread pool configuration.
 *
 * Jetty is a mature, production-ready servlet container with excellent
 * compatibility with Java web standards.
 *
 * Performance Characteristics:
 * - Throughput: High (good req/s)
 * - Latency: Low
 * - Memory: Low to Moderate
 * - Concurrency: Good (thousands of connections)
 * - Best For: Compatibility with standard Java web libraries
 */
data class JettyEngineConfiguration(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val maxThreads: Int = 100,
    val minThreads: Int = 10,
    val connectionIdleTimeoutMs: Long = 180000L // 3 minutes
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be in range 1-65535" }
        require(maxThreads > 0) { "maxThreads must be positive" }
        require(minThreads > 0) { "minThreads must be positive" }
        require(minThreads <= maxThreads) { "minThreads must be <= maxThreads" }
        require(connectionIdleTimeoutMs > 0) { "connectionIdleTimeoutMs must be positive" }
    }
}
