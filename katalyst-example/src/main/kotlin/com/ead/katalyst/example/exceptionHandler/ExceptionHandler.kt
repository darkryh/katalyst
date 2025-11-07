package com.ead.katalyst.example.exceptionHandler

import com.ead.katalyst.example.domain.exception.TestException
import com.ead.katalyst.example.domain.exception.UserExampleValidationException
import com.ead.katalyst.routes.katalystExceptionHandler
import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.response.*

@Suppress("unused")
fun Application.exceptionHandlers() = katalystExceptionHandler {
    exception<UserExampleValidationException> { call, exception ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "VALIDATION_ERROR", "message" to exception.message)
        )
    }
    exception<TestException> { call, exception ->
        call.respond(
            HttpStatusCode.Conflict,
            mapOf("error_example" to "EXAMPLE ERROR", "message" to exception.message)
        )
    }
}