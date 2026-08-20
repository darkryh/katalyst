package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.ktor.middleware.Middleware
import io.github.darkryh.katalyst.ktor.middleware.MiddlewareBuilder
import io.github.darkryh.katalyst.ktor.middleware.MiddlewareResult
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.github.darkryh.katalyst.ktor.middleware.ktInject
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class MiddlewareBuilderTest {

    @Test
    fun `use registers middleware and retains order`() = testApplication {
        application {
            val builder = MiddlewareBuilder(this)
            val first = TestMiddleware("first")
            val second = TestMiddleware("second")

            builder.use(first)
            builder.use(second)

            assertEquals(listOf(first, second), builder.getMiddlewares())
        }
    }

    @Test
    fun `inject resolves dependencies from active container`() {
        val dependency = SampleDependency("value")
        KatalystContainerProvider.set(
            TestKatalystContainer(mapOf(TestKatalystContainer.Key(SampleDependency::class) to dependency))
        )

        try {
            testApplication {
                application {
                    val builder = MiddlewareBuilder(this)
                    val resolved: Lazy<SampleDependency> = builder.ktInject()
                    assertSame(dependency, resolved.value)
                }
            }
        } finally {
            KatalystContainerProvider.reset()
        }
    }

    @Test
    fun `katalystMiddleware executes middleware before and after downstream pipeline`() = testApplication {
        val events = mutableListOf<String>()

        application {
            katalystMiddleware {
                use(
                    object : Middleware {
                        override suspend fun process(request: ApplicationRequest): MiddlewareResult {
                            events += "before:${request.path()}"
                            return MiddlewareResult.Continue
                        }

                        override suspend fun after(call: ApplicationCall) {
                            events += "after:${call.response.status()}"
                        }
                    }
                )
            }

            routing {
                get("/ok") {
                    events += "route"
                    call.respondText("ok")
                }
            }
        }

        val response = client.get("/ok")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
        assertEquals(listOf("before:/ok", "route", "after:200 OK"), events)
    }

    @Test
    fun `katalystMiddleware aborts request before route execution`() = testApplication {
        var routeExecuted = false

        application {
            katalystMiddleware {
                use(
                    object : Middleware {
                        override suspend fun process(request: ApplicationRequest): MiddlewareResult =
                            MiddlewareResult.Abort(HttpStatusCode.Forbidden.value, "blocked")
                    }
                )
            }

            routing {
                get("/blocked") {
                    routeExecuted = true
                    call.respondText("should-not-run")
                }
            }
        }

        val response = client.get("/blocked")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("blocked", response.bodyAsText())
        assertFalse(routeExecuted)
    }

    @Test
    fun `katalystMiddleware can handle downstream exceptions`() = testApplication {
        application {
            katalystMiddleware {
                use(
                    object : Middleware {
                        override suspend fun process(request: ApplicationRequest): MiddlewareResult =
                            MiddlewareResult.Continue

                        override suspend fun onException(call: ApplicationCall, cause: Throwable): Boolean {
                            call.respondText(
                                text = "handled:${cause.message}",
                                status = HttpStatusCode.fromValue(418)
                            )
                            return true
                        }
                    }
                )
            }

            routing {
                get("/boom") {
                    error("boom")
                }
            }
        }

        val response = client.get("/boom")

        assertEquals(HttpStatusCode.fromValue(418), response.status)
        assertEquals("handled:boom", response.bodyAsText())
    }

    @Test
    fun `katalystMiddleware can wrap native Ktor pipeline setup`() = testApplication {
        val events = mutableListOf<String>()

        application {
            katalystMiddleware {
                intercept(ApplicationCallPipeline.Monitoring) {
                    events += "native-before:${call.request.path()}"
                    proceed()
                    events += "native-after:${call.response.status()}"
                }
            }

            routing {
                get("/native") {
                    events += "route"
                    call.respondText("native")
                }
            }
        }

        val response = client.get("/native")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("native", response.bodyAsText())
        assertEquals(listOf("native-before:/native", "route", "native-after:200 OK"), events)
    }

    private class TestMiddleware(
        private val id: String
    ) : Middleware {
        override suspend fun process(request: ApplicationRequest): MiddlewareResult = MiddlewareResult.Continue
        override fun toString(): String = "TestMiddleware($id)"
    }

    private data class SampleDependency(val id: String)
}
