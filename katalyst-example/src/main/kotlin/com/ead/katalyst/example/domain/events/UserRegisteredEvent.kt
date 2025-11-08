package com.ead.katalyst.example.domain.events

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata

data class UserRegisteredEvent(
    val accountId: Long,
    val email: String,
    val displayName: String
) : DomainEvent {
    override fun getMetadata(): EventMetadata = EventMetadata(eventType = "UserRegisteredEvent")
}
