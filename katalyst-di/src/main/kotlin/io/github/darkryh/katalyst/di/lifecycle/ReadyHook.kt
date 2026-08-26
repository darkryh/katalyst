package io.github.darkryh.katalyst.di.lifecycle

/**
 * Lifecycle hook that runs AFTER the server is serving.
 *
 * In Ktor applications this hook runs after `ApplicationStarted`.
 * Implementations should be used for background activations such as scheduler
 * jobs, consumers, and polling loops.
 *
 * Whatever this hook starts should be stopped from the matching [ShutdownHook], which Katalyst
 * awaits while the pool and the container are still alive. `id` and `order` come from
 * [LifecycleHook], so one class can implement both without restating either.
 *
 * **Discovery:**
 * Implementing this interface is sufficient. A hook is scanned, dependency-validated,
 * and constructor-injected on its own — it does NOT need to also implement
 * `Component` or `Service`.
 */
interface ReadyHook : LifecycleHook {
    /**
     * Invoked when runtime is ready for traffic.
     */
    suspend fun onReady()
}
