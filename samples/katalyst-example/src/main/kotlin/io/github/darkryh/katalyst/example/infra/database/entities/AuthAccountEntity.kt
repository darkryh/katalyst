package io.github.darkryh.katalyst.example.infra.database.entities

import io.github.darkryh.katalyst.repositories.Identifiable

data class AuthAccountEntity(
    override val id: Long? = null,
    val email: String,
    val passwordHash: String,
    val createdAtMillis: Long,
    val lastLoginAtMillis: Long? = null,
    val status: String = "active"
) : Identifiable<Long>
