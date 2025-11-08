package com.ead.katalyst.example.api

import com.ead.katalyst.example.domain.UserProfile
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val id: Long,
    val accountId: Long,
    val displayName: String,
    val bio: String? = null,
    val avatarUrl: String? = null
) {
    companion object {
        fun from(profile: UserProfile) = UserProfileResponse(
            id = profile.id,
            accountId = profile.accountId,
            displayName = profile.displayName,
            bio = profile.bio,
            avatarUrl = profile.avatarUrl
        )
    }
}
