package com.ead.katalyst.events

import kotlin.reflect.KClass

/**
 * Handler that reacts to published domain events.
 *
 * Implement this interface to handle specific event types.
 * Multiple handlers can listen to the same event type.
 * Handlers are invoked asynchronously in parallel when an event is published.
 *
 * **Single Event Type Handler:**
 *
 * ```kotlin
 * @Component
 * class SendWelcomeEmailHandler(
 *     private val emailService: EmailService
 * ) : EventHandler<UserCreatedEvent> {
 *
 *     override val eventType = UserCreatedEvent::class
 *
 *     override suspend fun handle(event: UserCreatedEvent) {
 *         emailService.sendWelcomeEmail(event.email)
 *     }
 * }
 * ```
 *
 * **Multiple Event Types (Sealed Hierarchy):**
 *
 * Register a handler for a sealed parent class, and it automatically
 * handles all concrete subtypes:
 *
 * ```kotlin
 * sealed class UserEvent : DomainEvent { ... }
 * data class UserCreatedEvent(...) : UserEvent()
 * data class UserDeletedEvent(...) : UserEvent()
 *
 * @Component
 * class UserAuditHandler : EventHandler<UserEvent> {
 *     override val eventType = UserEvent::class  // ← Sealed parent
 *
 *     override suspend fun handle(event: UserEvent) {
 *         when (event) {
 *             is UserCreatedEvent -> auditLog.created(event)
 *             is UserDeletedEvent -> auditLog.deleted(event)
 *         }
 *     }
 * }
 *
 * // The bus automatically registers this handler for:
 * // - UserCreatedEvent
 * // - UserDeletedEvent
 * // Because they are concrete subtypes of sealed UserEvent
 * ```
 *
 * **Important Notes:**
 *
 * - Handlers are discovered and registered during application startup
 * - Handlers must be annotated with @Component for auto-discovery
 * - Handlers are called asynchronously and in parallel
 * - If a handler throws an exception, it's logged but doesn't affect other handlers
 * - Handlers should be idempotent (safe to call multiple times)
 * - Handlers should complete quickly (async operations should return immediately)
 *
 * @param T The type of event this handler processes
 */
interface EventHandler<T : DomainEvent> {
    /**
     * The event type this handler listens to.
     *
     * Can be:
     * - A concrete event class (e.g., UserCreatedEvent::class)
     * - A sealed parent class (automatically registers for all subtypes)
     */
    val eventType: KClass<T>

    /**
     * Handle the event.
     *
     * Called asynchronously when an event of the specified type is published.
     *
     * **Contract:**
     * - Must be suspended function for async operations
     * - Exceptions are caught and logged, won't crash the bus
     * - Should complete reasonably quickly
     * - Should be idempotent (safe to retry)
     *
     * @param event The event to handle
     * @throws Any exception will be logged and not propagated
     */
    suspend fun handle(event: T)
}
