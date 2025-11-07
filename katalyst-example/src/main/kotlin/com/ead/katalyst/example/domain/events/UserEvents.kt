package com.ead.katalyst.example.domain.events

import com.ead.katalyst.events.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

sealed interface UserAuditEvent : DomainEvent {
    data class Created(
        val userId: Long,
        val email: String
    ) : UserAuditEvent

    data class GetData(
        val userId: Long,
        val email: String
    ) : UserAuditEvent
}

data class UserNotificationEvent(
    val email: String,
    val name: String
) : DomainEvent
