package com.ead.katalyst.di.lifecycle

import org.koin.core.Koin

/**
 * Lifecycle hook interface for application initialization phases.
 *
 * Implementations are discovered and executed automatically during
 * DI bootstrap AFTER all services are instantiated and database
 * schema is initialized.
 *
 * **Execution Order:**
 * 1. StartupValidator (order=-100) - Validates DB readiness
 * 2. SchedulerMethodInvoker (order=-50) - Discovers and invokes scheduler methods
 * 3. User-defined initializers (order=0+) - Custom post-init logic
 *
 * **Component Discovery:**
 * Users do NOT need to implement this interface to use schedulers.
 * The framework automatically discovers scheduler methods via reflection.
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
     * - SchedulerMethodInvoker: -50 (before custom initializers)
     * - Custom initializers: 0+ (default, in any order)
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
     *
     * @param koin Fully-configured Koin instance
     */
    suspend fun onApplicationReady(koin: Koin)
}
