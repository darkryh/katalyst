package com.ead.katalyst.di.lifecycle

import org.koin.core.Koin
import org.slf4j.LoggerFactory

/**
 * Registry for managing application initialization lifecycle.
 *
 * **Ownership**: Orchestrates the complete application initialization flow.
 *
 * **Behavior**:
 * 1. Includes built-in initializers (StartupValidator)
 * 2. Discovers feature-provided initializers from Koin (SchedulerInitializer, etc.)
 * 3. Sorts all by order (lower numbers execute first)
 * 4. Executes in sequence with error handling
 *
 * **Standard Execution Order:**
 * 1. StartupValidator (order=-100) - Validate DB is ready
 * 2. SchedulerInitializer (order=-50) - Register scheduler tasks [from scheduler module]
 * 3. Custom initializers (order=0+) - User-defined post-init logic
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
        try {
            logger.info("")
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ APPLICATION INITIALIZATION STARTING               ║")
            logger.info("║                                                    ║")
            logger.info("║ Phase 3: Component Discovery & Registration       ║")
            logger.info("║ Phase 4: Database Schema Initialization           ║")
            logger.info("║ Phase 5: Transaction Adapter Registration         ║")
            logger.info("║ Phase 6: Application Initialization Hooks         ║")
            logger.info("║                                                    ║")
            logger.info("╚════════════════════════════════════════════════════╝")
            logger.info("")

            // Built-in initializers (always present)
            val builtInInitializers = mutableListOf<ApplicationInitializer>(
                StartupValidator()
            )

            // Discover feature-provided initializers from Koin
            val discoveredInitializers = runCatching {
                koin.getAll<ApplicationInitializer>()
            }.getOrElse { emptyList() }

            logger.debug("Discovered {} ApplicationInitializer(s) from Koin", discoveredInitializers.size)
            discoveredInitializers.forEach { init ->
                logger.debug("  Found: {} (order={})", init.initializerId, init.order)
            }

            // Combine all initializers
            val initializers = (builtInInitializers + discoveredInitializers).toMutableList()

            // Sort by order (lower first)
            initializers.sortBy { it.order }

            logger.info("")
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ PHASE 6: INITIALIZATION HOOKS ({} total)          ║", initializers.size)
            logger.info("╚════════════════════════════════════════════════════╝")
            logger.info("")

            initializers.forEach { init ->
                logger.info("  [Order: {:>4d}] {}", init.order, init.initializerId)
            }
            logger.info("")

            // Execute each initializer with fail-fast error handling
            initializers.forEach { initializer ->
                val startTime = System.currentTimeMillis()

                logger.info("⏱  Starting: {}", initializer.initializerId)

                runCatching {
                    initializer.onApplicationReady(koin)
                }.onFailure { e ->
                    val duration = System.currentTimeMillis() - startTime

                    logger.error("")
                    logger.error("╔════════════════════════════════════════════════════╗")
                    logger.error("║ ✗ INITIALIZATION FAILED                           ║")
                    logger.error("║ Failed at: {} ({} ms)", initializer.initializerId, duration)
                    logger.error("║ Reason: {}", e.message)
                    logger.error("╚════════════════════════════════════════════════════╝")
                    logger.error("")

                    // Wrap in InitializerFailedException for better error context
                    val initException = if (e is LifecycleException) {
                        e  // Already a lifecycle exception, re-throw as-is
                    } else {
                        InitializerFailedException(
                            initializerName = initializer.initializerId,
                            message = e.message ?: "Unknown error",
                            cause = e
                        )
                    }
                    throw initException
                }

                val duration = System.currentTimeMillis() - startTime
                logger.info("✓  Completed: {} ({} ms)", initializer.initializerId, duration)
            }

            logger.info("")
            logger.info("╔════════════════════════════════════════════════════╗")
            logger.info("║ ✓ APPLICATION INITIALIZATION COMPLETE            ║")
            logger.info("║                                                    ║")
            logger.info("║ Status: READY FOR TRAFFIC                          ║")
            logger.info("║                                                    ║")
            logger.info("║ ✓ All components instantiated                     ║")
            logger.info("║ ✓ Database operational & schema ready             ║")
            logger.info("║ ✓ Transaction adapters configured                 ║")
            logger.info("║ ✓ Scheduler tasks registered & running            ║")
            logger.info("║ ✓ All initializer hooks completed                 ║")
            logger.info("║                                                    ║")
            logger.info("╚════════════════════════════════════════════════════╝")
            logger.info("")

        } catch (e: Exception) {
            logger.error("Fatal error during initialization", e)
            throw e
        }
    }
}
