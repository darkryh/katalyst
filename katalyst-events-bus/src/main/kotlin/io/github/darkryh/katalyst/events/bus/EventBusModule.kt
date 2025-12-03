package io.github.darkryh.katalyst.events.bus

import org.koin.core.module.Module
import org.koin.dsl.module
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

    // Register ApplicationEventBus (not exposed as EventBus, used internally)
    single<ApplicationEventBus> {
        logger.debug("Creating ApplicationEventBus singleton")
        ApplicationEventBus(
            // Uses default Dispatchers.Default for handler execution
            // and empty interceptors list (can be customized in config)
        )
    }

    // Register TransactionAwareEventBus as the main EventBus implementation
    // This wrapper automatically defers event publishing until after transaction commits
    single<EventBus> {
        logger.debug("Creating TransactionAwareEventBus (wraps ApplicationEventBus)")
        TransactionAwareEventBus(
            delegate = get<ApplicationEventBus>()
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
