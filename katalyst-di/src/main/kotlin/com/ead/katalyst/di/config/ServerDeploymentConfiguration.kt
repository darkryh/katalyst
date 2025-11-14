package com.ead.katalyst.di.config

/**
 * SSL/TLS Configuration for Ktor servers.
 *
 * Only required when sslPort is configured.
 * All fields are required if SSL is enabled.
 *
 * @param keyStore Path to the SSL key store (JKS or PKCS12 format)
 * @param keyAlias Alias for the certificate in the key store
 * @param keyStorePassword Password for the key store
 * @param privateKeyPassword Password for the private key
 * @param provider Optional: KeyStore provider (default: SunX509)
 * @param keyManagerFactory Optional: KeyManagerFactory algorithm
 * @param trustManagerFactory Optional: TrustManagerFactory algorithm
 * @param trustStore Optional: Path to trust store for client certificates
 * @param trustStorePassword Optional: Password for trust store
 */
data class SslConfiguration(
    val keyStore: String,
    val keyAlias: String,
    val keyStorePassword: String,
    val privateKeyPassword: String,
    val provider: String? = null,
    val keyManagerFactory: String? = null,
    val trustManagerFactory: String? = null,
    val trustStore: String? = null,
    val trustStorePassword: String? = null
) {
    fun validate() {
        require(keyStore.isNotBlank()) { "keyStore must not be blank" }
        require(keyAlias.isNotBlank()) { "keyAlias must not be blank" }
        require(keyStorePassword.isNotBlank()) { "keyStorePassword must not be blank" }
        require(privateKeyPassword.isNotBlank()) { "privateKeyPassword must not be blank" }
    }
}

/**
 * Complete Ktor server deployment configuration.
 *
 * This class encapsulates all Ktor predefined deployment properties and maps them
 * from `application.yaml` under `ktor.deployment.*` and `ktor.security.ssl.*`.
 *
 * All properties except optional ones are required to be present in the YAML configuration.
 * If a required property is missing, ServerDeploymentConfigurationLoader will throw an exception.
 *
 * **Network Configuration:**
 * - host: Bind address (e.g., "0.0.0.0" for all interfaces, "localhost" for local only)
 * - port: HTTP listen port (1-65535)
 * - sslPort: HTTPS listen port (1-65535, optional, requires SSL config)
 *
 * **Shutdown Configuration:**
 * - shutdownGracePeriod: Maximum time (ms) to accept new requests before forcing shutdown
 * - shutdownTimeout: Maximum time (ms) to wait for graceful shutdown before killing server
 * - shutdownUrl: Optional endpoint for triggering shutdown
 *
 * **Context & Routing:**
 * - rootPath: Servlet context path (default: "/")
 * - watchPaths: Optional file paths to watch for auto-reloading during development
 *
 * **Thread Pool Configuration (Netty-specific, but defined here for consistency):**
 * - connectionGroupSize: Threads for accepting new connections and starting call processing
 * - workerGroupSize: Event group threads for parsing messages and internal engine work
 * - callGroupSize: Minimum threads for executing application code
 * Can be set to "auto" to use system defaults
 *
 * **HTTP Protocol Limits:**
 * - maxInitialLineLength: Maximum HTTP request line length (e.g., "GET /path HTTP/1.1")
 * - maxHeaderSize: Maximum total size of all HTTP headers
 * - maxChunkSize: Maximum chunk size for chunked transfer encoding
 *
 * **Timeout Configuration:**
 * - connectionIdleTimeoutMs: Keep-alive timeout for idle connections
 * - requestTimeoutMs: Optional maximum time to process a single request
 *
 * **Jetty-specific:**
 * - maxThreads: Maximum thread pool size (Jetty only, optional)
 * - minThreads: Minimum thread pool size (Jetty only, optional)
 *
 * **SSL/TLS (Optional):**
 * - sslConfig: SSL configuration, required if sslPort is set
 *
 * @throws IllegalArgumentException if validation fails (see init block)
 */
