package com.ead.katalyst.events

import java.time.LocalDateTime

/**
 * Base contract for domain events that can be published through the dispatcher.
 */
interface DomainEvent {
    val eventId: String
    val occurredAt: LocalDateTime

    fun eventType(): String = this::class.simpleName ?: "UnknownEvent"
}

/**
 * Handler abstraction for reacting to domain events.
 */
fun interface EventHandler<T : DomainEvent> {
    suspend fun handle(event: T)
}

/**
 * Lightweight event dispatcher that fans-out published events to subscribed handlers.
 */
class EventDispatcher {
    private val handlers = mutableMapOf<Class<*>, MutableList<EventHandler<*>>>()

    fun <T : DomainEvent> subscribe(eventType: Class<T>, handler: EventHandler<T>) {
        @Suppress("UNCHECKED_CAST")
        val group = handlers.getOrPut(eventType) { mutableListOf() } as MutableList<EventHandler<T>>
        group += handler
    }

    suspend fun dispatch(event: DomainEvent) {
        val listeners = handlers[event::class.java] ?: return
        listeners.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            (listener as EventHandler<DomainEvent>).handle(event)
        }
    }
}
