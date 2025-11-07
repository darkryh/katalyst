package com.ead.katalyst.example.api

import com.ead.katalyst.example.domain.User
import kotlinx.serialization.Serializable

/**
 * Request payload for creating a new user.
 */
@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String
)

/**
 * Simple response wrapper that hides internal persistence details.
 */
@Serializable
data class UserResponse(
    val id: Long,
    val name: String,
    val email: String
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id,
            name = user.name,
            email = user.email
        )
    }
}
