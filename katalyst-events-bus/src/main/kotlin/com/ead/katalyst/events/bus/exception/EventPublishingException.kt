package com.ead.katalyst.events.bus.exception

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.bus.HandlerFailure

class EventPublishingException(event : DomainEvent, val failures: List<HandlerFailure> = emptyList(),) : Exception()