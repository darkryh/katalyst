package com.ead.katalyst.example.infra.database.entities

import com.ead.katalyst.repositories.Identifiable

data class AuthAccountEntity(
    override val id: Long? = null,
    val email: String,
    val passwordHash: String,
    val createdAtMillis: Long,
    val lastLoginAtMillis: Long? = null
) : Identifiable<Long>
