package com.ead.katalyst.example.infra.database.entities

import com.ead.katalyst.repositories.Identifiable

class UserEntity(
    override val id: Long? = null,
    val name: String,
    val email: String,
    val active: Boolean = true
) : Identifiable<Long>