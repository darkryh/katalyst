package com.ead.katalyst.events.bus

import com.ead.katalyst.events.DomainEvent

class EventPublishingException(event : DomainEvent, val failures: List<HandlerFailure> = emptyList(),) : Exception()