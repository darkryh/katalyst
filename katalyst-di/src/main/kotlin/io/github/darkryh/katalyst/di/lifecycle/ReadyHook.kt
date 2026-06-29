package io.github.darkryh.katalyst.di.lifecycle

/**
 * Lifecycle hook that runs AFTER the server is serving.
 *
 * In Ktor applications this hook runs after `ApplicationStarted`.
 * Implementations should be used for background activations such as scheduler
 * jobs, consumers, and polling loops.
 */
interface ReadyHook {
    /**
     * Stable identifier for logs and diagnostics.
     */
    val id: String
        get() = this::class.simpleName ?: "ReadyHook"

    /**
     * Ordering among ready hooks.
     * Lower values execute first.
     */
    val order: Int
        get() = 0

    /**
     * Invoked when runtime is ready for traffic.
     */
    suspend fun onReady()
}
