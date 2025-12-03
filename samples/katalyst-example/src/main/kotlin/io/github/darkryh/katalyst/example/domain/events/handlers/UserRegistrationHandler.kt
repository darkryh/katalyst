package io.github.darkryh.katalyst.example.domain.events.handlers

import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.example.domain.events.UserRegisteredEvent
import io.github.darkryh.katalyst.example.service.UserProfileService

@Suppress("unused")
class UserRegistrationHandler(
    private val userProfileService: UserProfileService
) : EventHandler<UserRegisteredEvent> {
    override val eventType = UserRegisteredEvent::class

    override suspend fun handle(event: UserRegisteredEvent) {
        userProfileService.createProfileForAccount(
            accountId = event.accountId,
            displayName = event.displayName
        )
    }
}
