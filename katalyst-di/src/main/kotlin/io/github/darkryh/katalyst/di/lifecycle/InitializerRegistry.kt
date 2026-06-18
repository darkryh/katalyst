package io.github.darkryh.katalyst.di.lifecycle

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.core.di.get
import io.github.darkryh.katalyst.core.di.getAll
import org.slf4j.LoggerFactory

/**
 * Registry for managing application initialization lifecycle.
 *
 * **Ownership**: Orchestrates the complete application initialization flow.
 *
 * **Behavior**:
 * 1. Includes built-in initializers (StartupValidator)
 * 2. Discovers feature-provided pre-start initializers from the active container
 * 3. Sorts all by order (lower numbers execute first)
 * 4. Executes in sequence with error handling
 *
 * **Standard Execution Order:**
 * 1. StartupValidator (order=-100) - Validate DB is ready
 * 2. Custom pre-start initializers (order=0+) - user-defined pre-server logic
 *
 * **Dynamic Discovery**:
 * Feature modules register their ApplicationInitializer implementations in bean modules.
 * InitializerRegistry automatically discovers these from the active container and includes them in execution order.
 */
internal class InitializerRegistry(private val container: KatalystContainer) {
    private val logger = LoggerFactory.getLogger("InitializerRegistry")

    /**
     * Discovers and executes all ApplicationInitializers in order.
     *
     * **Discovery Strategy**:
     * - Always includes built-in initializers (StartupValidator)
     * - Queries Koin for all registered ApplicationInitializer implementations
     * - Sorts by order field (ascending)
     * - Executes sequentially with fail-fast error handling
     */
    suspend fun invokeAll() {
        // PHASE 5: Application Initialization Hooks
        BootstrapProgress.startLifecycle(BootstrapLifecycle.PRE_START_INITIALIZERS)
        try {
            logger.info("Application initialization lifecycle starting")

            // Built-in initializers (always present)
            val builtInInitializers = mutableListOf<ApplicationInitializer>(
                StartupValidator(container.get())
            )

            // Discover feature-provided initializers from the container.
            val containerInitializers = runCatching {
                container.getAll<ApplicationInitializer>()
            }.getOrElse { emptyList() }
            val registryInitializers = ApplicationInitializerRegistry.getAll()
            val discoveredInitializers = (registryInitializers + containerInitializers)
                .distinctBy { it::class }

            logger.info(
                "Discovered {} ApplicationInitializer(s) (registry={}, container={})",
                discoveredInitializers.size,
                registryInitializers.size,
                containerInitializers.size
            )

            // Combine all initializers
            val initializers = (builtInInitializers + discoveredInitializers).toMutableList()

            initializers.sortWith(initializerOrderComparator)

            logger.info("Pre-start initializers execution plan: {} initializer(s)", initializers.size)

            // Execute each initializer with fail-fast error handling
            initializers.forEach { initializer ->
                val startTime = System.currentTimeMillis()

                logger.info("⏱  Starting: {}", initializer.initializerId)

                runCatching {
                    initializer.onApplicationReady()
                }.onFailure { e ->
                    val duration = System.currentTimeMillis() - startTime

                    logger.error("✗ Initializer failed: {} ({} ms)", initializer.initializerId, duration)
                    logger.error("  Reason: {}", e.message ?: "Unknown error")

                    // Wrap in InitializerFailedException for better error context
                    val initException = // Already a lifecycle exception, re-throw as-is
                        e as? LifecycleException
                            ?: InitializerFailedException(
                                initializerName = initializer.initializerId,
                                message = e.message ?: "Unknown error",
                                cause = e
                            )
                    throw initException
                }

                val duration = System.currentTimeMillis() - startTime
                logger.info("✓  Completed: {} ({} ms)", initializer.initializerId, duration)
            }
            BootstrapProgress.completeLifecycle(
                BootstrapLifecycle.PRE_START_INITIALIZERS,
                "All application initialization hooks completed"
            )

        } catch (e: Exception) {
            logger.error("Fatal error during initialization", e)
            BootstrapProgress.failLifecycle(BootstrapLifecycle.PRE_START_INITIALIZERS, e)
            throw e
        }
    }
}

internal val initializerOrderComparator: Comparator<ApplicationInitializer> =
    compareBy<ApplicationInitializer> { it.order }
        .thenBy { it::class.qualifiedName ?: it::class.simpleName ?: "" }
