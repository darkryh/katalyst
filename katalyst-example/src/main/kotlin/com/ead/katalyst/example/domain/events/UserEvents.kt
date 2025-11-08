package com.ead.katalyst.example.domain.events

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata

sealed class UserAuditEvent : DomainEvent {
    data class Created(
        val userId: Long,
        val email: String
    ) : UserAuditEvent() {
        override fun getMetadata(): EventMetadata = EventMetadata(eventType = "UserAuditEventCreated")
        override fun eventType(): String = "UserAuditEventCreated"
    }

    data class GetData(
        val userId: Long,
        val email: String
    ) : UserAuditEvent() {
        override fun getMetadata(): EventMetadata = EventMetadata(eventType = "UserAuditEventGetData")
        override fun eventType(): String = "UserAuditEventGetData"
    }
}

data class UserNotificationEvent(
    val email: String,
    val name: String
) : DomainEvent {
    override fun getMetadata(): EventMetadata = EventMetadata(eventType = "UserNotificationEvent")
    override fun eventType(): String = "UserNotificationEvent"
}
