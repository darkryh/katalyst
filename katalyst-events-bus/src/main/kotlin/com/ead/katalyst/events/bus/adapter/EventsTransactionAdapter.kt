package com.ead.katalyst.events.bus.adapter

import com.ead.katalyst.events.bus.ApplicationEventBus
import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory

/**
 * Transaction adapter for event publishing concerns.
 *
 * Handles event-specific transaction lifecycle:
 * - Publishing queued events right before the database commit (failures abort the commit)
 * - Discarding events on transaction rollback
 * - Event bus cleanup
 *
 * **Execution Priority**: 5 (medium priority - runs after persistence)
 *
 * **Phases Handled:**
 * - BEFORE_COMMIT: Publish all pending events that were queued during the transaction
 * - ON_ROLLBACK: Discard all pending events to prevent inconsistencies
 * - Other phases: No action needed
 *
 * **Event Publishing Flow:**
 * 1. During transaction: Events are queued in TransactionEventContext
 * 2. Immediately before commit: This adapter publishes all queued events (failures bubble up)
 * 3. On rollback: All queued events are discarded (not published)
 *
 * **Example:**
 * ```kotlin
 * // In service code within transaction:
 * transactionManager.transaction {
 *     val user = userRepository.save(newUser)
 *     eventBus.publish(UserCreatedEvent(user.id))  // Queued, not published yet
 * }
 * // Right before commit: UserCreatedEvent is published
 * // After transaction rolls back: UserCreatedEvent is discarded
 * ```
 */
class EventsTransactionAdapter(
    private val eventBus: ApplicationEventBus
) : TransactionAdapter {
    private val logger = LoggerFactory.getLogger(EventsTransactionAdapter::class.java)

    override fun name(): String = "Events"

    override fun priority(): Int = 5  // Medium priority - after persistence

    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_COMMIT -> publishPendingEvents(context)
            TransactionPhase.ON_ROLLBACK -> discardPendingEvents(context)
            else -> Unit
        }
    }

    /**
     * Publishes all pending events that were queued during the transaction.
     *
     * Each event is published independently. If an event fails to publish,
     * the error is logged but other events continue to be published.
     *
     * @param context The transaction context containing pending events
     */
    private suspend fun publishPendingEvents(context: TransactionEventContext) {
        val pendingEvents = context.getPendingEvents()
        if (pendingEvents.isEmpty()) {
            logger.debug("No pending events to publish before transaction commit")
            return
        }

        logger.debug("Publishing {} pending event(s) before transaction commit", pendingEvents.size)
        for (event in pendingEvents) {
            logger.debug("Publishing event: {}", event::class.simpleName)
            eventBus.publish(event)
        }
        context.clearPendingEvents()
        logger.debug("Finished publishing pending events")
    }

    /**
     * Discards all pending events when the transaction is rolled back.
     *
     * This prevents events from being published if the transaction fails,
     * ensuring consistency between database state and domain events.
     *
     * @param context The transaction context containing pending events
     */
    private fun discardPendingEvents(context: TransactionEventContext) {
        val pendingCount = context.getPendingEventCount()
        if (pendingCount == 0) {
            logger.debug("No pending events to discard on rollback")
            return
        }

        logger.debug("Discarding {} pending event(s) due to transaction rollback", pendingCount)
        context.clearPendingEvents()
        logger.debug("Finished discarding pending events")
    }
}
