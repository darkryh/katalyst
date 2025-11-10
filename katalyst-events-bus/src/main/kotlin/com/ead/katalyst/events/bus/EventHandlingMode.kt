package com.ead.katalyst.events.bus

/**
 * Controls when event handlers are executed.
 *
 * - SYNC_BEFORE_COMMIT: Handlers run before transaction commits (failures rollback transaction)
 * - ASYNC_AFTER_COMMIT: Handlers run after transaction commits (failures don't rollback)
 *
 * Default: SYNC_BEFORE_COMMIT for consistency, can override per event type
 */
enum class EventHandlingMode {
    /**
     * Synchronous mode: Event handlers execute BEFORE transaction commits.
     *
     * **Guarantees:**
     * - If handler fails, entire transaction is rolled back
     * - Strong consistency (all-or-nothing)
     * - Developer doesn't need saga pattern
     * - Simple API: handlers just throw on error
     *
     * **Trade-offs:**
     * - Slower (handlers block commit)
     * - Long-running handlers slow down transaction
     *
     * **Example:**
     * ```kotlin
     * transactionManager.transaction {
     *     account = repository.save(account)
     *     eventBus.publish(UserRegisteredEvent(account.id)) // Handler runs NOW
     *     // If handler fails here, entire tx rolls back
     * }
     * ```
     */
    SYNC_BEFORE_COMMIT,

    /**
     * Asynchronous mode: Event handlers execute AFTER transaction commits.
     *
     * **Guarantees:**
     * - Fast commit (handlers don't block)
     * - Eventual consistency
     * - Decoupled systems
     *
     * **Trade-offs:**
     * - If handler fails, can't rollback transaction
     * - May have partial state in database
     * - Requires idempotent handlers
     *
     * **Example:**
     * ```kotlin
     * transactionManager.transaction {
     *     account = repository.save(account)
     *     eventBus.publish(UserRegisteredEvent(account.id)) // Handler queued, runs LATER
     *     // Tx commits regardless of handler result
     * }
     * ```
     */
    ASYNC_AFTER_COMMIT
}

/**
 * Event handler configuration.
 *
 * Controls how specific event types are handled.
 */
data class EventHandlerConfig(
    /**
     * The event type this config applies to.
     */
    val eventType: String,

    /**
     * When to execute handlers for this event type.
     */
    val handlingMode: EventHandlingMode = EventHandlingMode.SYNC_BEFORE_COMMIT,

    /**
     * Maximum timeout for handler execution (ms).
     *
     * Only applies to SYNC_BEFORE_COMMIT mode.
     * If handler takes longer, throws TimeoutException.
     */
    val timeoutMs: Long = 5000,

    /**
     * Whether to fail transaction if handler fails.
     *
     * true: Handler failure = transaction rollback (SYNC_BEFORE_COMMIT only)
     * false: Handler failure = logged but transaction continues
     */
    val failOnHandlerError: Boolean = true
)
