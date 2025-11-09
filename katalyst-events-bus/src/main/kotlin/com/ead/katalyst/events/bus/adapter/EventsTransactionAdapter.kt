package com.ead.katalyst.events.bus.adapter

import com.ead.katalyst.events.bus.ApplicationEventBus
import com.ead.katalyst.events.bus.deduplication.EventDeduplicationStore
import com.ead.katalyst.events.bus.deduplication.NoOpEventDeduplicationStore
import com.ead.katalyst.events.bus.validation.DefaultEventPublishingValidator
import com.ead.katalyst.events.bus.validation.EventPublishingValidator
import com.ead.katalyst.events.bus.validation.EventValidationException
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
    private val eventBus: ApplicationEventBus,
    private val validator: EventPublishingValidator = DefaultEventPublishingValidator({ eventBus.hasHandlers(it) }),
    private val deduplicationStore: EventDeduplicationStore = NoOpEventDeduplicationStore()
) : TransactionAdapter {
    private val logger = LoggerFactory.getLogger(EventsTransactionAdapter::class.java)

    override fun name(): String = "Events"

    override fun priority(): Int = 5  // Medium priority - after persistence

    /**
     * Mark this adapter as critical:
     * If event publishing validation fails, the transaction must rollback
     * to prevent inconsistent state (DB changes without corresponding events)
     */
    override fun isCritical(): Boolean = true

    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_COMMIT_VALIDATION -> validateAllEvents(context)
            TransactionPhase.BEFORE_COMMIT -> publishPendingEvents(context)
            TransactionPhase.ON_ROLLBACK -> discardPendingEvents(context)
            else -> Unit
        }
    }

    /**
     * Validate all pending events before committing the transaction.
     *
     * This is a critical validation: if any event cannot be published,
     * the entire transaction is rolled back.
     *
     * @param context The transaction context containing pending events
     * @throws EventValidationException if any event fails validation
     */
    private suspend fun validateAllEvents(context: TransactionEventContext) {
        val pendingEvents = context.getPendingEvents()
        if (pendingEvents.isEmpty()) {
            logger.debug("No pending events to validate")
            return
        }

        logger.debug("Validating {} pending event(s) before transaction commit", pendingEvents.size)

        for (event in pendingEvents) {
            val result = validator.validate(event)
            if (!result.isValid) {
                logger.error(
                    "Event validation failed before commit: {} (error: {})",
                    event::class.simpleName,
                    result.error
                )
                throw EventValidationException(
                    event,
                    result.error ?: "Validation failed"
                )
            }
            logger.debug("Event validation passed: {}", event::class.simpleName)
        }

        logger.debug("All {} event(s) validated successfully", pendingEvents.size)
    }

    /**
     * Publishes all pending events that were queued during the transaction.
     *
     * Checks deduplication store first: if event was already published,
     * skips it to prevent duplicates from retries.
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
        var publishedCount = 0
        var skippedCount = 0

        for (event in pendingEvents) {
            // NEW - P0 Critical: Check deduplication store
            if (deduplicationStore.isEventPublished(event.eventId)) {
                logger.warn(
                    "Duplicate event detected and skipped: {} (eventId: {})",
                    event::class.simpleName,
                    event.eventId
                )
                skippedCount++
                continue  // Skip duplicate
            }

            logger.debug("Publishing event: {} (eventId: {})", event::class.simpleName, event.eventId)
            try {
                eventBus.publish(event)

                // NEW - P0 Critical: Mark as published after successful publishing
                deduplicationStore.markAsPublished(event.eventId)
                publishedCount++
            } catch (e: Exception) {
                logger.error(
                    "Failed to publish event {} (eventId: {}): {}",
                    event::class.simpleName,
                    event.eventId,
                    e.message,
                    e
                )
                // Continue publishing other events
            }
        }

        context.clearPendingEvents()
        logger.debug(
            "Finished publishing pending events: {} published, {} skipped (duplicates)",
            publishedCount,
            skippedCount
        )
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
