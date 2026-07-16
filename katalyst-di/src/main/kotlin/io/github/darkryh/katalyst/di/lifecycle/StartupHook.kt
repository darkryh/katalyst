package io.github.darkryh.katalyst.di.lifecycle

/**
 * Lifecycle hook that runs BEFORE the server binds.
 *
 * Implementations are discovered and executed automatically during
 * DI bootstrap before server bind, after services are instantiated and
 * database schema is initialized.
 *
 * **Discovery:**
 * Implementing this interface is sufficient. A hook is scanned, dependency-validated,
 * and constructor-injected on its own — it does NOT need to also implement
 * `Component` or `Service`:
 *
 * ```kotlin
 * class WarmCachesHook(private val catalog: CatalogService) : StartupHook {
 *     override suspend fun onStartup() = catalog.warm()
 * }
 * ```
 *
 * Hooks participate in the same dependency graph as components, so an unresolvable
 * constructor dependency fails the bootstrap with a validation error rather than
 * being skipped.
 *
 * **Execution Order:**
 * 1. StartupValidator (order=-100) - Validates DB readiness
 * 2. User-defined hooks (order=0+) - Custom pre-start validation/setup logic
 *
 * Runtime activations (scheduler, background consumers) should use
 * [ReadyHook], not this interface.
 */
interface StartupHook {
    /**
     * Unique identifier for this hook.
     * Used for logging and debugging.
     */
    val id: String
        get() = this::class.simpleName ?: "StartupHook"

    /**
     * Execution order relative to other hooks.
     * Lower numbers execute first.
     *
     * Standard values:
     * - StartupValidator: -100 (always first)
     * - Custom pre-start hooks: 0+ (default, in any order)
     */
    val order: Int
        get() = 0

    /**
     * Invoked when this hook's turn comes during startup.
     *
     * At this point:
     * - All services, repositories, components instantiated ✓
     * - All database tables discovered and schema initialized ✓
     * - Transaction adapters registered ✓
     * - Koin DI fully configured ✓
     * - Ktor server NOT started yet
     *
     */
    suspend fun onStartup()
}
