package com.ead.katalyst.events.bus.adapter

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.bus.ApplicationEventBus
import com.ead.katalyst.events.bus.EventHandlerConfig
import com.ead.katalyst.events.bus.EventHandlingMode
import com.ead.katalyst.events.bus.EventSideEffect
import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import com.ead.katalyst.transactions.sideeffects.SideEffectConfig
import com.ead.katalyst.transactions.sideeffects.SideEffectHandlingMode
import com.ead.katalyst.transactions.sideeffects.TransactionalSideEffectAdapter
import org.slf4j.LoggerFactory

/**
 * Event-specialized adapter for the generic transactional side-effect framework.
 *
 * This adapter:
 * - Wraps domain events as EventSideEffect instances
 * - Uses the generic TransactionalSideEffectAdapter framework
 * - Bridges EventHandlingMode to SideEffectHandlingMode
 * - Handles event-specific operations (validation, deduplication, etc.)
 *
 * **Execution Priority**: 5 (medium priority - runs after persistence)
 *
 * **Phases Handled:**
 * - BEFORE_COMMIT_VALIDATION: Validate all events have handlers
 * - BEFORE_COMMIT: Execute SYNC_BEFORE_COMMIT events (failures rollback)
 * - AFTER_COMMIT: Execute ASYNC_AFTER_COMMIT events (failures isolated)
 * - ON_ROLLBACK: Discard all pending events
 *
 * Example:
 * ```kotlin
 * val eventBus = ApplicationEventBus()
 * val adapter = EventSideEffectAdapter(eventBus)
 *
 * // Configure specific events
 * adapter.configureEvent(EventHandlerConfig(
 *     eventType = "UserCreatedEvent",
 *     handlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT
 * ))
 *
 * // Use in transaction
 * transactionManager.transaction {
 *     user = repository.save(user)
 *     eventBus.publish(UserCreatedEvent(user.id))  // Will use configured mode
 * }
 * ```
 */
