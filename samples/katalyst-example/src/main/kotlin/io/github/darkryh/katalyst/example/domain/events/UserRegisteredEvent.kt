package io.github.darkryh.katalyst.example.domain.events

import io.github.darkryh.katalyst.events.DomainEvent

data class UserRegisteredEvent(
    val accountId: Long,
    val email: String,
    val displayName: String
) : DomainEvent