package io.github.darkryh.katalyst.events.bus

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import org.slf4j.LoggerFactory

/**
 * Wires event handlers to the event bus.
 *
 * This component is responsible for:
 * - Taking discovered handlers from component discovery
 * - Registering them with the EventBus
 * - Maintaining a registry of all handlers
 *
 * **Lifecycle:**
 * 1. Created during DI bootstrap
 * 2. Called after component discovery to register found handlers
 * 3. EventBus is ready to accept events after this
 *
 * **Usage (internal - called by DI system):**
 *
 * ```kotlin
 * // During DI bootstrap
 * val discoveredHandlers = GlobalEventHandlerRegistry.consumeAll()
 * val topology = get<EventTopology>()
 * topology.registerHandlers(discoveredHandlers)
 * // Now EventBus is ready
 * ```
 *
 * This ensures handlers are registered BEFORE the first event is published.
 *
 * @param eventBus The event bus to register handlers with
 * @param registry The injectable handler registry to maintain
 */
class EventTopology(
    private val eventBus: EventBus,
    private val registry: EventHandlerRegistry
) {
    private val logger = LoggerFactory.getLogger(EventTopology::class.java)

    /**
     * Register all discovered handlers.
     *
     * This should be called once during DI bootstrap, after component discovery
     * but before the application starts accepting events.
     *
     * **Important:** This must be called BEFORE any events are published.
     * The DI system ensures this order:
     * 1. Create EventBus
     * 2. Discover components (including handlers)
     * 3. Call topology.registerHandlers()
     * 4. Application starts and can publish events
     *
     * @param handlers List of handlers discovered during component discovery
     */
    fun registerHandlers(handlers: List<EventHandler<*>>) {
        if (handlers.isEmpty()) {
            logger.info("No event handlers discovered")
            return
        }

        logger.info("Registering {} event handler(s)", handlers.size)

        var registered = 0
        val failed = mutableListOf<String>()

        for (handler in handlers) {
            val handlerName = handler::class.qualifiedName
                ?: handler::class.simpleName
                ?: handler::class.java.name
            try {
                @Suppress("UNCHECKED_CAST")
                eventBus.register(handler as EventHandler<out DomainEvent>)

                registry.register(handler)
                registered++

                logger.info(
                    "Registered event handler: {} (for event type: {})",
                    handlerName,
                    handler.eventType.qualifiedName
                )
            } catch (e: Exception) {
                failed += handlerName
                logger.error(
                    "Failed to register event handler {}: {}",
                    handlerName,
                    e.message,
                    e
                )
                // Continue registering other handlers even if one fails
            }
        }

        // A dropped handler means an event type silently stops being handled at runtime, so the
        // summary reports what really happened instead of unconditionally claiming success.
        if (failed.isEmpty()) {
            logger.info("Event handler registration completed: {} of {} registered", registered, handlers.size)
        } else {
            logger.error(
                "Event handler registration completed with failures: {} of {} registered, {} failed: {}. " +
                    "Events for the failed handler(s) will not be delivered.",
                registered,
                handlers.size,
                failed.size,
                failed.joinToString()
            )
        }
    }

    /**
     * Get the underlying event handler registry.
     *
     * Useful for querying which handlers are registered (primarily for testing).
     *
     * @return The handler registry
     */
    fun getRegistry(): EventHandlerRegistry = registry
}
