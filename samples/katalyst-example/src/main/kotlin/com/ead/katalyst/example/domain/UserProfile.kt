package com.ead.katalyst.example.domain

data class UserProfile(
    val id: Long,
    val accountId: Long,
    val displayName: String,
    val bio: String? = null,
    val avatarUrl: String? = null
)
