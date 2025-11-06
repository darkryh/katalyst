package com.ead.katalyst.scanner.integration

import com.ead.katalyst.scanner.core.DiscoveryRegistry
import com.ead.katalyst.scanner.scanner.InMemoryDiscoveryRegistry
import org.koin.core.Koin
import org.slf4j.LoggerFactory

/**
 * DiscoveryRegistry implementation that integrates with Koin DI container.
 *
 * This registry:
 * - Stores discovered types in an in-memory registry
 * - Retrieves instances from Koin container when requested
 * - Bridges discovery with dependency injection
 * - Provides typed access to discovered services
 *
 * **Usage:**
 * ```kotlin
 * val koin = getKoin()
 * val registry = KoinDiscoveryRegistry<Service>(
 *     baseType = Service::class.java,
 *     koin = koin
 * )
 *
 * registry.register(EmailService::class.java)
 * registry.registerAll(discoveredServices)
 *
 * // Get instances from Koin
 * val instance = registry.getInstanceFromKoin(EmailService::class.java)
 * val all = registry.getAllInstances()
 * ```
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 * @param koin The Koin instance to use for resolving dependencies
 */
class KoinDiscoveryRegistry<T>(
    private val baseType: Class<T>,
    private val koin: Koin
) : DiscoveryRegistry<T> {

    private val inMemoryRegistry = InMemoryDiscoveryRegistry(baseType)
    private val logger = LoggerFactory.getLogger(KoinDiscoveryRegistry::class.java)

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
     * Retrieves an instance of a discovered type from Koin.
     *
     * **Important:** The discovered type MUST be registered in Koin container.
     *
     * **Example:**
     * ```kotlin
     * // In your Koin module:
     * single<Service> { EmailService(...) }  // or
     * single { EmailService(...) }
     *
     * // Then get the instance:
     * val instance = registry.getInstanceFromKoin(EmailService::class.java)
     * ```
     *
     * @param type The class to retrieve an instance for
     * @return The instance from Koin
     * @throws IllegalArgumentException if the type is not registered in Koin
     */
    fun getInstanceFromKoin(type: Class<out T>): T {
        return try {
            @Suppress("UNCHECKED_CAST")
            koin.get(type.kotlin) as T
        } catch (e: Exception) {
            logger.error("Failed to get instance from Koin for {}", type.simpleName, e)
            throw IllegalArgumentException(
                "Instance of ${type.simpleName} not found in Koin container. " +
                    "Ensure it's registered in a module: single { ${type.simpleName}(...) }",
                e
            )
        }
    }

    /**
     * Retrieves all instances of discovered types from Koin.
     *
     * @return List of all discovered service instances from Koin
     */
    fun getAllInstances(): List<T> {
        val instances = mutableListOf<T>()

        for (type in getAll()) {
            try {
                val instance = getInstanceFromKoin(type)
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
    fun getInstanceFromKoinOrNull(type: Class<out T>): T? {
        return try {
            getInstanceFromKoin(type)
        } catch (e: Exception) {
            logger.debug("Instance not found for {}: {}", type.simpleName, e.message)
            null
        }
    }

    override fun toString(): String {
        return "KoinDiscoveryRegistry(baseType=${baseType.simpleName}, size=${size()})"
    }
}
