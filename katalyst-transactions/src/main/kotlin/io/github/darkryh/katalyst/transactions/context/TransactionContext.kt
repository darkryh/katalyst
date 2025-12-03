package io.github.darkryh.katalyst.transactions.context

import io.github.darkryh.katalyst.events.DomainEvent
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Manages pending domain events within a transaction context.
 *
 * This context element allows events to be queued during transaction execution
 * and published after the transaction commits. Events are stored in a thread-local
 * queue that is automatically cleaned up when the context leaves scope.
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

    // Thread-local queue for events in current transaction
    private val pendingEvents = ThreadLocal.withInitial { mutableListOf<DomainEvent>() }

    /**
     * Queues an event to be published after the transaction commits.
     *
     * @param event The domain event to queue
     */
    fun queueEvent(event: DomainEvent) {
        pendingEvents.get().add(event)
    }

    /**
     * Retrieves all pending events without removing them from the queue.
     *
     * @return List of queued events
     */
    fun getPendingEvents(): List<DomainEvent> {
        return pendingEvents.get().toList()
    }

    /**
     * Clears all pending events from the queue.
     * Called after events are published or transaction rolls back.
     */
    fun clearPendingEvents() {
        pendingEvents.get().clear()
    }

    /**
     * Checks if there are any pending events.
     *
     * @return true if events are queued, false otherwise
     */
    fun hasPendingEvents(): Boolean {
        return pendingEvents.get().isNotEmpty()
    }

    /**
     * Gets the count of pending events.
     *
     * @return Number of queued events
     */
    fun getPendingEventCount(): Int {
        return pendingEvents.get().size
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
