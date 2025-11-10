package com.ead.katalyst.events.bus.adapter

import com.ead.katalyst.events.bus.ApplicationEventBus
import com.ead.katalyst.events.bus.EventHandlerConfig
import com.ead.katalyst.events.bus.EventHandlingMode
import com.ead.katalyst.events.bus.deduplication.EventDeduplicationStore
import com.ead.katalyst.events.bus.deduplication.NoOpEventDeduplicationStore
import com.ead.katalyst.events.bus.validation.DefaultEventPublishingValidator
import com.ead.katalyst.events.bus.validation.EventPublishingValidator
import com.ead.katalyst.events.bus.validation.EventValidationException
import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory

/**
 * Transaction adapter for event publishing concerns.
 *
 * Supports two event handling modes:
 *
 * **SYNC_BEFORE_COMMIT (Default):**
 * - Handlers execute BEFORE transaction commits
 * - Handler failures cause transaction rollback (strong consistency)
 * - No Saga pattern needed - automatic "under the hood" rollback
 * - Suitable for events that require transactional guarantees
 *
 * **ASYNC_AFTER_COMMIT:**
 * - Handlers execute AFTER transaction commits
 * - Handler failures don't rollback transaction (eventual consistency)
 * - Suitable for fire-and-forget notifications
 *
 * **Execution Priority**: 5 (medium priority - runs after persistence)
 *
 * **Phases Handled:**
 * - BEFORE_COMMIT: Publish SYNC_BEFORE_COMMIT events (failures cause rollback)
 * - AFTER_COMMIT: Publish ASYNC_AFTER_COMMIT events (failures don't rollback)
 * - ON_ROLLBACK: Discard all pending events
 *
 * **Example:**
 * ```kotlin
 * // In service code within transaction:
 * transactionManager.transaction {
 *     val user = userRepository.save(newUser)
 *     eventBus.publish(UserCreatedEvent(user.id))  // SYNC_BEFORE_COMMIT - will rollback on failure
 * }
 * // If handler throws, entire transaction rolls back automatically
 * ```
 */
