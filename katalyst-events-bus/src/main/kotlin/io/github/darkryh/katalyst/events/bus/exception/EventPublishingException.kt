package io.github.darkryh.katalyst.events.bus.exception

import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.bus.HandlerFailure

class EventPublishingException(event : DomainEvent, val failures: List<HandlerFailure> = emptyList(),) : Exception()