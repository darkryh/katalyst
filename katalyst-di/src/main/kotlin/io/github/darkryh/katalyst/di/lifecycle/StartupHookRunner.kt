package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.get
import io.github.darkryh.katalyst.core.di.getAll
import org.slf4j.LoggerFactory

/**
 * Runner for managing the pre-start application initialization lifecycle.
 *
 * **Ownership**: Orchestrates the complete pre-start hook flow.
 *
 * **Behavior**:
 * 1. Includes built-in hooks (StartupValidator)
 * 2. Discovers feature-provided pre-start hooks from the active container
 * 3. Sorts all by order (lower numbers execute first)
 * 4. Executes in sequence with error handling
 *
 * **Standard Execution Order:**
 * 1. StartupValidator (order=-100) - Validate DB is ready
 * 2. Custom pre-start hooks (order=0+) - user-defined pre-server logic
 *
 * **Dynamic Discovery**:
 * Feature modules register their StartupHook implementations in bean modules.
 * StartupHookRunner automatically discovers these from the active container and includes them in execution order.
 */
internal class StartupHookRunner(private val container: KatalystContainer) {
    private val logger = LoggerFactory.getLogger("StartupHookRunner")

    /**
     * Discovers and executes all StartupHooks in order.
     *
     * **Discovery Strategy**:
     * - Always includes built-in hooks (StartupValidator)
     * - Queries Koin for all registered StartupHook implementations
     * - Sorts by order field (ascending)
     * - Executes sequentially with fail-fast error handling
     */
    suspend fun invokeAll() {
        // PHASE 5: Application Startup Hooks
        BootstrapProgress.startLifecycle(BootstrapLifecycle.PRE_START_INITIALIZERS)
        try {
            logger.info("Application startup hook lifecycle starting")

            // Built-in hooks (always present)
            val builtInHooks = mutableListOf<StartupHook>(
                StartupValidator(container.get())
            )

            // Discover feature-provided hooks from the container.
            val containerHooks = runCatching {
                container.getAll<StartupHook>()
            }.getOrElse { emptyList() }
            val registryHooks = StartupHookRegistry.getAll()
            val discoveredHooks = (registryHooks + containerHooks)
                .distinctBy { it::class }

            logger.info(
                "Discovered {} StartupHook(s) (registry={}, container={})",
                discoveredHooks.size,
                registryHooks.size,
                containerHooks.size
            )

            // Combine all hooks
            val hooks = (builtInHooks + discoveredHooks).toMutableList()

            hooks.sortWith(startupHookOrderComparator)

            logger.info("Pre-start hooks execution plan: {} hook(s)", hooks.size)

            // Execute each hook with fail-fast error handling
            hooks.forEach { hook ->
                val startTime = System.currentTimeMillis()

                logger.info("⏱  Starting: {}", hook.id)

                runCatching {
                    hook.onStartup()
                }.onFailure { e ->
                    val duration = System.currentTimeMillis() - startTime

                    logger.error("✗ Startup hook failed: {} ({} ms)", hook.id, duration)
                    logger.error("  Reason: {}", e.message ?: "Unknown error")

                    // Wrap in InitializerFailedException for better error context
                    val initException = // Already a lifecycle exception, re-throw as-is
                        e as? LifecycleException
                            ?: InitializerFailedException(
                                initializerName = hook.id,
                                message = e.message ?: "Unknown error",
                                cause = e
                            )
                    throw initException
                }

                val duration = System.currentTimeMillis() - startTime
                logger.info("✓  Completed: {} ({} ms)", hook.id, duration)
            }
            BootstrapProgress.completeLifecycle(
                BootstrapLifecycle.PRE_START_INITIALIZERS,
                "All application startup hooks completed"
            )

        } catch (e: Exception) {
            logger.error("Fatal error during startup hooks", e)
            BootstrapProgress.failLifecycle(BootstrapLifecycle.PRE_START_INITIALIZERS, e)
            throw e
        }
    }
}

internal val startupHookOrderComparator: Comparator<StartupHook> =
    compareBy<StartupHook> { it.order }
        .thenBy { it::class.qualifiedName ?: it::class.simpleName ?: "" }
