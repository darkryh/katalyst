package com.ead.katalyst.transactions.adapter

import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry for transaction adapters.
 *
 * Adapters are sorted by priority (high → low) and executed in order during transaction phases.
 * This enables modules to register their transaction concerns independently without
 * creating circular dependencies or tight coupling.
 *
 * **Thread Safety:**
 * This registry is thread-safe. Multiple threads can register adapters and execute phases
 * concurrently without synchronization issues.
 *
 * **Execution Model:**
 * 1. Adapters are stored in priority order
 * 2. When a phase is executed, all adapters are called for that phase
 * 3. If an adapter throws, the exception is logged and execution continues
 * 4. The transaction state is never affected by adapter errors
 *
 * **Usage:**
 * ```kotlin
 * val registry = TransactionAdapterRegistry()
 *
 * // Register adapters (done during DI bootstrap)
 * registry.register(PersistenceTransactionAdapter())
 * registry.register(EventsTransactionAdapter(eventBus))
 * registry.register(CachingTransactionAdapter())
 *
 * // Later, during transaction execution
 * registry.executeAdapters(TransactionPhase.AFTER_COMMIT, context)
 * ```
 */
class TransactionAdapterRegistry {
    private val adapters = CopyOnWriteArrayList<TransactionAdapter>()
    private val logger = LoggerFactory.getLogger(TransactionAdapterRegistry::class.java)

    /**
     * Register a transaction adapter.
     *
     * Adapters are automatically sorted by priority after registration.
     * Higher priority adapters execute first during each phase.
     *
     * @param adapter The adapter to register
     */
    fun register(adapter: TransactionAdapter) {
        adapters.add(adapter)
        // Re-sort by priority (higher first)
        adapters.sortByDescending { it.priority() }
        logger.info("Registered transaction adapter: {} (priority: {})", adapter.name(), adapter.priority())
    }

    /**
     * Remove an adapter.
     *
     * Mainly used for testing/cleanup. In production, adapters are typically registered
     * once during DI bootstrap and persist for the application lifetime.
     *
     * @param adapter The adapter to remove
     */
    fun unregister(adapter: TransactionAdapter) {
        adapters.remove(adapter)
        logger.debug("Unregistered transaction adapter: {}", adapter.name())
    }

    /**
     * Execute all adapters for a specific transaction phase.
     *
     * Adapters execute in priority order (high → low). If an adapter throws an exception,
     * it is logged and other adapters continue to execute. No exception is propagated.
     *
     * @param phase The transaction phase
     * @param context The transaction context
     */
    suspend fun executeAdapters(
        phase: TransactionPhase,
        context: TransactionEventContext,
        failFast: Boolean = false
    ) {
        if (adapters.isEmpty()) {
            logger.debug("No adapters registered for phase: {}", phase)
            return
        }

        logger.debug("Executing {} adapter(s) for phase: {}", adapters.size, phase)

        for (adapter in adapters) {
            try {
                logger.debug("Executing adapter {} for phase {}", adapter.name(), phase)
                adapter.onPhase(phase, context)
            } catch (e: Exception) {
                logger.error(
                    "Error in transaction adapter {} during {}: {}",
                    adapter.name(),
                    phase,
                    e.message,
                    e
                )
                if (failFast) {
                    throw e
                }
                // Continue executing other adapters even if one fails
            }
        }

        logger.debug("Finished executing adapters for phase: {}", phase)
    }

    /**
     * Get all registered adapters (for testing/inspection).
     *
     * @return Immutable list of registered adapters in priority order
     */
    fun getAdapters(): List<TransactionAdapter> = adapters.toList()

    /**
     * Clear all registered adapters (for testing/cleanup).
     *
     * Do not use in production code.
     */
    fun clear() {
        adapters.clear()
        logger.debug("Cleared all transaction adapters")
    }

    /**
     * Get the count of registered adapters.
     *
     * @return Number of adapters
     */
    fun size(): Int = adapters.size
}
