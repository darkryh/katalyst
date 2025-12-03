package io.github.darkryh.katalyst.example.api.exception_handler

import io.github.darkryh.katalyst.example.api.dto.ErrorResponse
import io.github.darkryh.katalyst.example.config.httpConfigMiddleware
import io.github.darkryh.katalyst.example.domain.exception.TestException
import io.github.darkryh.katalyst.example.domain.exception.UserExampleValidationException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class ExceptionHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `exception handlers return typed responses`() = testApplication {
        application {
            httpConfigMiddleware()
            exceptionHandlers()
            routing {
                get("/validation") { throw UserExampleValidationException("invalid") }
                get("/conflict") { throw TestException("conflict") }
                get("/boom") { throw IllegalStateException("boom") }
            }
        }

        val validationResponse = client.get("/validation")
        assertEquals(HttpStatusCode.BadRequest, validationResponse.status)
        val validationBody = json.decodeFromString<ErrorResponse>(validationResponse.bodyAsText())
        assertEquals("VALIDATION_ERROR", validationBody.error)

        val conflictResponse = client.get("/conflict")
        assertEquals(HttpStatusCode.Conflict, conflictResponse.status)
        val conflictBody = json.decodeFromString<ErrorResponse>(conflictResponse.bodyAsText())
        assertEquals("OPERATION_CONFLICT", conflictBody.error)

        val boomResponse = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, boomResponse.status)
        val boomBody = json.decodeFromString<ErrorResponse>(boomResponse.bodyAsText())
        assertEquals("INTERNAL_SERVER_ERROR", boomBody.error)
    }
}
