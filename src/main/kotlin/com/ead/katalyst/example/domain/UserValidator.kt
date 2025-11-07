package com.ead.katalyst.example.domain

import com.ead.katalyst.components.Component
import com.ead.katalyst.example.api.CreateUserRequest
import com.ead.katalyst.example.domain.exception.UserExampleValidationException

class UserValidator : Component {

    fun validate(request: CreateUserRequest) {
        val errors = buildList {
            if (request.name.isBlank()) add("Name must not be blank")
            if (!request.email.contains("@")) add("Email must contain '@'")
        }
        if (errors.isNotEmpty()) {
            throw UserExampleValidationException(errors.joinToString("; "))
        }
    }
}