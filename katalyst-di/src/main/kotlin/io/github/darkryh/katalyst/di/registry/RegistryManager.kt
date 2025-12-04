package io.github.darkryh.katalyst.di.registry

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

private val logger = LoggerFactory.getLogger("RegistryManager")

/**
 * Central manager for all [ResettableRegistry] instances in the Katalyst DI system.
 *
 * This singleton provides a single point of control for resetting all registries,
 * which is essential for test isolation and cleanup.
 *
 * **Automatic Registration:**
 * Registries automatically register themselves with the manager in their `init` block:
 * ```kotlin
 * object MyRegistry : ResettableRegistry {
 *     init { RegistryManager.register(this) }
 *     // ...
 * }
 * ```
 *
 * **Usage in Tests:**
 * ```kotlin
 * class MyServiceTest {
 *     @BeforeEach
 *     fun setup() {
 *         RegistryManager.resetAll()
 *     }
 *
 *     @Test
 *     fun `test something`() {
 *         // Test runs with clean registry state
 *     }
 * }
 * ```
 *
 * **Thread Safety:**
 * All methods are thread-safe. The manager uses [CopyOnWriteArrayList] internally
 * to handle concurrent registration and reset operations.
 *
 * @see ResettableRegistry The interface implemented by all managed registries
 */
object RegistryManager : ResettableRegistry {
    private val registries = CopyOnWriteArrayList<ResettableRegistry>()

    /**
     * Registers a [ResettableRegistry] instance with this manager.
     *
     * Typically called from the registry's `init` block to ensure automatic registration.
     * Duplicate registrations are prevented (same instance won't be added twice).
     *
     * @param registry The registry to manage
     */
    fun register(registry: ResettableRegistry) {
        if (registry === this) {
            // Prevent self-registration (RegistryManager implements ResettableRegistry)
            return
        }
        if (registries.addIfAbsent(registry)) {
            logger.debug("Registered {} with RegistryManager", registry::class.simpleName)
        }
    }

    /**
     * Resets all registered registries to their initial state.
     *
     * This method iterates through all registered registries and calls their `reset()` method.
     * Use this in test setup to ensure clean state between test cases.
     *
     * **Note:** This does NOT remove registries from management; it only resets their contents.
     */
    fun resetAll() {
        if (registries.isEmpty()) {
            logger.debug("No registries to reset")
            return
        }

        logger.debug("Resetting {} registries", registries.size)
        registries.forEach { registry ->
            try {
                registry.reset()
                logger.trace("Reset {}", registry::class.simpleName)
            } catch (e: Exception) {
                logger.warn("Failed to reset {}: {}", registry::class.simpleName, e.message)
            }
        }
        logger.debug("All registries reset")
    }

    /**
     * Returns the number of registries currently managed.
     *
     * Useful for debugging and testing.
     */
    fun registryCount(): Int = registries.size

    /**
     * Resets the RegistryManager itself (clears tracked registries).
     *
     * **Warning:** This removes all registries from management. Use with caution.
     * Primarily intended for testing the RegistryManager itself.
     */
    override fun reset() {
        registries.clear()
        logger.debug("RegistryManager cleared all tracked registries")
    }
}
