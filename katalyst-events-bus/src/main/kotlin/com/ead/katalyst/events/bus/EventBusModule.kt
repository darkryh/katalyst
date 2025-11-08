package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

/**
 * Koin DI module for katalyst-events-bus.
 *
 * Registers the ApplicationEventBus and related components.
 *
 * **Usage:**
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         eventBusModule()
 *     )
 * }
 *
 * // Later in the app:
 * val bus: EventBus = get()
 * ```
 */
fun eventBusModule(): Module = module {
    val logger = LoggerFactory.getLogger("EventBusModule")

    logger.info("Configuring EventBus module")

    // Register event handler registry
    single<EventHandlerRegistry> {
        logger.debug("Creating InMemoryEventHandlerRegistry singleton")
        InMemoryEventHandlerRegistry()
    }

    // Register the main EventBus implementation first
    single<EventBus> {
        logger.debug("Creating ApplicationEventBus singleton")
        ApplicationEventBus(
            // Uses default Dispatchers.Default for handler execution
            // and empty interceptors list (can be customized in config)
        )
    }

    // Register event topology for wiring handlers to bus
    single<EventTopology> {
        logger.debug("Creating EventTopology singleton")
        EventTopology(
            eventBus = get(),
            registry = get()
        )
    }

    logger.info("EventBus module configured successfully")
}