class EventSideEffectAdapter(
    private val eventBus: ApplicationEventBus
) : TransactionAdapter {

    private val logger = LoggerFactory.getLogger(EventSideEffectAdapter::class.java)

    /**
     * The underlying generic side-effect adapter.
     *
     * Does the heavy lifting using generic TransactionalSideEffectAdapter.
     */
    private val genericAdapter = TransactionalSideEffectAdapter<DomainEvent>(
        name = "Events",
        priority = 5,
        isCritical = true
    )

    /**
     * Pending events that were queued during transaction.
     */
    private val pendingEvents = mutableListOf<DomainEvent>()

    override fun name(): String = genericAdapter.name()

    override fun priority(): Int = genericAdapter.priority()

    override fun isCritical(): Boolean = genericAdapter.isCritical()

    /**
     * Handle transaction phases.
     *
     * Orchestrates event execution using the generic side-effect framework.
     */
    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_COMMIT_VALIDATION -> validateAllEvents(context)
            TransactionPhase.BEFORE_COMMIT -> executeEventSideEffects(context)
            TransactionPhase.AFTER_COMMIT -> handleAsyncEvents(context)
            TransactionPhase.ON_ROLLBACK -> discardPendingEvents(context)
            else -> Unit
        }
    }

    /**
     * Validate all pending events before committing the transaction.
     *
     * Ensures all events have registered handlers.
     *
     * @param context The transaction context
     * @throws Exception If validation fails (critical)
     */
    private suspend fun validateAllEvents(context: TransactionEventContext) {
        val events = context.getPendingEvents()
        if (events.isEmpty()) {
            logger.debug("No events to validate")
            return
        }

        logger.debug("Validating {} event(s) before transaction commit", events.size)

        for (event in events) {
            if (!eventBus.hasHandlers(event)) {
                logger.warn(
                    "No handlers registered for event: {}",
                    event.eventType()
                )
                // Still allow - some events may have no handlers
            }
        }

        logger.debug("All {} event(s) validated successfully", events.size)
    }

    /**
     * Execute events as side-effects using generic framework.
     *
     * Separates SYNC_BEFORE_COMMIT and ASYNC_AFTER_COMMIT events and processes them accordingly.
     *
     * @param context The transaction context
     * @throws Exception If SYNC event handler fails (causes rollback)
     */
    private suspend fun executeEventSideEffects(context: TransactionEventContext) {
        val events = context.getPendingEvents().filterIsInstance<DomainEvent>()
        if (events.isEmpty()) {
            logger.debug("No events to execute before transaction commit")
            return
        }

        logger.debug("Processing {} event(s) before transaction commit", events.size)

        val syncEvents = mutableListOf<DomainEvent>()
        val asyncEvents = mutableListOf<DomainEvent>()

        // Separate events by handling mode
        for (event in events) {
            val config = eventBus.getHandlerConfig(event)
            if (config.handlingMode == EventHandlingMode.SYNC_BEFORE_COMMIT) {
                syncEvents.add(event)
            } else {
                asyncEvents.add(event)
                pendingEvents.add(event)  // Queue for AFTER_COMMIT
            }
        }

        // Execute SYNC events (failures bubble up and rollback)
        var executedCount = 0
        for (event in syncEvents) {
            logger.debug(
                "Executing SYNC event before commit: {}",
                event.eventType()
            )

            try {
                // Convert event to side-effect and execute
                val sideEffect = EventSideEffect(
                    event,
                    eventBus,
                    SideEffectHandlingMode.SYNC_BEFORE_COMMIT
                )
                val result = sideEffect.execute(Unit)
                executedCount++

                logger.debug(
                    "SYNC event executed successfully: {}",
                    event.eventType()
                )
            } catch (e: Exception) {
                logger.error(
                    "SYNC event failed before commit: {} - {}",
                    event.eventType(),
                    e.message,
                    e
                )
                // Exception bubbles up - transaction rolls back
                throw e
            }
        }

        context.clearPendingEvents()

        logger.debug(
            "Finished executing SYNC events: {} executed, {} queued for async",
            executedCount,
            asyncEvents.size
        )
    }

    /**
     * Execute async events after transaction commits.
     *
     * These are executed outside transaction context.
     * Failures are logged and isolated from transaction.
     *
     * @param context The transaction context (unused - async is after commit)
     */
    private suspend fun handleAsyncEvents(context: TransactionEventContext) {
        if (pendingEvents.isEmpty()) {
            logger.debug("No async events to execute after transaction commit")
            return
        }

        logger.debug("Executing {} async event(s) after transaction commit", pendingEvents.size)

        var executedCount = 0
        var failedCount = 0

        for (event in pendingEvents) {
            logger.debug(
                "Executing ASYNC event after commit: {}",
                event.eventType()
            )

            try {
                // Convert event to side-effect and execute
                val sideEffect = EventSideEffect(
                    event,
                    eventBus,
                    SideEffectHandlingMode.ASYNC_AFTER_COMMIT
                )
                val result = sideEffect.execute(Unit)
                executedCount++

                logger.debug(
                    "ASYNC event executed successfully: {}",
                    event.eventType()
                )
            } catch (e: Exception) {
                failedCount++
                logger.error(
                    "Failed to execute async event after commit: {} - Handler failures are isolated",
                    event.eventType(),
                    e
                )
                // Don't rethrow - async events are fire-and-forget
            }
        }

        pendingEvents.clear()

        logger.debug(
            "Finished executing async events: {} executed, {} failed (isolated)",
            executedCount,
            failedCount
        )
    }

    /**
     * Discard all pending events on transaction rollback.
     *
     * @param context The transaction context
     */
    private fun discardPendingEvents(context: TransactionEventContext) {
        val pendingCount = context.getPendingEventCount()
        val asyncCount = pendingEvents.size

        if (pendingCount == 0 && asyncCount == 0) {
            logger.debug("No pending events to discard on rollback")
            return
        }

        logger.debug(
            "Discarding {} pending event(s) due to transaction rollback",
            pendingCount
        )
        context.clearPendingEvents()

        if (asyncCount > 0) {
            logger.debug(
                "Discarding {} async event(s) queued for after-commit due to rollback",
                asyncCount
            )
            pendingEvents.clear()
        }

        logger.debug("Finished discarding pending events")
    }

    /**
     * Configure event handling mode for a specific event type.
     *
     * @param config The event handler configuration
     */
    fun configureEvent(config: EventHandlerConfig) {
        logger.debug(
            "Configured event handlers for {}: mode={}, timeout={}ms",
            config.eventType,
            config.handlingMode,
            config.timeoutMs
        )

        // Register with generic framework
        val sideEffectConfig = SideEffectConfig(
            sideEffectId = config.eventType,
            handlingMode = when (config.handlingMode) {
                EventHandlingMode.SYNC_BEFORE_COMMIT -> SideEffectHandlingMode.SYNC_BEFORE_COMMIT
                EventHandlingMode.ASYNC_AFTER_COMMIT -> SideEffectHandlingMode.ASYNC_AFTER_COMMIT
            },
            timeoutMs = config.timeoutMs,
            failOnHandlerError = config.failOnHandlerError
        )
        genericAdapter.configureeSideEffect(sideEffectConfig)
    }
}
