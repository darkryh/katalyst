package com.ead.katalyst.example.domain

data class AuthAccount(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val createdAtMillis: Long,
    val lastLoginAtMillis: Long?,
    val status: String
)
