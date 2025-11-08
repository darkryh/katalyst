package com.ead.katalyst.example.events.handlers

import com.ead.katalyst.example.domain.events.UserRegisteredEvent
import com.ead.katalyst.example.service.UserProfileService
import com.ead.katalyst.events.EventHandler

@Suppress("unused")
class UserRegistrationHandler(
    private val userProfileService: UserProfileService
) : EventHandler<UserRegisteredEvent> {
    override val eventType = UserRegisteredEvent::class

    override suspend fun handle(event: UserRegisteredEvent) {
        println("handler event called UserRegisteredEvent")
        userProfileService.createProfileForAccount(
            accountId = event.accountId,
            displayName = event.displayName
        )
    }
}
