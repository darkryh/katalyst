package io.github.darkryh.katalyst.di.lifecycle

import org.koin.core.Koin
import org.slf4j.LoggerFactory
import io.github.darkryh.katalyst.di.lifecycle.BootstrapProgress

/**
 * Registry for managing application initialization lifecycle.
 *
 * **Ownership**: Orchestrates the complete application initialization flow.
 *
 * **Behavior**:
 * 1. Includes built-in initializers (StartupValidator)
 * 2. Discovers feature-provided pre-start initializers from Koin
 * 3. Sorts all by order (lower numbers execute first)
 * 4. Executes in sequence with error handling
 *
 * **Standard Execution Order:**
 * 1. StartupValidator (order=-100) - Validate DB is ready
 * 2. Custom pre-start initializers (order=0+) - user-defined pre-server logic
 *
 * **Dynamic Discovery**:
 * Feature modules (like scheduler) register their ApplicationInitializer implementations
 * in their Koin modules. InitializerRegistry automatically discovers these via
 * `koin.getAll<ApplicationInitializer>()` and includes them in execution order.
 */
internal class InitializerRegistry(private val koin: Koin) {
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
                StartupValidator(koin.get())
            )

            // Discover feature-provided initializers from Koin
            val koinInitializers = runCatching {
                koin.getAll<ApplicationInitializer>()
            }.getOrElse { emptyList() }
            val registryInitializers = ApplicationInitializerRegistry.getAll()
            val discoveredInitializers = (registryInitializers + koinInitializers)
                .distinctBy { it::class }

            logger.info(
                "Discovered {} ApplicationInitializer(s) (registry={}, koin={})",
                discoveredInitializers.size,
                registryInitializers.size,
                koinInitializers.size
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
