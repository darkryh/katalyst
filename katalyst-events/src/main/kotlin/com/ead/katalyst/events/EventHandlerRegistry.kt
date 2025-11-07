package com.ead.katalyst.events

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe registry used during auto-discovery to collect [EventHandler] instances
 * before the event bus is ready to subscribe them.
 */
object EventHandlerRegistry {
    private val handlers = CopyOnWriteArrayList<EventHandler<*>>()

    fun register(handler: EventHandler<*>) {
        handlers += handler
    }

    fun consume(): List<EventHandler<*>> {
        val snapshot = handlers.toList()
        handlers.clear()
        return snapshot
    }
}
