package io.github.darkryh.katalyst.ktor

import io.github.darkryh.katalyst.ktor.middleware.Middleware
import io.github.darkryh.katalyst.ktor.middleware.MiddlewareBuilder
import io.github.darkryh.katalyst.ktor.middleware.MiddlewareResult
import io.github.darkryh.katalyst.ktor.middleware.ktInject
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.koin.core.context.stopKoin
import org.koin.core.context.startKoin
import org.koin.dsl.module

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
    fun `inject resolves dependencies from global koin`() {
        stopKoin()
        val koinApp = startKoin {
            modules(
                module {
                    single { SampleDependency("value") }
                }
            )
        }

        try {
            testApplication {
                application {
                    val builder = MiddlewareBuilder(this)
                    val resolved = builder.ktInject<SampleDependency>()
                    assertSame(koinApp.koin.get<SampleDependency>(), resolved)
                }
            }
        } finally {
            stopKoin()
        }
    }

    private class TestMiddleware(
        private val id: String
    ) : Middleware {
        override suspend fun process(request: ApplicationRequest): MiddlewareResult = MiddlewareResult.Continue
        override fun toString(): String = "TestMiddleware($id)"
    }

    private data class SampleDependency(val id: String)
}
