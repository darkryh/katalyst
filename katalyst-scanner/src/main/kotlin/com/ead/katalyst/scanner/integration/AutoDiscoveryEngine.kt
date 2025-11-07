package com.ead.katalyst.scanner.integration

import com.ead.katalyst.scanner.core.TypeDiscovery
import org.koin.core.Koin
import org.slf4j.LoggerFactory

/**
 * Generic auto-discovery engine that bridges scanning and dependency injection.
 *
 * This engine:
 * 1. Discovers types using a TypeDiscovery implementation
 * 2. Retrieves instances from Koin container
 * 3. Returns fully instantiated and injected instances
 *
 * **Workflow:**
 * ```
 * Scan Classpath
 *    ↓
 * Find Matching Classes
 *    ↓
 * Resolve from Koin
 *    ↓
 * Return Instances
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val koin = getKoin()
 * val discovery: TypeDiscovery<Service> = ReflectionsTypeScanner(...)
 *
 * val engine = AutoDiscoveryEngine(discovery, koin)
 * val instances = engine.discoverAndInstantiate()
 * ```
 *
 * @param T The base type or marker interface
 * @param discovery The TypeDiscovery implementation to use for scanning
 * @param koin The Koin instance for resolving dependencies
 */
class AutoDiscoveryEngine<T>(
    private val discovery: TypeDiscovery<T>,
    private val koin: Koin
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
     * Discovers all types and instantiates them from Koin.
     *
     * **Important:** All discovered types MUST be registered in the Koin container.
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
                val instance = getInstanceFromKoin(serviceClass)
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
                val instance = getInstanceFromKoin(clazz)
                instances[clazz] = instance
                logger.debug("Instantiated: {} (package: {})", metadata.simpleName, metadata.packageName)
            } catch (e: Exception) {
                logger.warn("Failed to instantiate {}: {}", metadata.simpleName, e.message)
            }
        }

        return instances
    }

    /**
     * Gets an instance of a type from Koin.
     *
     * @param type The class to instantiate
     * @return The instance from Koin
     * @throws IllegalArgumentException if the type is not registered in Koin
     */
    private fun getInstanceFromKoin(type: Class<out T>): T {
        return try {
            @Suppress("UNCHECKED_CAST")
            koin.get(type.kotlin) as T
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Type ${type.simpleName} not registered in Koin. " +
                    "Ensure it's registered in a Koin module.",
                e
            )
        }
    }
}
