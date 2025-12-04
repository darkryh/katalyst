package com.ead.boshi.smtp.exception

import io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond

class SmtpTestException : Exception("test")

//just testing purpose
@Suppress("unused")
fun Application.smtpTestException() = katalystExceptionHandler {
    exception<SmtpTestException> { call, exception ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Email validation failed", "details" to exception.message)
        )
    }
}