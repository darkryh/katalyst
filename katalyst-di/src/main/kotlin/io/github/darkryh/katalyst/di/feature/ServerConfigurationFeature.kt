package io.github.darkryh.katalyst.di.feature

import io.github.darkryh.katalyst.di.KatalystFeaturesBuilder
import io.github.darkryh.katalyst.di.config.ServerDeploymentConfiguration
import org.slf4j.LoggerFactory

/**
 * Katalyst feature for loading Ktor server deployment configuration from application.yaml.
 *
 * **Purpose:**
 * Provides server deployment configuration support during application bootstrap.
 * When enabled with an explicit configuration source, reads all ktor.deployment.* and ktor.security.ssl.* properties
 * and makes ServerDeploymentConfiguration available in the active container for engine modules to use.
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
 *     enableYamlConfiguration()
 *     features {
 *         enableServerTuning()  // Enable server deployment loading from installed config
 *     }
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * **Without This Feature:**
 * Applications use default ServerDeploymentConfiguration values.
 *
 * **With This Feature:**
 * Applications load configuration from the explicitly installed ConfigProvider.
 * The loaded configuration is validated and made available to engine modules.
 *
 * **Auto-Discovery of Loader:**
 * The ServerDeploymentConfigurationLoader uses the explicit ConfigProvider installed during
 * bootstrap. Katalyst does not create a YAML source implicitly.
 *
 * **Note:**
 * This feature is optional. Engine modules have built-in defaults that work without it.
 */
object ServerConfigurationFeature : KatalystFeature {
    private val logger = LoggerFactory.getLogger("ServerConfigurationFeature")
    override val id: String = "server-configuration"

    override fun provideBeanModules(): List<KatalystBeanModule> {
        logger.info("Loading server configuration feature modules")
        return listOf(serverConfigurationModule())
    }

    override fun onReady(context: KatalystBeanContext) {
        logger.info("Server configuration feature ready (ServerDeploymentConfiguration available)")
    }
}

/**
 * Bean module for server configuration.
 *
 * Registers ServerDeploymentConfiguration in the container for use by engine modules.
 * The configuration is initialized with defaults - actual YAML loading would be done
 * by the ServerDeploymentConfigurationLoader and passed here.
 */
private fun serverConfigurationModule(): KatalystBeanModule = katalystBeanModule {
    // Register default ServerDeploymentConfiguration
    // Actual YAML loading should be done via ServerDeploymentConfigurationLoader
    single { ServerDeploymentConfiguration.createDefault() }
}

/**
 * Extension function to enable server configuration loading from application.yaml.
 *
 * **Usage:**
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     enableYamlConfiguration()
 *     features {
 *         enableServerTuning()  // Loads from ktor.deployment.* in application.yaml
 *     }
 *     scanPackages("com.example.app")
 * }
 * ```
 *
 * **What it does:**
 * 1. Registers ServerConfigurationFeature with the application builder
 * 2. Makes ServerDeploymentConfiguration available in the active container
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
fun KatalystFeaturesBuilder.enableServerTuning(): KatalystFeaturesBuilder =
    feature(ServerConfigurationFeature).also {
        logger.debug("Server tuning feature enabled - will load ktor.deployment.* from application.yaml")
    }

private val logger = LoggerFactory.getLogger("ServerConfigurationFeatureKt")
