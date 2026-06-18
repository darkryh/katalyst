package io.github.darkryh.katalyst.transactions.context

import io.github.darkryh.katalyst.events.DomainEvent
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages pending domain events within a transaction context.
 *
 * This context element allows events to be queued during transaction execution
 * and published after the transaction commits. State belongs to this context
 * instance, so it remains stable when a coroutine resumes on another thread.
 *
 * **Usage:**
 * ```kotlin
 * val transactionContext = TransactionEventContext()
 * withContext(transactionContext) {
 *     // Events published here are queued
 *     eventBus.publish(UserCreatedEvent(...))
 *
 *     // After transaction commits, events are auto-published
 *     val events = transactionContext.getPendingEvents()
 * }
 * ```
 *
 * **Key Features:**
 * - Thread-safe event queue management
 * - Automatic cleanup on context exit
 * - No manual event handling required
 * - Transparent to application code
 */
class TransactionEventContext : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionEventContext>

    private val pendingEvents = ConcurrentLinkedQueue<DomainEvent>()
    private val deferredItems = ConcurrentHashMap<Any, ConcurrentLinkedQueue<Any>>()

    /**
     * Queues an event to be published after the transaction commits.
     *
     * @param event The domain event to queue
     */
    fun queueEvent(event: DomainEvent) {
        pendingEvents.add(event)
    }

    /**
     * Retrieves all pending events without removing them from the queue.
     *
     * @return List of queued events
     */
    fun getPendingEvents(): List<DomainEvent> {
        return pendingEvents.toList()
    }

    /**
     * Clears all pending events from the queue.
     * Called after events are published or transaction rolls back.
     */
    fun clearPendingEvents() {
        pendingEvents.clear()
    }

    /**
     * Checks if there are any pending events.
     *
     * @return true if events are queued, false otherwise
     */
    fun hasPendingEvents(): Boolean {
        return pendingEvents.isNotEmpty()
    }

    /**
     * Gets the count of pending events.
     *
     * @return Number of queued events
     */
    fun getPendingEventCount(): Int {
        return pendingEvents.size
    }

    /** Stores work owned by an adapter until a later transaction phase. */
    fun defer(owner: Any, item: Any) {
        deferredItems.computeIfAbsent(owner) { ConcurrentLinkedQueue() }.add(item)
    }

    /** Removes and returns all deferred work for [owner]. */
    fun drainDeferred(owner: Any): List<Any> =
        deferredItems.remove(owner)?.toList().orEmpty()

    /** Discards deferred work for [owner], returning the number of removed items. */
    fun clearDeferred(owner: Any): Int = deferredItems.remove(owner)?.size ?: 0

    /** Returns the deferred item count for diagnostics and lifecycle tests. */
    fun getDeferredCount(owner: Any): Int = deferredItems[owner]?.size ?: 0

    /** Clears every payload retained by this transaction context. */
    fun clear() {
        pendingEvents.clear()
        deferredItems.clear()
    }

    override fun toString(): String = "TransactionEventContext(pending=${getPendingEventCount()})"
}

/**
 * Gets the transaction event context from the current coroutine context.
 *
 * @return The TransactionEventContext if present, null otherwise
 */
fun CoroutineContext.getTransactionEventContext(): TransactionEventContext? {
    return this[TransactionEventContext]
}

/**
 * Checks if we're currently in a transaction context.
 *
 * @return true if a TransactionEventContext is active, false otherwise
 */
fun CoroutineContext.isInTransactionContext(): Boolean {
    return this[TransactionEventContext] != null
}
