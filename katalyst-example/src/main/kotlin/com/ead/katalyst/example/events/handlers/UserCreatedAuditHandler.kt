package com.ead.katalyst.example.events.handlers

import com.ead.katalyst.example.domain.events.UserAuditEvent
import com.ead.katalyst.example.service.AuditService
import com.ead.katalyst.events.EventHandler

@Suppress("unused")
class UserCreatedAuditHandler(
    private val auditService: AuditService
) : EventHandler<UserAuditEvent> {

    override val eventType = UserAuditEvent::class

    override suspend fun handle(event: UserAuditEvent) {
        when (event) {
            is UserAuditEvent.Created -> auditService.logUserCreated(
                userId = event.userId.toString(),
                email = event.email
            )
            is UserAuditEvent.GetData -> auditService.logUserInfo(
                userId = event.userId.toString(),
                email = event.email
            )
        }
    }
}
