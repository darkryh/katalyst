package io.github.darkryh.katalyst.scanner.integration

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.scanner.core.DiscoveryRegistry
import io.github.darkryh.katalyst.scanner.scanner.InMemoryDiscoveryRegistry
import org.slf4j.LoggerFactory

/**
 * DiscoveryRegistry implementation that resolves instances from a Katalyst container.
 *
 * This registry:
 * - Stores discovered types in an in-memory registry
 * - Retrieves instances from a Katalyst container when requested
 * - Bridges discovery with dependency injection
 * - Provides typed access to discovered services
 *
 * **Usage:**
 * ```kotlin
 * val container = KatalystContainerProvider.current()
 * val registry = ContainerDiscoveryRegistry<Service>(
 *     baseType = Service::class.java,
 *     container = container
 * )
 *
 * registry.register(EmailService::class.java)
 * registry.registerAll(discoveredServices)
 *
 * val instance = registry.getInstance(EmailService::class.java)
 * val all = registry.getAllInstances()
 * ```
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 * @param container The Katalyst container to use for resolving dependencies
 */
class ContainerDiscoveryRegistry<T : Any>(
    private val baseType: Class<T>,
    private val container: KatalystContainer
) : DiscoveryRegistry<T> {

    private val inMemoryRegistry = InMemoryDiscoveryRegistry(baseType)
    private val logger = LoggerFactory.getLogger(ContainerDiscoveryRegistry::class.java)

    override fun register(type: Class<out T>) {
        inMemoryRegistry.register(type)
    }

    override fun registerAll(types: Set<Class<out T>>) {
        inMemoryRegistry.registerAll(types)
    }

    override fun getAll(): Set<Class<out T>> {
        return inMemoryRegistry.getAll()
    }

    override fun getByName(name: String): Class<out T>? {
        return inMemoryRegistry.getByName(name)
    }

    override fun isRegistered(type: Class<out T>): Boolean {
        return inMemoryRegistry.isRegistered(type)
    }

    override fun size(): Int {
        return inMemoryRegistry.size()
    }

    override fun clear() {
        inMemoryRegistry.clear()
    }

    /**
     * Retrieves an instance of a discovered type from the container.
     *
     * **Important:** The discovered type MUST be registered in the active container.
     *
     * @param type The class to retrieve an instance for
     * @return The instance from the container
     * @throws IllegalArgumentException if the type is not registered in the container
     */
    fun getInstance(type: Class<out T>): T {
        return try {
            container.get(type.kotlin)
        } catch (e: Exception) {
            logger.error("Failed to get instance from container for {}", type.simpleName, e)
            throw IllegalArgumentException(
                "Instance of ${type.simpleName} was not found in the Katalyst container.",
                e
            )
        }
    }

    /**
     * Retrieves all instances of discovered types from the container.
     *
     * @return List of all discovered service instances from the container
     */
    fun getAllInstances(): List<T> {
        val instances = mutableListOf<T>()

        for (type in getAll()) {
            try {
                val instance = getInstance(type)
                instances.add(instance)
                logger.debug("  ✓ Retrieved instance: {}", type.simpleName)
            } catch (e: Exception) {
                logger.warn("  ⚠ Could not retrieve instance for {}: {}", type.simpleName, e.message)
            }
        }

        return instances
    }

    /**
     * Safely tries to get an instance, returning null if not found.
     *
     * @param type The class to retrieve an instance for
     * @return The instance if found, null otherwise
     */
    fun getInstanceOrNull(type: Class<out T>): T? {
        return try {
            getInstance(type)
        } catch (e: Exception) {
            logger.debug("Instance not found for {}: {}", type.simpleName, e.message)
            null
        }
    }

    override fun toString(): String {
        return "ContainerDiscoveryRegistry(baseType=${baseType.simpleName}, size=${size()})"
    }
}
