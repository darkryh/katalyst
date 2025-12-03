package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionHandlerBuilderTest {

    @Test
    fun `katalystExceptionHandler maps exceptions to responses`() = testApplication {
        application {
            katalystExceptionHandler {
                exception<IllegalStateException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, cause.message ?: "bad-request")
                }
            }

            routing {
                get("/boom") {
                    throw IllegalStateException("boom")
                }
            }
        }

        val response = client.get("/boom")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("boom", response.bodyAsText())
    }
}
