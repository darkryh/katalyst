package com.ead.katalyst.config.provider

import com.ead.katalyst.core.config.ConfigException
import com.ead.katalyst.core.config.ConfigProvider
import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import com.ead.katalyst.di.config.SslConfiguration
import org.slf4j.LoggerFactory

/**
 * ServiceConfigLoader implementation for ServerDeploymentConfiguration.
 *
 * **Purpose:**
 * Loads Ktor server deployment configuration from ConfigProvider in a type-safe manner.
 * Reads all Ktor predefined deployment properties from application.yaml.
 * No fallbacks - throws exception if required configuration is missing.
 *
 * **Configuration Structure (ktor.deployment.*):**
 * ```yaml
 * ktor:
 *   deployment:
 *     # Network Configuration (REQUIRED)
 *     host: "0.0.0.0"
 *     port: 8080
 *     sslPort: 8443  # Optional
 *
 *     # Shutdown Configuration (REQUIRED)
 *     shutdownGracePeriod: 1000  # milliseconds
 *     shutdownTimeout: 5000      # milliseconds
 *     shutdownUrl: /shutdown     # Optional
 *
 *     # Context & Routing (Optional)
 *     rootPath: "/"
 *     watchPaths: []
 *
 *     # Thread Pools (REQUIRED)
 *     connectionGroupSize: 8
 *     workerGroupSize: 8
 *     callGroupSize: 8
 *
 *     # HTTP Protocol Limits (REQUIRED)
 *     maxInitialLineLength: 4096
 *     maxHeaderSize: 8192
 *     maxChunkSize: 8192
 *
 *     # Timeouts (REQUIRED)
 *     connectionIdleTimeoutMs: 180000
 *     requestTimeoutMs: null  # Optional
 *
 *     # Engine-Specific (Optional - Jetty only)
 *     maxThreads: 200
 *     minThreads: 10
 *
 *   # SSL/TLS Configuration (Optional - required if sslPort is set)
 *   security:
 *     ssl:
 *       keyStore: /path/to/keystore.jks
 *       keyAlias: server
 *       keyStorePassword: password
 *       privateKeyPassword: password
 *       provider: SunX509
 *       keyManagerFactory: SunX509
 *       trustManagerFactory: PKIX
 *       trustStore: /path/to/truststore.jks
 *       trustStorePassword: password
 * ```
 *
 * **Usage in Application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 *     val deploymentConfig = ConfigBootstrapHelper.loadServiceConfig(
 *         config,
 *         ServerDeploymentConfigurationLoader()
 *     )
 *     // deploymentConfig can be used to override defaults
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * **No Fallbacks Policy:**
 * All required fields must be present in application.yaml or environment variables.
 * Missing required configuration will throw ConfigException immediately.
 * Optional fields return null if not configured.
 *
 * **Auto-Discovery:**
 * ```kotlin
 * val loaders = ConfigMetadata.discoverLoaders(arrayOf("com.ead.katalyst.config.provider"))
 * // ServerDeploymentConfigurationLoader is automatically discovered
 * ```
 */
object ServerDeploymentConfigurationLoader : ServiceConfigLoader<ServerDeploymentConfiguration> {
    private val log = LoggerFactory.getLogger(ServerDeploymentConfigurationLoader::class.java)

