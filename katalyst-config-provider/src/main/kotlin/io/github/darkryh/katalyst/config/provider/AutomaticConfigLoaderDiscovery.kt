package io.github.darkryh.katalyst.config.provider

import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

private val logger = LoggerFactory.getLogger("AutomaticConfigLoaderDiscovery")

/**
 * Discovers [AutomaticServiceConfigLoader] implementations in the classpath during component scanning.
 *
 * This object is responsible for:
 * 1. Scanning packages for classes implementing [AutomaticServiceConfigLoader]
 * 2. Instantiating each discovered loader
 * 3. Mapping each loader to its configuration type
 * 4. Returning the discovered loaders for registration during DI initialization
 *
 * **Discovery Strategy:**
 * - Uses bytecode scanning (Reflections library) to find all subtypes of [AutomaticServiceConfigLoader]
 * - Handles both `object` declarations (singletons) and regular class instances
 * - Logs all discovered loaders for visibility
 *
 * **Usage:**
 * ```kotlin
 * val loaders = AutomaticConfigLoaderDiscovery.discoverLoaders(scanPackages)
 * loaders.forEach { (configType, loader) ->
 *     val config = loader.loadConfig(configProvider)
 *     loader.validate(config)
 *     // Register config in Koin...
 * }
 * ```
 *
 * @see AutomaticServiceConfigLoader The interface being discovered
 */
object AutomaticConfigLoaderDiscovery {
    /**
     * Discover all [AutomaticServiceConfigLoader] implementations in the specified packages.
     *
     * This method scans the classpath for all classes/objects that implement the
     * [AutomaticServiceConfigLoader] interface and returns them as a map from configuration type
     * to loader instance.
     *
     * **Discovery Details:**
     * - Uses Reflections library for bytecode scanning
     * - Handles Kotlin `object` declarations (singleton pattern)
     * - Handles regular class instances (uses no-arg constructor or Kotlin reflection)
     * - Filters to only packages specified in [scanPackages]
     * - Returns empty map if no loaders found or if scanPackages is empty
     *
     * **Instance Creation:**
     * - For Kotlin `object` declarations: Retrieves the singleton INSTANCE
     * - For regular classes: Calls no-arg constructor via Kotlin reflection
     * - Fails with exception if instance cannot be created
     *
     * **Error Handling:**
     * - Non-fatal: Logs warning if individual loader cannot be instantiated (continues)
     * - Fatal: Throws exception during scanning if packages are invalid
     *
     * **Example:**
     * ```kotlin
     * val loaders = discoverLoaders(arrayOf("com.example.config"))
     * // Returns:
     * // SmtpConfig -> SmtpConfigLoader (instance)
     * // DatabaseConfig -> DatabaseConfigLoader (instance)
     * ```
     *
     * @param scanPackages Array of package names to scan (e.g., ["com.example.app", "com.example.config"])
     * @return Map from configuration type (KClass) to loader instance
     *         Empty map if no loaders found or scanPackages is empty
     * @throws Exception if classpath scanning fails
     */
    fun discoverLoaders(scanPackages: Array<String>): Map<KClass<*>, AutomaticServiceConfigLoader<*>> {
        if (scanPackages.isEmpty()) {
            logger.debug("No packages configured for scanning, skipping automatic config loader discovery")
            return emptyMap()
        }

        return try {
            val results = mutableMapOf<KClass<*>, AutomaticServiceConfigLoader<*>>()

            val loaderType = AutomaticServiceConfigLoader::class.java

            logger.debug("Scanning {} packages for AutomaticServiceConfigLoader implementations...",
                scanPackages.joinToString(", "))

            // Use Reflections library directly (same as AutoBindingRegistrar)
            val urls = scanPackages.flatMap { pkg -> ClasspathHelper.forPackage(pkg) }
            if (urls.isEmpty()) {
                logger.debug("No classpath URLs found for packages, skipping loader discovery")
                return results
            }

            val filter = scanPackages.fold(FilterBuilder()) { builder, pkg ->
                builder.includePackage(pkg)
            }

            val config = ConfigurationBuilder()
                .setUrls(urls)
                .filterInputsBy(filter)
                .setScanners(Scanners.SubTypes)

            val reflections = Reflections(config)
            val loaderClasses = reflections.getSubTypesOf(loaderType)
            logger.debug("Found {} AutomaticServiceConfigLoader candidate(s)", loaderClasses.size)

            loaderClasses.forEach { loaderClass ->
                try {
                    val instance = instantiateLoader(loaderClass)
                    val configType = instance.configType
                    results[configType] = instance

                    logger.debug("Discovered AutomaticServiceConfigLoader: {} -> {}",
                        loaderClass.simpleName, configType.simpleName)
                } catch (e: Exception) {
                    logger.warn("Could not instantiate AutomaticServiceConfigLoader {}: {}",
                        loaderClass.simpleName, e.message)
                    logger.debug("Full error instantiating {}", loaderClass.simpleName, e)
                }
            }

            if (results.isNotEmpty()) {
                logger.info("Discovered {} automatic config loader(s)", results.size)
                results.forEach { (configType, loader) ->
                    logger.debug("  - {} (via {})", configType.simpleName, loader::class.simpleName)
                }
            } else {
                logger.debug("No automatic config loaders discovered")
            }

            results
        } catch (e: Exception) {
            logger.error("Error during automatic config loader discovery: {}", e.message)
            logger.debug("Full error during loader discovery", e)
            throw e
        }
    }

