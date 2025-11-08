package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler

/**
 * Primary interface for local in-memory event publishing and subscription.
 *
 * The EventBus is responsible for:
 * - Publishing events to all interested handlers (in-memory only)
 * - Managing handler subscriptions
 * - Executing handlers asynchronously and in parallel
 * - Handling errors gracefully (errors are logged, not propagated)
 *
 * The bus does NOT handle:
 * - Serialization (that's the transport layer)
 * - Routing to external systems (that's the client layer)
 * - Validation (that's done before publishing)
 *
 * **Usage:**
 *
 * ```kotlin
 * // Get the bus from DI
 * val eventBus = get<EventBus>()
 *
 * // Register a handler
 * eventBus.register(myEventHandler)
 *
 * // Publish an event
 * eventBus.publish(UserCreatedEvent(userId = "123", email = "user@example.com"))
 * ```
 *
 * **Handler Execution:**
 *
 * - All handlers for an event type are called asynchronously
 * - Handlers run in parallel (using coroutine dispatcher)
 * - If one handler fails, others still execute
 * - Exceptions are caught and logged
 *
 * **Handler Registration:**
 *
 * - Handlers are registered during application startup (auto-discovery)
 * - Multiple handlers can listen to the same event type
 * - Sealed event hierarchies automatically register for all subtypes
 */
interface EventBus {
    /**
     * Publish an event to all interested handlers.
     *
     * This is a suspending function for async handler execution.
     *
     * **Behavior:**
     * 1. Calls beforePublish() on all interceptors
     * 2. If any interceptor aborts, returns immediately
     * 3. Finds all handlers for this event type
     * 4. Launches each handler asynchronously
     * 5. Calls afterPublish() on all interceptors
     * 6. Returns when all handlers have completed
     *
     * **Error Handling:**
     * - If a handler throws, the exception is caught and logged
     * - Other handlers still execute
     * - The exception doesn't propagate to the caller
     *
     * @param event The event to publish
     * @throws Any exception will be caught and logged
     */
    suspend fun publish(event: DomainEvent)

    /**
     * Register a handler to listen for events.
     *
     * **Important:** This is called during application startup by EventTopology.
     * Applications should not call this directly.
     *
     * **Sealed Hierarchies:**
     * If the handler's eventType is a sealed class, the bus automatically
     * registers the handler for all concrete subtypes.
     *
     * Example:
     * ```kotlin
     * sealed class UserEvent : DomainEvent
     * data class UserCreatedEvent(...) : UserEvent()
     * data class UserDeletedEvent(...) : UserEvent()
     *
     * // Register for sealed parent
     * eventBus.register(handler with eventType = UserEvent::class)
     * // Automatically registered for:
     * // - UserCreatedEvent
     * // - UserDeletedEvent
     * ```
     *
     * @param handler The handler to register
     */
    fun register(handler: EventHandler<out DomainEvent>)
}
