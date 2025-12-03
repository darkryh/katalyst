package io.github.darkryh.katalyst.example.infra.mappers

import io.github.darkryh.katalyst.example.domain.AuthAccount
import io.github.darkryh.katalyst.example.domain.UserProfile
import io.github.darkryh.katalyst.example.infra.database.entities.AuthAccountEntity
import io.github.darkryh.katalyst.example.infra.database.entities.UserProfileEntity

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