class EventsTransactionAdapter(
    private val eventBus: ApplicationEventBus,
    private val validator: EventPublishingValidator = DefaultEventPublishingValidator({ eventBus.hasHandlers(it) }),
    private val deduplicationStore: EventDeduplicationStore = NoOpEventDeduplicationStore()
) : TransactionAdapter {
    private val logger = LoggerFactory.getLogger(EventsTransactionAdapter::class.java)

    /**
     * Events to publish asynchronously after commit.
     * SYNC_BEFORE_COMMIT events are published in BEFORE_COMMIT phase and removed from this list.
     */
    private val asyncEventsForAfterCommit = mutableListOf<DomainEvent>()

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
            TransactionPhase.BEFORE_COMMIT -> publishSyncBeforeCommitEvents(context)
            TransactionPhase.AFTER_COMMIT -> publishAsyncAfterCommitEvents()
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
     * Publishes SYNC_BEFORE_COMMIT events before transaction commits.
     *
     * These events are published within the transaction context.
     * If a handler fails, the exception bubbles up and causes transaction rollback.
     * This provides strong consistency: all-or-nothing with respect to handlers.
     *
     * ASYNC_AFTER_COMMIT events are queued for later publishing in AFTER_COMMIT phase.
     *
     * @param context The transaction context containing pending events
     * @throws Exception If a SYNC_BEFORE_COMMIT handler fails (causes transaction rollback)
     */
    private suspend fun publishSyncBeforeCommitEvents(context: TransactionEventContext) {
        val pendingEvents = context.getPendingEvents()
        if (pendingEvents.isEmpty()) {
            logger.debug("No pending events to publish before transaction commit")
            return
        }

        logger.debug("Publishing {} pending event(s) before transaction commit", pendingEvents.size)

        val syncEvents = mutableListOf<DomainEvent>()
        val asyncEvents = mutableListOf<DomainEvent>()

        // Separate events by handling mode
        for (event in pendingEvents) {
            val config = eventBus.getHandlerConfig(event)
            if (config.handlingMode == EventHandlingMode.SYNC_BEFORE_COMMIT) {
                syncEvents.add(event)
            } else {
                asyncEvents.add(event)
            }
        }

        // Queue async events for AFTER_COMMIT phase
        if (asyncEvents.isNotEmpty()) {
            asyncEventsForAfterCommit.addAll(asyncEvents)
            logger.debug("{} event(s) queued for async publishing after commit", asyncEvents.size)
        }

        // Publish SYNC_BEFORE_COMMIT events (failures bubble up and rollback transaction)
        var publishedCount = 0
        for (event in syncEvents) {
            // Check deduplication store
            if (deduplicationStore.isEventPublished(event.eventId)) {
                logger.warn(
                    "Duplicate event detected and skipped: {} (eventId: {})",
                    event::class.simpleName,
                    event.eventId
                )
                continue  // Skip duplicate
            }

            logger.debug(
                "Publishing SYNC event before commit: {} (eventId: {})",
                event::class.simpleName,
                event.eventId
            )

            // Let exceptions bubble up - this will cause transaction rollback
            eventBus.publish(event)
            deduplicationStore.markAsPublished(event.eventId)
            publishedCount++

            logger.debug("SYNC event published successfully: {}", event::class.simpleName)
        }

        context.clearPendingEvents()
        logger.debug(
            "Finished publishing SYNC events: {} published, {} queued for async after commit",
            publishedCount,
            asyncEvents.size
        )
    }

    /**
     * Publishes ASYNC_AFTER_COMMIT events after transaction commits.
     *
     * These events are published outside the transaction context.
     * Handler failures don't affect the transaction - they're logged and isolated.
     * This provides eventual consistency with decoupled systems.
     */
    private suspend fun publishAsyncAfterCommitEvents() {
        if (asyncEventsForAfterCommit.isEmpty()) {
            logger.debug("No async events to publish after transaction commit")
            return
        }

        logger.debug("Publishing {} async event(s) after transaction commit", asyncEventsForAfterCommit.size)
        var publishedCount = 0
        var failedCount = 0

        for (event in asyncEventsForAfterCommit) {
            // Check deduplication store
            if (deduplicationStore.isEventPublished(event.eventId)) {
                logger.warn(
                    "Duplicate async event detected and skipped: {} (eventId: {})",
                    event::class.simpleName,
                    event.eventId
                )
                continue
            }

            logger.debug(
                "Publishing ASYNC event after commit: {} (eventId: {})",
                event::class.simpleName,
                event.eventId
            )

            try {
                eventBus.publish(event)
                deduplicationStore.markAsPublished(event.eventId)
                publishedCount++

                logger.debug("ASYNC event published successfully: {}", event::class.simpleName)
            } catch (e: Exception) {
                failedCount++
                logger.error(
                    "Failed to publish async event {} (eventId: {}): {} - Handler failures are isolated and don't rollback",
                    event::class.simpleName,
                    event.eventId,
                    e.message,
                    e
                )
                // Don't rethrow - async events are fire-and-forget
                // Failures are logged for monitoring/alerting
            }
        }

        asyncEventsForAfterCommit.clear()
        logger.debug(
            "Finished publishing async events: {} published, {} failed (isolated from transaction)",
            publishedCount,
            failedCount
        )
    }

    /**
     * Discards all pending events when the transaction is rolled back.
     *
     * This prevents both SYNC and ASYNC events from being published if the transaction fails,
     * ensuring consistency between database state and domain events.
     *
     * SYNC events that failed are already rolled back via exception.
     * ASYNC events queued for after-commit are discarded here.
     *
     * @param context The transaction context containing pending events
     */
    private fun discardPendingEvents(context: TransactionEventContext) {
        val pendingCount = context.getPendingEventCount()
        val asyncCount = asyncEventsForAfterCommit.size

        if (pendingCount == 0 && asyncCount == 0) {
            logger.debug("No pending events to discard on rollback")
            return
        }

        logger.debug("Discarding {} pending event(s) due to transaction rollback", pendingCount)
        context.clearPendingEvents()

        if (asyncCount > 0) {
            logger.debug("Discarding {} async event(s) queued for after-commit due to rollback", asyncCount)
            asyncEventsForAfterCommit.clear()
        }

        logger.debug("Finished discarding pending events")
    }
}
