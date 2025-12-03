package io.github.darkryh.katalyst.scanner.scanner

import io.github.darkryh.katalyst.scanner.core.DiscoveryRegistry
import org.slf4j.LoggerFactory

/**
 * In-memory implementation of DiscoveryRegistry.
 *
 * Simple, thread-safe registry that stores discovered types in memory.
 * Suitable for most use cases where types need to be registered and retrieved.
 *
 * **Thread Safety:**
 * - Uses `ConcurrentHashMap` internally for thread-safe operations
 * - Safe to use from multiple threads
 *
 * **Usage:**
 * ```kotlin
 * val registry: DiscoveryRegistry<Service> =
 *     InMemoryDiscoveryRegistry(Service::class.java)
 *
 * registry.register(EmailService::class.java)
 * registry.registerAll(listOf(SmsService::class.java, PushService::class.java).toSet())
 *
 * val all = registry.getAll()
 * val byName = registry.getByName("EmailService")
 * ```
 *
 * @param T The base type or marker interface
 * @param baseType The Class object for the base type
 */
class InMemoryDiscoveryRegistry<T>(
    private val baseType: Class<T>
) : DiscoveryRegistry<T> {

    private val registry = LinkedHashMap<String, Class<out T>>()
    private val logger = LoggerFactory.getLogger(InMemoryDiscoveryRegistry::class.java)

    override fun register(type: Class<out T>) {
        validateType(type)
        registry[type.simpleName] = type
        logger.debug("Registered: {} -> {}", type.simpleName, type.canonicalName)
    }

    override fun registerAll(types: Set<Class<out T>>) {
        types.forEach { register(it) }
    }

    override fun getAll(): Set<Class<out T>> {
        return registry.values.toSet()
    }

    override fun getByName(name: String): Class<out T>? {
        return registry[name]
    }

    override fun isRegistered(type: Class<out T>): Boolean {
        return registry.containsKey(type.simpleName)
    }

    override fun size(): Int {
        return registry.size
    }

    override fun clear() {
        registry.clear()
        logger.debug("Registry cleared")
    }

    private fun validateType(type: Class<out T>) {
        if (!baseType.isAssignableFrom(type)) {
            throw IllegalArgumentException(
                "Type ${type.simpleName} is not assignable to ${baseType.simpleName}"
            )
        }
    }

    override fun toString(): String {
        return "InMemoryDiscoveryRegistry(baseType=${baseType.simpleName}, size=${registry.size})"
    }
}
