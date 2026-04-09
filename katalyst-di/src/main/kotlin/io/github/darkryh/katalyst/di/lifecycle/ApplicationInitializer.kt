package io.github.darkryh.katalyst.di.lifecycle

/**
 * Lifecycle hook interface for application initialization phases.
 *
 * Implementations are discovered and executed automatically during
 * DI bootstrap before server bind, after services are instantiated and
 * database schema is initialized.
 *
 * **Execution Order:**
 * 1. StartupValidator (order=-100) - Validates DB readiness
 * 2. User-defined initializers (order=0+) - Custom pre-start validation/setup logic
 *
 * **Component Discovery:**
 * Runtime activations (scheduler, background consumers) should use
 * [ApplicationReadyInitializer], not this interface.
 */
interface ApplicationInitializer {
    /**
     * Unique identifier for this initializer.
     * Used for logging and debugging.
     */
    val initializerId: String
        get() = this::class.simpleName ?: "ApplicationInitializer"

    /**
     * Execution order relative to other initializers.
     * Lower numbers execute first.
     *
     * Standard values:
     * - StartupValidator: -100 (always first)
     * - Custom pre-start initializers: 0+ (default, in any order)
     */
    val order: Int
        get() = 0

    /**
     * Invoked when this initializer's turn comes during startup.
     *
     * At this point:
     * - All services, repositories, components instantiated ✓
     * - All database tables discovered and schema initialized ✓
     * - Transaction adapters registered ✓
     * - Koin DI fully configured ✓
     * - Ktor server NOT started yet
     *
     */
    suspend fun onApplicationReady()
}
