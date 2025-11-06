package com.ead.katalyst.scanner.core

/**
 * Registry for storing and retrieving discovered types.
 *
 * Provides a centralized mechanism to:
 * - Register discovered types
 * - Query registered types
 * - Look up types by various criteria
 *
 * **Type Parameter T:**
 * - `T` is the base type or marker interface
 * - All registered types must be assignable to `T`
 *
 * **Usage Examples:**
 * ```kotlin
 * val registry: DiscoveryRegistry<Service> = ...
 *
 * // Register individual types
 * registry.register(EmailService::class.java)
 *
 * // Register multiple types at once
 * registry.registerAll(setOf(EmailService::class.java, SmsService::class.java))
 *
 * // Query types
 * val allTypes = registry.getAll()
 * val byName = registry.getByName("EmailService")
 * ```
 *
 * @param T The base type or marker interface
 */
interface DiscoveryRegistry<T> {
    /**
     * Registers a single type in the registry.
     *
     * @param type The class to register
     * @throws IllegalArgumentException if the type is not assignable to T
     */
    fun register(type: Class<out T>)

    /**
     * Registers multiple types at once.
     *
     * @param types Set of classes to register
     * @throws IllegalArgumentException if any type is not assignable to T
     */
    fun registerAll(types: Set<Class<out T>>)

    /**
     * Retrieves all registered types.
     *
     * @return Set of all registered types
     */
    fun getAll(): Set<Class<out T>>

    /**
     * Looks up a type by its simple class name.
     *
     * @param name The simple name of the class (e.g., "EmailService")
     * @return The class if found, null otherwise
     */
    fun getByName(name: String): Class<out T>?

    /**
     * Checks if a type is registered.
     *
     * @param type The class to check
     * @return true if registered, false otherwise
     */
    fun isRegistered(type: Class<out T>): Boolean

    /**
     * Gets the number of registered types.
     */
    fun size(): Int

    /**
     * Clears all registered types.
     */
    fun clear()
}
