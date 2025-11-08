package com.ead.katalyst.transactions.adapter

import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase

/**
 * Adapter for module-specific transaction lifecycle handling.
 *
 * Modules register adapters to handle their concerns during transaction phases:
 * - Persistence module: manages database-specific logic
 * - Events module: manages event publishing
 * - Future modules: add their own adapters
 *
 * Each adapter is independent and doesn't know about other modules.
 *
 * **Example:**
 * ```kotlin
 * class EventsTransactionAdapter(private val eventBus: ApplicationEventBus) : TransactionAdapter {
 *     override fun name() = "Events"
 *     override fun priority() = 5
 *
 *     override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
 *         when (phase) {
 *             TransactionPhase.AFTER_COMMIT -> {
 *                 // Publish queued events
 *                 context.getPendingEvents().forEach { eventBus.publish(it) }
 *             }
 *             TransactionPhase.ON_ROLLBACK -> {
 *                 // Discard events
 *                 context.clearPendingEvents()
 *             }
 *             else -> {}
 *         }
 *     }
 * }
 * ```
 */
interface TransactionAdapter {
    /**
     * Module name for identification and logging.
     *
     * @return Human-readable name (e.g., "Events", "Persistence", "Caching")
     */
    fun name(): String

    /**
     * Execute adapter logic for a specific transaction phase.
     *
     * Adapters execute in priority order. Exceptions are logged but don't fail the transaction.
     *
     * **Execution Model:**
     * - Adapters for a phase execute sequentially in priority order (high → low)
     * - If an adapter throws, the exception is logged and other adapters continue
     * - The transaction state is not affected by adapter errors
     *
     * **Phases:**
     * - BEFORE_BEGIN: Setup, resource allocation (e.g., connect to DB)
     * - AFTER_BEGIN: Initialization (e.g., start logging)
     * - BEFORE_COMMIT: Validation (e.g., flush pending writes)
     * - AFTER_COMMIT: Publish events, invalidate caches (after transaction commits)
     * - ON_ROLLBACK: Cleanup on rollback (e.g., discard events)
     * - AFTER_ROLLBACK: Final cleanup after rollback
     *
     * @param phase The transaction phase
     * @param context The transaction context with event queue and metadata
     */
    suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext)

    /**
     * Execution priority (higher = earlier execution within a phase).
     *
     * Adapters with higher priority execute first during each phase.
     *
     * **Common Priority Values:**
     * - 100: System-critical (reserved)
     * - 50-100: Infrastructure (persistence, connections)
     * - 10-50: Core functionality (events, caching)
     * - 0-10: Optional/logging
     * - Default: 0
     *
     * **Example:**
     * ```
     * Persistence adapter: priority 50 (runs first, manages DB)
     * Events adapter: priority 10 (runs second, publishes events)
     * Logging adapter: priority 0 (runs last, logs completion)
     * ```
     *
     * @return Priority value (default: 0)
     */
    fun priority(): Int = 0
}
