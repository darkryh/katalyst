package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.ktor.server.engine.*
import org.slf4j.Logger

/**
 * Resolves [ServerDeploymentConfiguration] by merging CLI args (Ktor-compatible)
 * with the explicitly configured [ConfigProvider] source.
 */
internal class ServerConfigurationResolver(
    private val bootstrapArgs: BootstrapArgs,
    private val logger: Logger,
    private val configurationSource: () -> ConfigProvider?
) {

    fun resolveDeployment(): ServerDeploymentConfiguration {
        val providers = buildProviders()

        if (providers.isEmpty()) {
            throw IllegalStateException(
                "No configuration source configured. Call enableYamlConfiguration() " +
                    "or configuration(customSource) in the katalystApplication block."
            )
        }

        val compositeProvider = CompositeConfigProvider(providers)
        val loaderInstance = resolveLoaderInstance()

        return try {
            val deployment = invokeLoad(loaderInstance, compositeProvider)
            validate(loaderInstance, deployment)
            logger.info(
                "✓ Server deployment configuration resolved (host={}, port={}, source={})",
                deployment.host,
                deployment.port,
                describeSources(providers)
            )
            deployment
        } catch (error: Exception) {
            logger.error("Error loading server deployment configuration: {}", error.message)
            logger.debug("Full error while loading server deployment configuration", error)
            throw IllegalStateException("Failed to load server deployment configuration: ${error.message}", error)
        }
    }

    private fun buildProviders(): List<ConfigProvider> {
        val providers = mutableListOf<ConfigProvider>()

        buildCliProvider()?.let { providers += it }

        if (bootstrapArgs.forceCliConfig) {
            logger.info("Force CLI mode enabled for server configuration; skipping ConfigProvider fallback")
            return providers
        }

        resolveBootstrapProvider()?.let { providers += it }

        return providers
    }

    private fun buildCliProvider(): ConfigProvider? {
        return runCatching {
            if (bootstrapArgs.ktorArgs.isEmpty()) {
                return@runCatching null
            }
            val cliConfig = CommandLineConfig(bootstrapArgs.ktorArgs)
            ApplicationConfigProvider(cliConfig.rootConfig.environment.config)
        }.onFailure { error ->
            logger.debug("Could not parse CLI args for server configuration: {}", error.message)
            logger.trace("Full CLI parsing failure", error)
        }.getOrNull()
    }

    private fun resolveBootstrapProvider(): ConfigProvider? {
        if (bootstrapArgs.forceCliConfig) {
            logger.info("Force mode enabled; skipping ConfigProvider bootstrap for server deployment")
            return null
        }
        return configurationSource()
    }

    fun bootstrapConfigProvider(): ConfigProvider? = resolveBootstrapProvider()

    private fun resolveLoaderInstance(): Any {
        return runCatching {
            val loaderClass = Class.forName("io.github.darkryh.katalyst.di.config.ServerDeploymentConfigurationLoader")
            runCatching { loaderClass.getField("INSTANCE").get(null) }
                .getOrElse { loaderClass.kotlin.objectInstance ?: loaderClass.getDeclaredConstructor().newInstance() }
        }.onFailure { error ->
            when (error) {
                is ClassNotFoundException -> logger.warn(
                    "ServerDeploymentConfigurationLoader not found. Include katalyst-config-provider to load ktor.deployment.* from config."
                )
                else -> logger.warn("Failed to load ServerDeploymentConfigurationLoader: {}", error.message)
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                "ServerDeploymentConfigurationLoader is unavailable. Include katalyst-config-provider " +
                    "and configure an explicit source with enableYamlConfiguration() or configuration(customSource).",
                error
            )
        }
    }

    private fun invokeLoad(loader: Any, provider: ConfigProvider): ServerDeploymentConfiguration {
        val loadMethod = loader.javaClass.getMethod("loadConfig", ConfigProvider::class.java)
        @Suppress("UNCHECKED_CAST")
        return loadMethod.invoke(loader, provider) as ServerDeploymentConfiguration
    }

    private fun validate(loader: Any, deployment: ServerDeploymentConfiguration) {
        runCatching {
            val validateMethod = loader.javaClass.getMethod("validate", ServerDeploymentConfiguration::class.java)
            validateMethod.invoke(loader, deployment)
        }.onFailure { error ->
            logger.warn("Server deployment configuration validation reported an issue: {}", error.message)
        }
    }

    private fun describeSources(providers: List<ConfigProvider>): String {
        return providers.joinToString(" + ") { provider ->
            provider::class.simpleName ?: "ConfigProvider"
        }
    }
}
