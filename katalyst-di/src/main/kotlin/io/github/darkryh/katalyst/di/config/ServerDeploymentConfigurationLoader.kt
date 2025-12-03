package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.config.provider.ConfigLoaders
import io.github.darkryh.katalyst.config.provider.ServiceConfigLoader
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * Loads ktor.deployment.* into [ServerDeploymentConfiguration] from a ConfigProvider.
 * Kept in DI module to avoid circular dependency with config-provider.
 */
object ServerDeploymentConfigurationLoader : ServiceConfigLoader<ServerDeploymentConfiguration> {
    private val log = LoggerFactory.getLogger(ServerDeploymentConfigurationLoader::class.java)

    override fun loadConfig(provider: ConfigProvider): ServerDeploymentConfiguration {
        log.debug("Loading server deployment configuration...")

        val host = ConfigLoaders.loadRequiredString(provider, "ktor.deployment.host")
        val port = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.port")
        val sslPort = provider.getString("ktor.deployment.sslPort")?.toIntOrNull()

        val shutdownGracePeriod = ConfigLoaders.loadRequiredLong(provider, "ktor.deployment.shutdownGracePeriod")
        val shutdownTimeout = ConfigLoaders.loadRequiredLong(provider, "ktor.deployment.shutdownTimeout")
        val shutdownUrl = provider.getString("ktor.deployment.shutdownUrl")

        val rootPath = ConfigLoaders.loadOptionalString(provider, "ktor.deployment.rootPath", "/")
        val watchPaths: List<String> = runCatching { provider.getList("ktor.deployment.watchPaths") ?: emptyList() }
            .getOrElse { emptyList() }
            .map { it.toString() }

        val connectionGroupSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.connectionGroupSize")
        val workerGroupSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.workerGroupSize")
        val callGroupSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.callGroupSize")

        val maxInitialLineLength = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.maxInitialLineLength")
        val maxHeaderSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.maxHeaderSize")
        val maxChunkSize = ConfigLoaders.loadRequiredInt(provider, "ktor.deployment.maxChunkSize")

        val connectionIdleTimeoutMs = ConfigLoaders.loadRequiredLong(provider, "ktor.deployment.connectionIdleTimeoutMs")
        val requestTimeoutMs = provider.getString("ktor.deployment.requestTimeoutMs")?.toLongOrNull()

        val maxThreads = provider.getString("ktor.deployment.maxThreads")?.toIntOrNull()
        val minThreads = provider.getString("ktor.deployment.minThreads")?.toIntOrNull()

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
                "✓ Server deployment configuration loaded: host={}, port={}, shutdownGracePeriod={}ms, shutdownTimeout={}ms",
                host, port, shutdownGracePeriod, shutdownTimeout
            )
        }
    }

    override fun validate(config: ServerDeploymentConfiguration) {
        if (config.sslPort != null && config.sslPort > 0) {
            config.sslConfig ?: throw ConfigException("sslPort is set but no SSL configuration was provided")
            config.sslConfig?.validate()
        }

        if (config.maxThreads != null && config.maxThreads <= 0) {
            throw ConfigException("ktor.deployment.maxThreads must be > 0 when set")
        }
        if (config.minThreads != null && config.minThreads <= 0) {
            throw ConfigException("ktor.deployment.minThreads must be > 0 when set")
        }
    }

    private fun loadSslConfiguration(provider: ConfigProvider): SslConfiguration? {
        return try {
            val keyStore = provider.getString("ktor.security.ssl.keyStore").ifBlank { null }
            if (keyStore != null) {
                val keyAlias = ConfigLoaders.loadRequiredString(provider, "ktor.security.ssl.keyAlias")
                val keyStorePassword = ConfigLoaders.loadRequiredString(provider, "ktor.security.ssl.keyStorePassword")
                val privateKeyPassword = ConfigLoaders.loadRequiredString(provider, "ktor.security.ssl.privateKeyPassword")
                val providerName = provider.getString("ktor.security.ssl.provider")
                val keyManagerFactory = provider.getString("ktor.security.ssl.keyManagerFactory")
                val trustManagerFactory = provider.getString("ktor.security.ssl.trustManagerFactory")
                val trustStore = provider.getString("ktor.security.ssl.trustStore")
                val trustStorePassword = provider.getString("ktor.security.ssl.trustStorePassword")

                SslConfiguration(
                    keyStore = keyStore,
                    keyAlias = keyAlias,
                    keyStorePassword = keyStorePassword,
                    privateKeyPassword = privateKeyPassword,
                    provider = providerName,
                    keyManagerFactory = keyManagerFactory,
                    trustManagerFactory = trustManagerFactory,
                    trustStore = trustStore,
                    trustStorePassword = trustStorePassword
                )
            } else {
                null
            }
        } catch (e: Exception) {
            throw ConfigException("Invalid SSL configuration: ${e.message}", e)
        }
    }
}
