package com.ead.boshi.shared.exception_handler

import com.ead.boshi.shared.exceptions.EmailRequestedNotFound
import com.ead.boshi.shared.exceptions.EmailSpamException
import com.ead.boshi.shared.exceptions.InvalidEmailException
import com.ead.boshi.shared.exceptions.SmtpException
import com.ead.boshi.shared.exceptions.ValidationException
import io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond

@Suppress("unused")
fun Application.sharedExceptionHandler() = katalystExceptionHandler {
    exception<InvalidEmailException> { call, exception ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Invalid email address", "details" to exception.message)
        )
    }

    exception<ValidationException> { call, exception ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Email validation failed", "details" to exception.message)
        )
    }

    exception<SmtpException> { call, exception ->
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "Email submission failed", "details" to exception.message)
        )
    }

    exception<EmailSpamException> { call, exception ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Email rejected: spam detected", "spamScore" to exception.score)
        )
    }

    exception<EmailRequestedNotFound> { call, exception ->
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to "Message not found")
        )
    }

    exception<EmailRequestedNotFound> { call, exception ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Message ID is required")
        )
    }

    exception<Exception> { call, exception ->
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "Internal server error ${exception.message}")
        )
    }
}