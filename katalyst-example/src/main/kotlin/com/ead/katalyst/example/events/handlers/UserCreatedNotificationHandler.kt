package com.ead.katalyst.example.events.handlers

import com.ead.katalyst.example.domain.events.UserNotificationEvent
import com.ead.katalyst.example.service.NotificationService
import com.ead.katalyst.events.EventHandler

@Suppress("unused")
class UserCreatedNotificationHandler(
    private val notificationService: NotificationService
) : EventHandler<UserNotificationEvent> {
    override val eventType = UserNotificationEvent::class

    override suspend fun handle(event: UserNotificationEvent) {
        notificationService.notifyUserCreated(
            email = event.email,
            name = event.name
        )
    }
}