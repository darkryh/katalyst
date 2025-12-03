package io.github.darkryh.katalyst.example.infra.database.entities

import io.github.darkryh.katalyst.repositories.Identifiable

data class UserProfileEntity(
    override val id: Long? = null,
    val accountId: Long,
    val displayName: String,
    val bio: String? = null,
    val avatarUrl: String? = null
) : Identifiable<Long>