    /**
     * Instantiate a loader class, handling both Kotlin `object` singletons and regular classes.
     *
     * **Strategy:**
     * 1. First tries to get the singleton INSTANCE field (for Kotlin `object` declarations)
     * 2. If that fails, tries to instantiate using Kotlin reflection (for regular classes)
     * 3. If both fail, throws exception with helpful error message
     *
     * **Kotlin `object` Example:**
     * ```kotlin
     * object SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> { ... }
     * ```
     * When compiled, Kotlin creates a class with a static `INSTANCE` field containing the singleton.
     *
     * **Regular Class Example:**
     * ```kotlin
     * class SmtpConfigLoader : AutomaticServiceConfigLoader<SmtpConfig> {
     *     constructor()  // No-arg constructor required
     * }
     * ```
     *
     * @param loaderClass The loader class to instantiate
     * @return Instantiated loader object
     * @throws Exception if loader cannot be instantiated
     */
    private fun instantiateLoader(loaderClass: Class<*>): AutomaticServiceConfigLoader<*> {
        // Try to get Kotlin object singleton first
        return try {
            val instanceField = loaderClass.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.get(null) as AutomaticServiceConfigLoader<*>
        } catch (e: NoSuchFieldException) {
            // Not a Kotlin object, try regular instantiation
            try {
                val kotlinClass = loaderClass.kotlin
                kotlinClass.createInstance() as AutomaticServiceConfigLoader<*>
            } catch (e2: Exception) {
                throw IllegalStateException(
                    "Could not instantiate AutomaticServiceConfigLoader ${loaderClass.simpleName}: " +
                    "tried INSTANCE singleton field, then no-arg constructor. " +
                    "Ensure it's either a Kotlin object or has a no-arg constructor.",
                    e2
                )
            }
        }
    }

    /**
     * Get the configuration type that a specific loader produces.
     *
     * Convenience method to extract the [AutomaticServiceConfigLoader.configType] property.
     *
     * **Example:**
     * ```kotlin
     * val loader = SmtpConfigLoader
     * val configType = getConfigType(loader)  // Returns SmtpConfig::class
     * ```
     *
     * @param loader The loader instance
     * @return The configuration type (KClass) that this loader produces
     */
    fun getConfigType(loader: AutomaticServiceConfigLoader<*>): KClass<*> = loader.configType
}
