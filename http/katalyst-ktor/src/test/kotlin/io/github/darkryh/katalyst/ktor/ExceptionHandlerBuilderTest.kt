package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.ktor.builder.ExceptionHandlerRegistry
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

    @Test
    fun `handlers registered on one application do not leak into another application`() {
        // Application A registers a handler.
        testApplication {
            application {
                katalystExceptionHandler {
                    exception<IllegalStateException> { call, cause ->
                        call.respond(HttpStatusCode.BadRequest, cause.message ?: "bad-request")
                    }
                }

                assertEquals(1, ExceptionHandlerRegistry.handlerCount(this))
            }

            client.get("/")
        }

        // Application B is a completely separate Application instance and must start
        // with a clean slate: it must NOT see the handler registered by Application A.
        testApplication {
            application {
                assertEquals(0, ExceptionHandlerRegistry.handlerCount(this))
            }

            client.get("/")
        }
    }

    @Test
    fun `multiple katalystExceptionHandler calls on the same application install together once`() = testApplication {
        application {
            // Simulates two different files each registering their own handler on the
            // same Application; both must be collected and installed in a single
            // StatusPages installation (no DuplicatePluginException).
            katalystExceptionHandler {
                exception<IllegalStateException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, cause.message ?: "bad-request")
                }
            }
            katalystExceptionHandler {
                exception<IllegalArgumentException> { call, cause ->
                    call.respond(HttpStatusCode.UnprocessableEntity, cause.message ?: "invalid")
                }
            }

            assertEquals(2, ExceptionHandlerRegistry.handlerCount(this))

            routing {
                get("/boom-state") { throw IllegalStateException("boom-state") }
                get("/boom-arg") { throw IllegalArgumentException("boom-arg") }
            }
        }

        val stateResponse = client.get("/boom-state")
        assertEquals(HttpStatusCode.BadRequest, stateResponse.status)
        assertEquals("boom-state", stateResponse.bodyAsText())

        val argResponse = client.get("/boom-arg")
        assertEquals(HttpStatusCode.UnprocessableEntity, argResponse.status)
        assertEquals("boom-arg", argResponse.bodyAsText())
    }
}
