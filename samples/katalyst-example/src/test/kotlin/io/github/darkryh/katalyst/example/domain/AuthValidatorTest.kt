package io.github.darkryh.katalyst.example.domain

import io.github.darkryh.katalyst.example.api.dto.LoginRequest
import io.github.darkryh.katalyst.example.api.dto.RegisterRequest
import io.github.darkryh.katalyst.example.domain.exception.UserExampleValidationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthValidatorTest {
    private val validator = AuthValidator()

    @Test
    fun `accepts valid register request`() {
        validator.validate(
            RegisterRequest(
                email = "valid@example.com",
                password = "Sup3rSafe!",
                displayName = "Valid User"
            )
        )
    }

    @Test
    fun `aggregates register validation errors`() {
        val exception = assertFailsWith<UserExampleValidationException> {
            validator.validate(
                RegisterRequest(
                    email = "invalid",
                    password = "short",
                    displayName = ""
                )
            )
        }

        val message = exception.message ?: ""
        assertTrue(message.contains("Email must be valid"))
        assertTrue(message.contains("Password must be at least 8 characters long"))
        assertTrue(message.contains("Display name must not be blank"))
    }

    @Test
    fun `login requires email and password`() {
        assertFailsWith<UserExampleValidationException> {
            validator.validate(LoginRequest(email = "", password = ""))
        }
    }
}
