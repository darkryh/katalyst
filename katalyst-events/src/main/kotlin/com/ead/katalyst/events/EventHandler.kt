package com.ead.katalyst.events

import kotlin.reflect.KClass

/**
 * Typed event handler that can react to one or many [DomainEvent] classes.
 *
 * For sealed event hierarchies, set [eventType] to the sealed parent and the event
 * system will automatically register the handler for every leaf subclass.
 */
interface EventHandler<T : DomainEvent> {
    val eventType: KClass<T>
    suspend fun handle(event: T)
}

/**
 * Convenience base class for handlers that don't need additional metadata.
 */
abstract class SimpleEventHandler<T : DomainEvent>(
    override val eventType: KClass<T>
) : EventHandler<T>
