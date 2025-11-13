package com.ead.katalyst.example.infra.mappers

import com.ead.katalyst.example.domain.AuthAccount
import com.ead.katalyst.example.domain.UserProfile
import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.entities.UserProfileEntity

fun AuthAccountEntity.toDomain() = AuthAccount(
    id = requireNotNull(id),
    email = email,
    passwordHash = passwordHash,
    createdAtMillis = createdAtMillis,
    lastLoginAtMillis = lastLoginAtMillis,
    status = status
)

fun UserProfileEntity.toDomain() = UserProfile(
    id = requireNotNull(id),
    accountId = accountId,
    displayName = displayName,
    bio = bio,
    avatarUrl = avatarUrl
)
