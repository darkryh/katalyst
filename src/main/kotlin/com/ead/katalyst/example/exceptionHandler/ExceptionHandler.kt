package com.ead.katalyst.example.exceptionHandler

import com.ead.katalyst.example.domain.UserExampleValidationException
import com.ead.katalyst.routes.katalystExceptionHandler
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Suppress("unused")
fun Route.exceptionHandlers() = katalystExceptionHandler {
    exception<UserExampleValidationException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "VALIDATION_ERROR", "message" to cause.message)
        )
    }
}