package io.github.darkryh.katalyst.transactions.adapter

import io.github.darkryh.katalyst.transactions.context.TransactionEventContext
import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
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
     * Adapters execute in priority order (high → low). Tracks execution results including
     * successes and failures. Critical adapter failures cause TransactionAdapterException
     * to be thrown when failFast=true.
     *
     * @param phase The transaction phase
     * @param context The transaction context
     * @param failFast If true, throw exception on critical adapter failure
     * @return Execution results for monitoring and debugging
     */
    suspend fun executeAdapters(
        phase: TransactionPhase,
        context: TransactionEventContext,
        failFast: Boolean = false
    ): PhaseExecutionResults {
        if (adapters.isEmpty()) {
            logger.debug("No adapters registered for phase: {}", phase)
            return PhaseExecutionResults(phase, emptyList())
        }

        logger.debug("Executing {} adapter(s) for phase: {}", adapters.size, phase)
        val results = mutableListOf<AdapterExecutionResult>()

        for (adapter in adapters) {
            val startTime = System.currentTimeMillis()
            try {
                logger.debug("Executing adapter {} for phase {}", adapter.name(), phase)
                adapter.onPhase(phase, context)

                val duration = System.currentTimeMillis() - startTime
                results.add(
                    AdapterExecutionResult(
                        adapter = adapter,
                        phase = phase,
                        success = true,
                        error = null,
                        duration = duration
                    )
                )
                logger.debug(
                    "Adapter {} executed successfully in {}ms",
                    adapter.name(),
                    duration
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                logger.error(
                    "Error in transaction adapter {} during {} ({}ms): {}",
                    adapter.name(),
                    phase,
                    duration,
                    e.message,
                    e
                )

                results.add(
                    AdapterExecutionResult(
                        adapter = adapter,
                        phase = phase,
                        success = false,
                        error = e,
                        duration = duration
                    )
                )

                // If critical adapter fails and failFast is enabled, throw exception immediately
                if (failFast && adapter.isCritical()) {
                    val criticalFailures = results.filter { !it.success && it.adapter.isCritical() }
                    val failedNames = criticalFailures.map { it.adapter.name() }.joinToString(", ")
                    throw TransactionAdapterException(
                        "Critical adapter(s) failed during $phase: $failedNames",
                        e
                    )
                }

                // Continue executing other adapters (non-critical failures don't stop execution)
            }
        }

        logger.debug("Finished executing adapters for phase: {}", phase)
        val executionResults = PhaseExecutionResults(phase, results)
        logger.debug("Phase execution summary: {}", executionResults.getSummary())
        return executionResults
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
