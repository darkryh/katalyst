package com.ead.katalyst.example.infra.mappers

import com.ead.katalyst.example.domain.User
import com.ead.katalyst.example.infra.database.entities.UserEntity

fun UserEntity.toUser(): User = User(
    id = id!!,
    name = name,
    email = email,
    active = active
)
