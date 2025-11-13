package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.context.getTransactionEventContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * Transaction-aware event bus that defers event publishing until after transaction commit.
 *
 * This wrapper around ApplicationEventBus ensures that:
 * - Events published during transaction execution are queued
 * - Queued events are published after transaction commits
 * - Queued events are discarded if transaction rolls back
 * - Application code remains unchanged
 *
 * **Architecture:**
 * ```
 * EventBus.publish(event)
 *     ↓
 * TransactionAwareEventBus.publish(event)
 *     ↓
 *     Is in TransactionContext?
 *     ├─ YES → Queue event
 *     └─ NO  → Publish immediately
 * ```
 *
 * **Example:**
 * ```kotlin
 * transactionManager.transaction {
 *     // Events published here are queued
 *     eventBus.publish(UserCreatedEvent(...))
 * }
 * // After transaction commits: queued events are published
 * ```
 *
 * **Key Features:**
 * - Transparent to application code
 * - Automatic event deferral
 * - Proper error handling
 * - Detailed logging for debugging
 * - No manual event management needed
 *
 * @param delegate The ApplicationEventBus to delegate actual publishing to
 */
class TransactionAwareEventBus(
    private val delegate: ApplicationEventBus
) : EventBus {

    private val logger = LoggerFactory.getLogger(TransactionAwareEventBus::class.java)

    /**
     * Registers an event handler with the underlying event bus.
     *
     * @param handler The handler to register
     */
    override fun register(handler: EventHandler<out DomainEvent>) {
        delegate.register(handler)
    }

    override fun events(): SharedFlow<DomainEvent> = delegate.events()

    override fun <T : DomainEvent> eventsOf(eventType: KClass<T>): Flow<T> =
        delegate.eventsOf(eventType)

    /**
     * Publishes a domain event.
     *
     * If called within a transaction context (TransactionEventContext):
     * - Event is queued for later publication
     * - Event will be published after transaction commits
     *
     * If called outside a transaction context:
     * - Event is published immediately to all handlers
     *
     * @param event The event to publish
     */
    override suspend fun publish(event: DomainEvent) {
        val transactionContext = currentCoroutineContext().getTransactionEventContext()

        if (transactionContext != null) {
            // In transaction: queue the event for later publication
            logger.debug(
                "In transaction context, queueing event: {}",
                event.eventType()
            )
            transactionContext.queueEvent(event)
        } else {
            // Not in transaction: publish immediately
            logger.debug(
                "Not in transaction context, publishing event immediately: {}",
                event.eventType()
            )
            delegate.publish(event)
        }
    }
}

/**
 * Publishes all pending events from a transaction context.
 *
 * This is called by DatabaseTransactionManager after a successful transaction commit.
 * It publishes all queued events and clears the queue.
 *
 * **Error Handling:**
 * - Individual event publication errors are logged but don't affect other events
 * - All pending events are attempted to be published
 * - The queue is cleared even if some publications fail
 *
 * @param eventBus The underlying ApplicationEventBus to publish with
 * @param context The TransactionEventContext containing queued events
 * @param logger The logger for recording publication details
 */
suspend fun publishPendingEvents(
    eventBus: ApplicationEventBus,
    context: TransactionEventContext,
    logger: org.slf4j.Logger
) {
    val pendingEvents = context.getPendingEvents()

    if (pendingEvents.isEmpty()) {
        logger.debug("No pending events to publish")
        return
    }

    logger.debug("Publishing {} pending events after transaction commit", pendingEvents.size)

    coroutineScope {
        pendingEvents.forEach { event ->
            try {
                logger.debug("Publishing queued event: {}", event.eventType())
                eventBus.publish(event)
            } catch (e: Exception) {
                logger.error(
                    "Failed to publish pending event {}: {}",
                    event.eventType(),
                    e.message,
                    e
                )
                // Continue publishing other events
            }
        }
    }

    context.clearPendingEvents()
    logger.debug("Finished publishing pending events")
}