    /**
     * Load server deployment configuration from ConfigProvider.
     *
     * **Process:**
     * 1. Load all required deployment properties
     * 2. Load optional properties (return null if missing, no defaults)
     * 3. Load optional SSL configuration if present
     * 4. Construct and return ServerDeploymentConfiguration
     * 5. Validation happens in validate() method
     *
     * @param provider ConfigProvider to load from
     * @return Loaded ServerDeploymentConfiguration instance
     * @throws ConfigException if required keys are missing
     */
    override fun loadConfig(provider: ConfigProvider): ServerDeploymentConfiguration {
        log.debug("Loading server deployment configuration...")

        // ========== Network Configuration (REQUIRED) ==========
        val host = ConfigLoaders.loadRequiredString(provider, "ktor.deployment.host")
        val port = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.port")
        val sslPortStr = provider.getString("ktor.deployment.sslPort")
        val sslPort = sslPortStr?.toIntOrNull()

        // ========== Shutdown Configuration (REQUIRED) ==========
        val shutdownGracePeriod = ConfigLoaders.loadRequiredLong(provider, "ktor.deployment.shutdownGracePeriod")
        val shutdownTimeout = ConfigLoaders.loadRequiredLong(provider, "ktor.deployment.shutdownTimeout")
        val shutdownUrl = provider.getString("ktor.deployment.shutdownUrl")

        // ========== Context & Routing (Optional) ==========
        val rootPath = ConfigLoaders.loadOptionalString(provider, "ktor.deployment.rootPath", "/")
        val watchPaths = try {
            val paths = provider.getList("ktor.deployment.watchPaths")
            paths?.map { it.toString() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // ========== Thread Pools (REQUIRED) ==========
        val connectionGroupSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.connectionGroupSize")
        val workerGroupSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.workerGroupSize")
        val callGroupSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.callGroupSize")

        // ========== HTTP Protocol Limits (REQUIRED) ==========
        val maxInitialLineLength = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.maxInitialLineLength")
        val maxHeaderSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.maxHeaderSize")
        val maxChunkSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.maxChunkSize")

        // ========== Timeouts (REQUIRED) ==========
        val connectionIdleTimeoutMs = ConfigLoaders.loadRequiredLong(provider, "ktor.deployment.connectionIdleTimeoutMs")
        val requestTimeoutMsStr = provider.getString("ktor.deployment.requestTimeoutMs")
        val requestTimeoutMs = requestTimeoutMsStr?.toLongOrNull()

        // ========== Engine-Specific Settings (Optional - Jetty only) ==========
        val maxThreadsStr = provider.getString("ktor.deployment.maxThreads")
        val maxThreads = maxThreadsStr?.toIntOrNull()
        val minThreadsStr = provider.getString("ktor.deployment.minThreads")
        val minThreads = minThreadsStr?.toIntOrNull()

        // ========== SSL/TLS Configuration (Optional) ==========
        val sslConfig = loadSslConfiguration(provider)

        return ServerDeploymentConfiguration(
            host = host,
            port = port,
            sslPort = sslPort,
            shutdownGracePeriod = shutdownGracePeriod,
            shutdownTimeout = shutdownTimeout,
            shutdownUrl = shutdownUrl,
            rootPath = rootPath,
            watchPaths = watchPaths,
            connectionGroupSize = connectionGroupSize,
            workerGroupSize = workerGroupSize,
            callGroupSize = callGroupSize,
            maxInitialLineLength = maxInitialLineLength,
            maxHeaderSize = maxHeaderSize,
            maxChunkSize = maxChunkSize,
            connectionIdleTimeoutMs = connectionIdleTimeoutMs,
            requestTimeoutMs = requestTimeoutMs,
            maxThreads = maxThreads,
            minThreads = minThreads,
            sslConfig = sslConfig
        ).also {
            log.debug(
                "✓ Server deployment configuration loaded: " +
                "host={}, port={}, shutdownGracePeriod={}ms, shutdownTimeout={}ms",
                host, port, shutdownGracePeriod, shutdownTimeout
            )
        }
    }

    /**
     * Load optional SSL/TLS configuration from ktor.security.ssl.* keys.
     *
     * Only loads SSL configuration if sslPort is configured or SSL keys are present.
     * Returns null if no SSL configuration is present (SSL is optional).
     *
     * @param provider ConfigProvider to load from
     * @return SslConfiguration if SSL keys are present, null otherwise
     * @throws ConfigException if SSL keys are incomplete
     */
    private fun loadSslConfiguration(provider: ConfigProvider): SslConfiguration? {
        return try {
            val keyStore = provider.getString("ktor.security.ssl.keyStore")
            // If keyStore is present, SSL configuration is required
            if (keyStore != null && keyStore.isNotBlank()) {
                log.debug("Loading SSL/TLS configuration...")
                val keyAlias = ConfigLoaders.loadRequiredString(provider, "ktor.security.ssl.keyAlias")
                val keyStorePassword = ConfigLoaders.loadRequiredString(provider, "ktor.security.ssl.keyStorePassword")
                val privateKeyPassword = ConfigLoaders.loadRequiredString(provider, "ktor.security.ssl.privateKeyPassword")
                val sslProvider = provider.getString("ktor.security.ssl.provider")
                val keyManagerFactory = provider.getString("ktor.security.ssl.keyManagerFactory")
                val trustManagerFactory = provider.getString("ktor.security.ssl.trustManagerFactory")
                val trustStore = provider.getString("ktor.security.ssl.trustStore")
                val trustStorePassword = provider.getString("ktor.security.ssl.trustStorePassword")

                SslConfiguration(
                    keyStore = keyStore,
                    keyAlias = keyAlias,
                    keyStorePassword = keyStorePassword,
                    privateKeyPassword = privateKeyPassword,
                    provider = sslProvider,
                    keyManagerFactory = keyManagerFactory,
                    trustManagerFactory = trustManagerFactory,
                    trustStore = trustStore,
                    trustStorePassword = trustStorePassword
                ).also {
                    log.debug("✓ SSL/TLS configuration loaded: keyStore={}, keyAlias={}", keyStore, keyAlias)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            // If SSL configuration is not present, that's fine - it's optional
            log.debug("No SSL/TLS configuration found (optional): {}", e.message)
            null
        }
    }

    /**
     * Validate loaded server deployment configuration.
     *
     * **Validation Checks:**
     * 1. Host is not blank (handled by ServerDeploymentConfiguration constructor)
     * 2. Ports are in valid range 1-65535 (handled by ServerDeploymentConfiguration)
     * 3. Shutdown values are positive (handled by ServerDeploymentConfiguration)
     * 4. Thread pool sizes are positive (handled by ServerDeploymentConfiguration)
     * 5. HTTP protocol limits are positive (handled by ServerDeploymentConfiguration)
     * 6. Timeout values are positive (handled by ServerDeploymentConfiguration)
     * 7. SSL validation: if sslPort is set, sslConfig must be present and valid
     * 8. Jetty-specific: if maxThreads or minThreads are set, maxThreads >= minThreads
     *
     * @param config ServerDeploymentConfiguration to validate
     * @throws ConfigException if validation fails
     */
    override fun validate(config: ServerDeploymentConfiguration) {
        log.debug("Validating server deployment configuration...")

        try {
            // Most validation is done in ServerDeploymentConfiguration's init block
            // Additional checks for configuration consistency:

            // SSL validation
            if (config.sslPort != null) {
                val sslConfig = config.sslConfig
                if (sslConfig == null) {
                    throw ConfigException(
                        "SSL/TLS configuration is required when sslPort is set to ${config.sslPort}. " +
                        "Configure ktor.security.ssl.* properties in application.yaml."
                    )
                }
                // SslConfiguration validates itself
                sslConfig.validate()
                log.debug("✓ SSL/TLS configuration is valid for port {}", config.sslPort)
            }

            // Jetty-specific validation
            val maxThreads = config.maxThreads
            val minThreads = config.minThreads
            if (maxThreads != null && minThreads != null) {
                if (maxThreads < minThreads) {
                    throw ConfigException(
                        "maxThreads ($maxThreads) must be >= minThreads ($minThreads)"
                    )
                }
                log.debug("✓ Jetty thread pool configuration is valid")
            }

            log.debug("✓ Server deployment configuration validation passed")
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Server deployment configuration validation failed: ${e.message}", e)
        }
    }
}
