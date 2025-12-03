package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.transactions.sideeffects.TransactionalSideEffect
import io.github.darkryh.katalyst.transactions.sideeffects.SideEffectHandlingMode
import io.github.darkryh.katalyst.transactions.sideeffects.SideEffectResult

/**
 * Domain event adapter for the generic transactional side-effect framework.
 *
 * Wraps domain events to be executed as transactional side-effects.
 * Uses the generic TransactionalSideEffect framework, enabling:
 * - SYNC_BEFORE_COMMIT: Event handlers execute before transaction commits
 * - ASYNC_AFTER_COMMIT: Event handlers execute after transaction commits
 *
 * This is a thin wrapper that delegates to ApplicationEventBus for actual event publishing.
 *
 * @param event The domain event to execute as a side-effect
 * @param eventBus The event bus for publishing the event
 * @param handlingMode When to execute the event handler (SYNC or ASYNC)
 *
 * Example:
 * ```kotlin
 * val event = UserCreatedEvent(userId = 123)
 * val sideEffect = EventSideEffect(event, eventBus, SideEffectHandlingMode.SYNC_BEFORE_COMMIT)
 *
 * // Executes event handler before transaction commits
 * sideEffect.execute(Unit)
 *
 * // If handler fails, transaction rolls back automatically
 * ```
 */
data class EventSideEffect(
    val event: DomainEvent,
    val eventBus: ApplicationEventBus,
    override val handlingMode: SideEffectHandlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
) : TransactionalSideEffect<Unit> {

    /**
     * Unique identifier for this event side-effect.
     *
     * Uses the event's eventId for identification.
     */
    override val sideEffectId: String = event.eventId

    /**
     * Execute the event as a side-effect.
     *
     * Publishes the event to registered handlers via the event bus.
     * For SYNC_BEFORE_COMMIT mode:
     * - Handlers execute immediately within transaction context
     * - Handler failures cause exceptions to bubble up (transaction rollback)
     *
     * For ASYNC_AFTER_COMMIT mode:
     * - Handlers are queued for execution after transaction commits
     * - Handler failures are isolated from transaction
     *
     * @param context Unused (events don't require context)
     * @return Success result if event publishes without errors
     * @throws Exception If event publishing fails (SYNC mode only)
     */
    override suspend fun execute(context: Unit): SideEffectResult {
        return try {
            // Publish event to all registered handlers
            eventBus.publish(event)

            // Return success
            SideEffectResult.Success(
                metadata = mapOf(
                    "eventType" to event.eventType(),
                    "eventId" to event.eventId
                )
            )
        } catch (e: Exception) {
            // For SYNC_BEFORE_COMMIT: exception will bubble up and rollback transaction
            // For ASYNC_AFTER_COMMIT: exception will be caught and logged
            SideEffectResult.Failed(error = e)
        }
    }

    /**
     * Compensate (no-op for events).
     *
     * Events don't need compensation - they're either published or not.
     * If a handler fails in SYNC mode, the transaction rolls back and the event isn't committed to any system.
     *
     * @param result The result from execute() (unused)
     */
    override suspend fun compensate(result: SideEffectResult) {
        // Events don't require compensation
        // Rollback happens at transaction level
    }
}
