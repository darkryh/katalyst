package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingBuilderTest {

    @Test
    fun `application katalystRouting installs routes even without koin`() = testApplication {
        var executed = false

        application {
            katalystRouting {
                get("/ping") {
                    executed = true
                    call.respondText("pong")
                }
            }
        }

        val response = client.get("/ping")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("pong", response.bodyAsText())
        assertTrue(executed, "Route handler should run on request")
    }

    @Test
    fun `route katalystRouting wraps nested declarations`() = testApplication {
        var nestedExecuted = false

        application {
            routing {
                route("/nest") {
                    katalystRouting {
                        get("/child") {
                            nestedExecuted = true
                            call.respondText("nested")
                        }
                    }
                }
            }
        }

        val response = client.get("/nest/child")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("nested", response.bodyAsText())
        assertTrue(nestedExecuted, "Nested katalystRouting should run inside an existing Route")
    }
}
