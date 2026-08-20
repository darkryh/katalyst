package io.github.darkryh.katalyst.testing.core.events

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test event handler that records published domain events of a single type.
 *
 * Register through `KatalystTestEnvironmentBuilder.eventProbe(...)` so the probe is
 * wired before Katalyst's event topology is finalized.
 */
class EventProbe<T : DomainEvent>(
    override val eventType: KClass<T>
) : EventHandler<T> {
    private val lock = Any()
    private val capturedEvents = mutableListOf<T>()

    val events: List<T>
        get() = synchronized(lock) { capturedEvents.toList() }

    val count: Int
        get() = synchronized(lock) { capturedEvents.size }

    val first: T?
        get() = synchronized(lock) { capturedEvents.firstOrNull() }

    val last: T?
        get() = synchronized(lock) { capturedEvents.lastOrNull() }

    override suspend fun handle(event: T) {
        synchronized(lock) {
            capturedEvents += event
        }
    }

    fun clear() = synchronized(lock) {
        capturedEvents.clear()
    }

    fun assertPublished(count: Int = 1) {
        assertEquals(count, this.count, "Expected $count published event(s), but captured ${this.count}")
    }

    fun assertNotPublished() {
        assertEquals(0, count, "Expected no published events, but captured $count")
    }

    fun assertAny(predicate: (T) -> Boolean) {
        assertTrue(events.any(predicate), "Expected at least one captured event to match predicate")
    }
}

inline fun <reified T : DomainEvent> eventProbe(): EventProbe<T> =
    EventProbe(T::class)