data class ServerDeploymentConfiguration(
    // Network Binding
    val host: String,
    val port: Int,
    val sslPort: Int? = null,

    // Shutdown Configuration
    val shutdownGracePeriod: Long,
    val shutdownTimeout: Long,
    val shutdownUrl: String? = null,

    // Context & Routing
    val rootPath: String = "/",
    val watchPaths: List<String> = emptyList(),

    // Thread Pools (Netty)
    val connectionGroupSize: Int,
    val workerGroupSize: Int,
    val callGroupSize: Int,

    // HTTP Protocol Limits
    val maxInitialLineLength: Int,
    val maxHeaderSize: Int,
    val maxChunkSize: Int,

    // Timeouts
    val connectionIdleTimeoutMs: Long,
    val requestTimeoutMs: Long? = null,

    // Jetty-specific
    val maxThreads: Int? = null,
    val minThreads: Int? = null,

    // SSL/TLS Configuration
    val sslConfig: SslConfiguration? = null
) {
    init {
        // Host validation
        require(host.isNotBlank()) { "host must not be blank" }

        // Port validation
        require(port in 1..65535) { "port must be in range 1-65535, got $port" }
        if (sslPort != null) {
            require(sslPort in 1..65535) { "sslPort must be in range 1-65535, got $sslPort" }
        }

        // Shutdown validation
        require(shutdownGracePeriod > 0) { "shutdownGracePeriod must be > 0, got $shutdownGracePeriod" }
        require(shutdownTimeout > 0) { "shutdownTimeout must be > 0, got $shutdownTimeout" }

        // Context validation
        require(rootPath.isNotBlank()) { "rootPath must not be blank" }

        // Thread pool validation
        require(connectionGroupSize > 0) { "connectionGroupSize must be > 0, got $connectionGroupSize" }
        require(workerGroupSize > 0) { "workerGroupSize must be > 0, got $workerGroupSize" }
        require(callGroupSize > 0) { "callGroupSize must be > 0, got $callGroupSize" }

        // HTTP protocol validation
        require(maxInitialLineLength > 0) { "maxInitialLineLength must be > 0, got $maxInitialLineLength" }
        require(maxHeaderSize > 0) { "maxHeaderSize must be > 0, got $maxHeaderSize" }
        require(maxChunkSize > 0) { "maxChunkSize must be > 0, got $maxChunkSize" }

        // Timeout validation
        require(connectionIdleTimeoutMs > 0) { "connectionIdleTimeoutMs must be > 0, got $connectionIdleTimeoutMs" }
        if (requestTimeoutMs != null) {
            require(requestTimeoutMs > 0) { "requestTimeoutMs must be > 0, got $requestTimeoutMs" }
        }

        // Jetty-specific validation
        if (maxThreads != null) {
            require(maxThreads > 0) { "maxThreads must be > 0, got $maxThreads" }
        }
        if (minThreads != null) {
            require(minThreads > 0) { "minThreads must be > 0, got $minThreads" }
        }
        if (maxThreads != null && minThreads != null) {
            require(maxThreads >= minThreads) { "maxThreads ($maxThreads) must be >= minThreads ($minThreads)" }
        }

        // SSL Validation
        if (sslPort != null) {
            require(sslConfig != null) { "sslConfig is required when sslPort is set" }
            sslConfig?.validate()
        }
    }

    companion object {
        /**
         * Create a ServerDeploymentConfiguration with sensible Ktor defaults.
         *
         * These defaults match Ktor's standard configuration:
         * - host: "0.0.0.0" (all interfaces)
         * - port: 8080
         * - shutdownGracePeriod: 1000ms
         * - shutdownTimeout: 5000ms
         * - Thread pools: 8 threads each
         * - HTTP limits: standard safe limits
         * - connectionIdleTimeoutMs: 180000ms (3 minutes)
         *
         * These defaults can be overridden by values loaded from application.yaml
         * via ServerDeploymentConfigurationLoader.
         *
         * @return Default ServerDeploymentConfiguration instance
         */
        fun createDefault(): ServerDeploymentConfiguration {
            return ServerDeploymentConfiguration(
                host = "0.0.0.0",
                port = 8080,
                sslPort = null,
                shutdownGracePeriod = 1000L,
                shutdownTimeout = 5000L,
                shutdownUrl = null,
                rootPath = "/",
                watchPaths = emptyList(),
                connectionGroupSize = 8,
                workerGroupSize = 8,
                callGroupSize = 8,
                maxInitialLineLength = 4096,
                maxHeaderSize = 8192,
                maxChunkSize = 8192,
                connectionIdleTimeoutMs = 180000L,
                requestTimeoutMs = null,
                maxThreads = null,
                minThreads = null,
                sslConfig = null
            )
        }
    }
}
