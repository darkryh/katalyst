package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.scanner.scanner.ReflectionsTypeScanner
import org.slf4j.LoggerFactory

/**
 * Configuration metadata and discovery utility.
 *
 * **Purpose:**
 * Discovers and manages ServiceConfigLoader implementations across the application.
 * Enables automatic configuration loading and validation without manual wiring.
 *
 * **Design:**
 * Uses reflection (via ReflectionsTypeScanner) to find all ServiceConfigLoader
 * implementations in specified packages. Integrates seamlessly with Katalyst's
 * reflection-based component discovery.
 *
 * **Usage Example:**
 * ```kotlin
 * // Discover all config loaders in application packages
 * val loaders = ConfigMetadata.discoverLoaders(arrayOf("com.example.app"))
 *
 * // Validate all loaders have required configuration
 * val config = ConfigBootstrapHelper.loadConfig(YamlConfigProvider::class.java)
 * ConfigMetadata.validateLoaders(config, loaders)
 * ```
 */
object ConfigMetadata {
    private val log = LoggerFactory.getLogger(ConfigMetadata::class.java)

    /**
     * Discover all ServiceConfigLoader implementations in specified packages.
     *
     * **How It Works:**
     * 1. Scans classpath for classes implementing ServiceConfigLoader
     * 2. Instantiates each implementation
     * 3. Returns list of loaders
     *
     * **Performance Note:**
     * Reflection scanning is performed once at startup. The list should be cached.
     *
     * @param scanPackages Array of package names to scan (e.g., ["com.example.app"])
     * @return List of discovered ServiceConfigLoader instances
     * @throws ConfigException if a loader cannot be instantiated
     */
    fun discoverLoaders(scanPackages: Array<String>): List<ServiceConfigLoader<*>> {
        log.info("Discovering ServiceConfigLoader implementations in packages: ${scanPackages.joinToString()}")

        return try {
            @Suppress("UNCHECKED_CAST")
            val scanner = ReflectionsTypeScanner(
                ServiceConfigLoader::class.java as Class<ServiceConfigLoader<Any>>,
                scanPackages.toList()
            )

            // Find all classes implementing ServiceConfigLoader
            val loaderTypes = scanner.discover()

            if (loaderTypes.isEmpty()) {
                log.debug("No ServiceConfigLoader implementations found")
                return emptyList()
            }

            log.debug("Found ${loaderTypes.size} ServiceConfigLoader implementations")

            // Instantiate each loader
            val loaders = mutableListOf<ServiceConfigLoader<*>>()
            for (loaderType in loaderTypes) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val loader = loaderType.getDeclaredConstructor().newInstance() as ServiceConfigLoader<*>
                    loaders.add(loader)
                    log.debug("Loaded: ${loaderType.simpleName}")
                } catch (e: Exception) {
                    throw ConfigException(
                        "Failed to instantiate ServiceConfigLoader ${loaderType.simpleName}: ${e.message}",
                        e
                    )
                }
            }

            log.info("✓ Discovered ${loaders.size} ServiceConfigLoader implementations")
            loaders
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Failed to discover ServiceConfigLoader implementations: ${e.message}", e)
        }
    }

    /**
     * Validate that all loaders can successfully load their configurations.
     *
     * **How It Works:**
     * 1. Attempts to load configuration using each loader
     * 2. Calls validate() on loaded configuration
     * 3. Collects all errors
     * 4. Throws if any loader failed
     *
     * **Purpose:**
     * Provides early feedback if required configuration is missing or invalid.
     * Prevents runtime errors when services try to access configuration.
     *
     * @param config ConfigProvider to load configurations from
     * @param loaders List of ServiceConfigLoader implementations to validate
     * @throws ConfigException if any loader fails
     */
    fun validateLoaders(config: ConfigProvider, loaders: List<ServiceConfigLoader<*>>) {
        log.info("Validating ${loaders.size} ServiceConfigLoader implementations...")

        val errors = mutableListOf<String>()

        for (loader in loaders) {
            try {
                // Try to load configuration
                @Suppress("UNCHECKED_CAST")
                val loadedConfig = (loader as ServiceConfigLoader<Any>).loadConfig(config)

                // Try to validate configuration
                (loader as ServiceConfigLoader<Any>).validate(loadedConfig)

                log.debug("✓ Loader validated: ${loader::class.simpleName}")
            } catch (e: Exception) {
                errors.add("${loader::class.simpleName}: ${e.message}")
                log.warn("✗ Loader validation failed: ${loader::class.simpleName} - ${e.message}")
            }
        }

        if (errors.isNotEmpty()) {
            val errorMessage = "ServiceConfigLoader validation failed:\n" + errors.joinToString("\n")
            throw ConfigException(errorMessage)
        }

        log.info("✓ All ServiceConfigLoader implementations validated successfully")
    }

    /**
     * Get metadata about a ServiceConfigLoader implementation.
     *
     * **Returns:**
     * - Loader class name
     * - Loader package
     * - Configuration type being loaded
     *
     * @param loader ServiceConfigLoader to inspect
     * @return Metadata about the loader
     */
    fun getLoaderMetadata(loader: ServiceConfigLoader<*>): LoaderMetadata {
        val loaderClass = loader::class.java
        return LoaderMetadata(
            className = loaderClass.simpleName,
            packageName = loaderClass.packageName,
            loadedType = "Unknown"  // Type information is erased at runtime
        )
    }

    /**
     * Metadata about a ServiceConfigLoader.
     */
    data class LoaderMetadata(
        val className: String,
        val packageName: String,
        val loadedType: String
    )
}
