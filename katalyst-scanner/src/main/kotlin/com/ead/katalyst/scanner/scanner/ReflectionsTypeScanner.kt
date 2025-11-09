package com.ead.katalyst.scanner.scanner

import com.ead.katalyst.scanner.core.DiscoveryConfig
import com.ead.katalyst.scanner.core.DiscoveryMetadata
import com.ead.katalyst.scanner.core.DiscoveryPredicate
import com.ead.katalyst.scanner.core.TypeDiscovery
import org.reflections.Reflections
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.slf4j.LoggerFactory

/**
 * Generic implementation of TypeDiscovery using the Reflections library.
 *
 * This implementation:
 * - Scans the classpath for implementations of a base type
 * - Applies optional predicates to filter results
 * - Provides metadata about discovered types
 * - Handles errors gracefully with optional callbacks
 *
 * **Type Parameter T:**
 * - The base type or marker interface that discovered classes must implement/extend
 * - Must be a valid class or interface available on the classpath
 *
 * **Usage Examples:**
 * ```kotlin
 * // Basic usage: discover all Service implementations
 * val scanner = ReflectionsTypeScanner<Service>(
 *     baseType = Service::class.java,
 *     scanPackages = listOf("com.ead.xtory")
 * )
 * val services = scanner.discover()
 *
 * // With predicate to exclude test classes
 * val scanner = ReflectionsTypeScanner<Service>(
 *     baseType = Service::class.java,
 *     scanPackages = listOf("com.ead.xtory"),
 *     predicate = DiscoveryPredicate { clazz ->
 *         !clazz.simpleName.startsWith("Test")
 *     }
 * )
 *
 * // Using configuration builder
 * val config = DiscoveryConfig.builder<Service>()
 *     .scanPackages("com.ead.xtory")
 *     .predicate(DiscoveryPredicate { !it.simpleName.startsWith("Mock") })
 *     .onDiscover { clazz -> println("Found: ${clazz.simpleName}") }
 *     .build()
 * val scanner = ReflectionsTypeScanner(Service::class.java, config)
 * ```
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 * @param config Configuration for discovery (packages, predicates, callbacks)
 */
class ReflectionsTypeScanner<T>(
    private val baseType: Class<T>,
    private val config: DiscoveryConfig<T> = DiscoveryConfig()
) : TypeDiscovery<T> {

    constructor(
        baseType: Class<T>,
        scanPackages: List<String> = emptyList(),
        predicate: DiscoveryPredicate<T>? = null
    ) : this(
        baseType,
        DiscoveryConfig(
            scanPackages = scanPackages,
            predicate = predicate
        )
    )

    private val logger = LoggerFactory.getLogger(ReflectionsTypeScanner::class.java)

    override fun discover(): Set<Class<out T>> {
        return try {
            val startTime = System.currentTimeMillis()
            val scanMessage = if (config.scanPackages.isEmpty()) {
                "entire classpath"
            } else {
                config.scanPackages.joinToString(", ")
            }
            logger.debug("🔍 Scanning {} for {} implementations", scanMessage, baseType.simpleName)

            // Configure reflections based on scan packages
            val reflections = if (config.scanPackages.isEmpty()) {
                logger.debug("No packages specified - scanning entire classpath")
                Reflections(
                    ConfigurationBuilder()
                        .setUrls(ClasspathHelper.forClassLoader())
                )
            } else {
                logger.debug("Scanning specified packages: {}", config.scanPackages.joinToString(", "))
                Reflections(*config.scanPackages.toTypedArray())
            }

            // Get all subtypes of the base type
            var implementations = reflections.getSubTypesOf(baseType)

            logger.info(
                "✓ Discovered {} {} implementation(s) in {} ms",
                implementations.size,
                baseType.simpleName,
                System.currentTimeMillis() - startTime
            )

            // Apply predicate if provided
            config.predicate?.let { predicate ->
                logger.debug("Applying predicate filter...")
                implementations = implementations.filter { clazz ->
                    predicate.matches(clazz)
                }.toSet()
                logger.debug("After predicate: {} implementation(s)", implementations.size)
            }

            // Log each found implementation
            implementations.forEach { impl ->
                logger.debug("  └─ Found: {}", impl.simpleName)
                config.onDiscover(impl)
            }

            if (implementations.isEmpty()) {
                logger.warn("No {} implementations matched the discovery criteria", baseType.simpleName)
            }

            implementations
        } catch (e: Exception) {
            logger.error("❌ Error scanning for {} implementations", baseType.simpleName, e)
            config.onError(e)
            emptySet()
        }
    }

    override fun discoverWithMetadata(): Map<Class<out T>, DiscoveryMetadata> {
        val discovered = discover()
        return discovered.associateWith { clazz ->
            DiscoveryMetadata.from(clazz)
        }
    }

    /**
     * Discovers all types with enhanced metadata including methods and type parameters.
     *
     * This method:
     * - Discovers all matching types
     * - Scans methods in each class (optional)
     * - Extracts generic type parameters (optional)
     * - Returns comprehensive metadata
     *
     * **Usage:**
     * ```kotlin
     * val scanner = ReflectionsTypeScanner<Repository<*, *>>(
     *     baseType = Repository::class.java,
     *     scanPackages = listOf("com.ead.repositories")
     * )
     *
     * val metadataMap = scanner.discoverWithEnhancedMetadata(
     *     baseType = Repository::class.java,  // To extract E and D types
     *     scanMethods = true,
     *     methodFilter = { metadata -> metadata.hasMethods() }
     * )
     * ```
     *
     * @param baseType The base type to extract generic parameters from (optional)
     * @param scanMethods Whether to scan and include methods in metadata (default: false)
     * @param methodFilter Optional filter for which methods to include
     * @return Map of discovered classes to their enhanced metadata
     */
    fun discoverWithEnhancedMetadata(
        baseType: Class<*>? = null,
        scanMethods: Boolean = false,
        methodFilter: (DiscoveryMetadata) -> Boolean = { true }
    ): Map<Class<out T>, DiscoveryMetadata> {
        val discovered = discover()
        val methodScanner = if (scanMethods) KotlinMethodScanner<T>() else null

        return discovered.associateWith { clazz ->
            val methods = methodScanner?.discoverMethodsInClass(clazz) ?: emptyList()

            DiscoveryMetadata.from(
                clazz = clazz,
                methods = methods,
                baseType = baseType
            )
        }.filterValues { metadata ->
            methodFilter(metadata)
        }
    }

    /**
     * Discovers methods in all discovered classes.
     *
     * **Usage:**
     * ```kotlin
     * val scanner = ReflectionsTypeScanner<RouteController>(...)
     * val classes = scanner.discover()
     * val routeMethods = scanner.discoverMethods(classes)
     * ```
     *
     * @param classes The classes to scan methods from
     * @param methodFilter Optional filter for methods
     * @return List of discovered method metadata
     */
    fun discoverMethods(
        classes: Set<Class<out T>>,
        methodFilter: (DiscoveryMetadata) -> Boolean = { true }
    ): List<DiscoveryMetadata> {
        val methodScanner = KotlinMethodScanner<T>()
        return classes.mapNotNull { clazz ->
            val methods = methodScanner.discoverMethodsInClass(clazz)
            if (methods.isNotEmpty()) {
                val metadata = DiscoveryMetadata.from(clazz, methods)
                metadata.takeIf { methodFilter(it) }
            } else {
                null
            }
        }
    }
}
