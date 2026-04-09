package io.github.darkryh.katalyst.di.lifecycle

/**
 * Lifecycle hook for work that must run only after runtime readiness is confirmed.
 *
 * In Ktor applications this hook runs after `ApplicationStarted`.
 * Implementations should be used for background activations such as scheduler
 * jobs, consumers, and polling loops.
 */
interface ApplicationReadyInitializer {
    /**
     * Stable identifier for logs and diagnostics.
     */
    val initializerId: String
        get() = this::class.simpleName ?: "ApplicationReadyInitializer"

    /**
     * Ordering among runtime-ready initializers.
     * Lower values execute first.
     */
    val order: Int
        get() = 0

    /**
     * Invoked when runtime is ready for traffic.
     */
    suspend fun onRuntimeReady()
}
