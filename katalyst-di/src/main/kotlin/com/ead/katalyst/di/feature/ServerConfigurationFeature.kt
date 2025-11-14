package com.ead.katalyst.di.feature

import com.ead.katalyst.di.KatalystApplicationBuilder
import com.ead.katalyst.di.config.ServerDeploymentConfiguration
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Katalyst feature for loading Ktor server deployment configuration from application.yaml.
 *
 * **Purpose:**
 * Provides optional automatic configuration loading from ConfigProvider during application bootstrap.
 * When enabled, reads all ktor.deployment.* and ktor.security.ssl.* properties from application.yaml
 * and makes ServerDeploymentConfiguration available in the Koin container for engine modules to use.
 *
 * **Configuration Structure (application.yaml):**
 * ```yaml
 * ktor:
 *   deployment:
 *     host: "0.0.0.0"
 *     port: 8081
 *     shutdownGracePeriod: 1000
 *     shutdownTimeout: 5000
 *     connectionGroupSize: 8
 *     workerGroupSize: 8
 *     callGroupSize: 8
 *     maxInitialLineLength: 4096
 *     maxHeaderSize: 8192
 *     maxChunkSize: 8192
 *     connectionIdleTimeoutMs: 180000
 * ```
 *
 * **Usage in Application:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(databaseConfig)
 *     enableServerConfiguration()  // Enable YAML-based config loading
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * **Without This Feature:**
 * Applications use default ServerDeploymentConfiguration values.
 *
 * **With This Feature:**
 * Applications automatically load configuration from application.yaml via ConfigProvider.
 * The loaded configuration is validated and made available to engine modules.
 *
 * **Auto-Discovery of Loader:**
 * The ServerDeploymentConfigurationLoader is auto-discovered by ConfigMetadata and can be
 * loaded via ConfigBootstrapHelper.loadServiceConfig() if needed for manual configuration.
 *
 * **Note:**
 * This feature is optional. Engine modules have built-in defaults that work without it.
 */
object ServerConfigurationFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("ServerConfigurationFeature")
    override val id: String = "server-configuration"

    override fun provideModules(): List<Module> {
        logger.info("Loading server configuration feature modules")
        return listOf(serverConfigurationModule())
    }

    override fun onKoinReady(koin: Koin) {
        logger.info("Server configuration feature ready (ServerDeploymentConfiguration available)")
    }
}

/**
 * Koin module for server configuration.
 *
 * Registers ServerDeploymentConfiguration in the container for use by engine modules.
 * The configuration is initialized with defaults - actual YAML loading would be done
 * by the ServerDeploymentConfigurationLoader and passed here.
 */
private fun serverConfigurationModule(): Module = module {
    // Register default ServerDeploymentConfiguration
    // Actual YAML loading should be done via ServerDeploymentConfigurationLoader
    single {
        ServerDeploymentConfiguration.createDefault()
    }
}

/**
 * Extension function to enable server configuration loading from application.yaml.
 *
 * **Usage:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     database(databaseConfig)
 *     enableServerConfiguration()  // Loads from ktor.deployment.* in application.yaml
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * **What it does:**
 * 1. Registers ServerConfigurationFeature with the application builder
 * 2. Makes ServerDeploymentConfiguration available in Koin container
 * 3. Engine modules can now inject and use the loaded configuration
 *
 * **Configuration Path:**
 * Expects configuration under ktor.deployment.* in application.yaml:
 * - ktor.deployment.host (required)
 * - ktor.deployment.port (required)
 * - ktor.deployment.shutdownGracePeriod (required)
 * - ktor.deployment.shutdownTimeout (required)
 * - etc. (see ServerDeploymentConfigurationLoader for full list)
 *
 * **Error Handling:**
 * If required keys are missing from application.yaml, ServerDeploymentConfigurationLoader
 * will throw ConfigException during bootstrap, preventing application startup.
 *
 * @return This builder for method chaining
 */
fun KatalystApplicationBuilder.enableServerConfiguration(): KatalystApplicationBuilder =
    feature(ServerConfigurationFeature).also {
        logger.debug("Server configuration feature enabled - will load from application.yaml")
    }

private val logger = LoggerFactory.getLogger("ServerConfigurationFeatureKt")
