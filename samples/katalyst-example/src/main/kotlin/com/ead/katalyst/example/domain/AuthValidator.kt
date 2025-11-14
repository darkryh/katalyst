package com.ead.katalyst.example.domain

import com.ead.katalyst.core.component.Component
import com.ead.katalyst.example.api.dto.LoginRequest
import com.ead.katalyst.example.api.dto.RegisterRequest
import com.ead.katalyst.example.domain.exception.UserExampleValidationException

class AuthValidator : Component {
    fun validate(request: RegisterRequest) {
        val errors = buildList {
            if (request.email.isBlank() || !request.email.contains("@")) {
                add("Email must be valid")
            }
            if (request.password.length < 8) {
                add("Password must be at least 8 characters long")
            }
            if (request.displayName.isBlank()) {
                add("Display name must not be blank")
            }
        }
        if (errors.isNotEmpty()) {
            throw UserExampleValidationException(errors.joinToString("; "))
        }
    }

    fun validate(request: LoginRequest) {
        if (request.email.isBlank() || request.password.isBlank()) {
            throw UserExampleValidationException("Email and password are required")
        }
    }
}
