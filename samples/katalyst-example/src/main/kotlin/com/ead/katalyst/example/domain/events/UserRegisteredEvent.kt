package com.ead.katalyst.example.domain.events

import com.ead.katalyst.events.DomainEvent

data class UserRegisteredEvent(
    val accountId: Long,
    val email: String,
    val displayName: String
) : DomainEvent