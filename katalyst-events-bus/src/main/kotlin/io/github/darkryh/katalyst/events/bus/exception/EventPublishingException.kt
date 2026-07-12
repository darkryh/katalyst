package io.github.darkryh.katalyst.events.bus.exception

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.bus.HandlerFailure

/**
 * Thrown when one or more handlers fail while publishing [event].
 *
 * Retains the failed [event] and the full list of [failures] so callers can
 * inspect which event failed and why. The message summarizes the event
 * (type/id) and each failing handler; the first handler's exception is
 * preserved as the [cause] so the original stack trace is not lost.
 */
class EventPublishingException(
    val event: DomainEvent,
    val failures: List<HandlerFailure> = emptyList(),
) : Exception(buildMessage(event, failures), failures.firstOrNull()?.exception)

private fun buildMessage(event: DomainEvent, failures: List<HandlerFailure>): String {
    val eventDescription = "${event::class.simpleName} (eventId=${event.eventId}, type=${event.eventType()})"
    if (failures.isEmpty()) {
        return "Event publishing failed for $eventDescription"
    }
    val failureSummary = failures.joinToString(separator = "; ") { failure ->
        "${failure.handlerClass} -> ${failure.exception::class.simpleName}: ${failure.exception.message}"
    }
    return "Event publishing failed for $eventDescription: ${failures.size} handler(s) failed [$failureSummary]"
}