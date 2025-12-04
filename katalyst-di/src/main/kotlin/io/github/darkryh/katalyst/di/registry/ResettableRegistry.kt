package io.github.darkryh.katalyst.di.registry

/**
 * Interface for registries that can be reset to their initial state.
 *
 * This interface provides a common contract for all singleton registries in the Katalyst DI system,
 * enabling consistent test isolation and cleanup.
 *
 * **Why This Is Needed:**
 * Singleton registries (like [KtorModuleRegistry], [ServiceRegistry], etc.) maintain state
 * across the application lifecycle. In tests, this state can leak between test cases,
 * causing flaky tests and hard-to-debug failures.
 *
 * **Usage in Tests:**
 * ```kotlin
 * @BeforeEach
 * fun setup() {
 *     RegistryManager.resetAll()  // Reset all registries before each test
 * }
 * ```
 *
 * **Implementation Requirements:**
 * - `reset()` must clear all internal state
 * - `reset()` must be idempotent (safe to call multiple times)
 * - `reset()` should be thread-safe
 * - After `reset()`, the registry should behave as if freshly initialized
 *
 * @see RegistryManager For centralized registry reset management
 */
interface ResettableRegistry {
    /**
     * Resets this registry to its initial empty state.
     *
     * After calling this method:
     * - All registered items are removed
     * - Any internal flags (like "installed" state) are reset
     * - The registry is ready to accept new registrations
     *
     * This method is thread-safe and idempotent.
     */
    fun reset()
}
