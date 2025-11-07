package com.ead.katalyst.events

import com.ead.katalyst.events.EventMessagingPublisher
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Provides Koin definitions for the events subsystem.
 */
fun eventModule(options: EventModuleOptions): Module = module {
    single<EventBus> {
        ApplicationEventBus(
            dispatcher = options.dispatcher,
            messagingPublisher = options.messagingPublisher
        )
    }

    single { EventTopology(eventBus = get()) }
}

/**
 * Central registry that wires handlers into the active [EventBus] on demand.
 */
class EventTopology(
    private val eventBus: EventBus
) {
    private val logger = LoggerFactory.getLogger(EventTopology::class.java)

    fun registerHandlers(handlers: List<EventHandler<*>>) {
        if (handlers.isEmpty()) {
            logger.info("No event handlers discovered")
            return
        }

        handlers.forEach { handler ->
            @Suppress("UNCHECKED_CAST")
            eventBus.register(handler as EventHandler<out DomainEvent>)
            logger.info("Event handler registered: {}", handler::class.qualifiedName)
        }
    }
}
