package io.github.darkryh.katalyst.scanner.integration

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.scanner.core.TypeDiscovery
import org.slf4j.LoggerFactory

/**
 * Generic auto-discovery engine that bridges scanning and dependency injection.
 *
 * This engine:
 * 1. Discovers types using a TypeDiscovery implementation
 * 2. Retrieves instances from a Katalyst container
 * 3. Returns fully instantiated and injected instances
 *
 * **Workflow:**
 * ```
 * Scan Classpath
 *    ↓
 * Find Matching Classes
 *    ↓
 * Resolve from container
 *    ↓
 * Return Instances
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val container = KatalystContainerProvider.current()
 * val discovery: TypeDiscovery<Service> = ReflectionsTypeScanner(...)
 *
 * val engine = AutoDiscoveryEngine(discovery, container)
 * val instances = engine.discoverAndInstantiate()
 * ```
 *
 * @param T The base type or marker interface
 * @param discovery The TypeDiscovery implementation to use for scanning
 * @param container The Katalyst container for resolving dependencies
 */
class AutoDiscoveryEngine<T : Any>(
    private val discovery: TypeDiscovery<T>,
    private val container: KatalystContainer
) {

    private val logger = LoggerFactory.getLogger(AutoDiscoveryEngine::class.java)

    /**
     * Discovers all types and returns them.
     *
     * @return Set of discovered class types
     */
    fun discover(): Set<Class<out T>> {
        return discovery.discover()
    }

    /**
     * Discovers all types and instantiates them from the configured container.
     *
     * **Important:** All discovered types MUST be registered in the container.
     *
     * @return List of instantiated service instances
     */
    fun discoverAndInstantiate(): List<T> {
        logger.info("🔍 Starting auto-discovery and instantiation...")

        val discoveredClasses = discovery.discover()
        logger.info("Found {} type(s)", discoveredClasses.size)

        val instances = mutableListOf<T>()

        discoveredClasses.forEach { serviceClass ->
            try {
                val instance = getInstance(serviceClass)
                instances.add(instance)
                logger.info("  ✅ {}", serviceClass.simpleName)
            } catch (e: Exception) {
                logger.warn("  ⚠️ {}: {}", serviceClass.simpleName, e.message)
            }
        }

        logger.info("✓ Successfully instantiated {} instance(s)", instances.size)

        return instances
    }

    /**
     * Discovers all types with metadata and instantiates them.
     *
     * Useful when you need additional information about each discovered type.
     *
     * @return Map of discovered class to its instantiated instance
     */
    fun discoverWithMetadataAndInstantiate(): Map<Class<out T>, T> {
        val discovered = discovery.discoverWithMetadata()
        val instances = mutableMapOf<Class<out T>, T>()

        discovered.forEach { (clazz, metadata) ->
            try {
                val instance = getInstance(clazz)
                instances[clazz] = instance
                logger.debug("Instantiated: {} (package: {})", metadata.simpleName, metadata.packageName)
            } catch (e: Exception) {
                logger.warn("Failed to instantiate {}: {}", metadata.simpleName, e.message)
            }
        }

        return instances
    }

    /**
     * Gets an instance of a type from the container.
     *
     * @param type The class to instantiate
     * @return The instance from the container
     * @throws IllegalArgumentException if the type is not registered in the container
     */
    private fun getInstance(type: Class<out T>): T {
        return try {
            container.get(type.kotlin)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Type ${type.simpleName} is not registered in the Katalyst container.",
                e
            )
        }
    }
}
