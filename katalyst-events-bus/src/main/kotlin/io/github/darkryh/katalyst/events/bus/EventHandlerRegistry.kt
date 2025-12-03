package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Injectable registry for event handlers.
 *
 * The registry stores handlers discovered during application startup.
 * Unlike the global EventHandlerRegistry, this one is injected through DI,
 * making it testable and mockable.
 *
 * **Usage:**
 *
 * Handlers are discovered and registered during DI bootstrap:
 *
 * ```kotlin
 * // Handler is discovered and auto-registered
 * @Component
 * class UserCreatedHandler : EventHandler<UserCreatedEvent> { ... }
 *
 * // Later, you can query the registry
 * val registry = get<EventHandlerRegistry>()
 * val allHandlers = registry.getAllHandlers()
 * ```
 *
 * **Thread Safety:**
 * This registry is thread-safe. Multiple threads can register and query
 * handlers concurrently.
 */
interface EventHandlerRegistry {
    /**
     * Register an event handler.
     *
     * @param handler The handler to register
     */
    fun <T : DomainEvent> register(handler: EventHandler<T>)

    /**
     * Get all registered handlers.
     *
     * @return Immutable list of all registered handlers
     */
    fun getAllHandlers(): List<EventHandler<*>>

    /**
     * Get handlers for a specific event type.
     *
     * @param eventType The event class to get handlers for
     * @return List of handlers that handle this event type
     */
    fun <T : DomainEvent> getHandlers(eventType: KClass<T>): List<EventHandler<T>>

    /**
     * Get count of registered handlers.
     *
     * @return Total number of registered handlers
     */
    fun size(): Int

    /**
     * Clear all registered handlers.
     *
     * **Warning:** This should only be called in testing/cleanup.
     * In production, handlers should remain registered for the lifetime of the app.
     */
    fun clear()
}

/**
 * In-memory thread-safe implementation of EventHandlerRegistry.
 *
 * Stores handlers in a CopyOnWriteArrayList for thread-safe concurrent access.
 *
 * **Thread Safety:**
 * - Safe for concurrent reads and writes
 * - Uses CopyOnWriteArrayList internally
 * - Suitable for multi-threaded applications
 */
class InMemoryEventHandlerRegistry : EventHandlerRegistry {
    private val handlers = CopyOnWriteArrayList<EventHandler<*>>()

    override fun <T : DomainEvent> register(handler: EventHandler<T>) {
        handlers.add(handler)
    }

    override fun getAllHandlers(): List<EventHandler<*>> {
        return handlers.toList()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : DomainEvent> getHandlers(eventType: KClass<T>): List<EventHandler<T>> {
        return handlers.filter { it.eventType == eventType } as List<EventHandler<T>>
    }

    override fun size(): Int = handlers.size

    override fun clear() {
        handlers.clear()
    }
}

/**
 * Global handler registry for component discovery during bootstrap.
 *
 * This is a temporary registry used only during application startup.
 * Handlers are discovered and stored here, then moved to the injectable
 * EventHandlerRegistry before the bus starts accepting events.
 *
 * **Important:**
 * - This is a global singleton (not injectable)
 * - Should only be used during component discovery
 * - Handlers are consumed (moved) from this registry
 * - Do not use directly in application code
 *
 * **Thread Safety:**
 * This uses CopyOnWriteArrayList internally for thread-safe operations.
 */
object GlobalEventHandlerRegistry {
    private val registry = InMemoryEventHandlerRegistry()

    /**
     * Register a handler during component discovery.
     *
     * @param handler The handler to register
     */
    fun register(handler: EventHandler<*>) {
        registry.register(handler)
    }

    /**
     * Get all handlers and clear the registry.
     *
     * This should be called once during DI bootstrap to move handlers
     * to the injectable registry before the event bus starts.
     *
     * @return List of all registered handlers
     */
    fun consumeAll(): List<EventHandler<*>> {
        val snapshot = registry.getAllHandlers()
        registry.clear()
        return snapshot
    }

    /**
     * Get count of handlers in registry.
     *
     * @return Number of handlers
     */
    fun size(): Int = registry.size()

    /**
     * Clear all handlers (for testing).
     *
     * @return List of handlers that were cleared
     */
    fun clear(): List<EventHandler<*>> {
        return consumeAll()
    }
}
