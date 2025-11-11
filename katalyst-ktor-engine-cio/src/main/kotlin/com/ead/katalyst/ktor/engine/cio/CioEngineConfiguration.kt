package com.ead.katalyst.ktor.engine.cio

/**
 * CIO (Coroutine-based I/O) engine configuration.
 * Holds CIO-specific settings.
 *
 * CIO is a lightweight, pure-Kotlin, coroutine-based I/O engine with no
 * external dependencies. It's ideal for microservices and cloud-native applications.
 *
 * Performance Characteristics:
 * - Throughput: Good (moderate to high req/s)
 * - Latency: Low
 * - Memory: Very Low
 * - Concurrency: Excellent (native coroutines)
 * - Best For: Cloud-native, lightweight services, microservices
 */
data class CioEngineConfiguration(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val connectionIdleTimeoutMs: Long = 180000L // 3 minutes
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be in range 1-65535" }
        require(connectionIdleTimeoutMs > 0) { "connectionIdleTimeoutMs must be positive" }
    }
}
