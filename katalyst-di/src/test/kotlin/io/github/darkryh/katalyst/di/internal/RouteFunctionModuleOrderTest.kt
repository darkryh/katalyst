package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.client.request.get as clientGet
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteFunctionModuleOrderTest {
    private lateinit var engine: TestBeanEngine

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()
        engine.registerInstance(RouteInjectedService("resolved-route-dependency"), RouteInjectedService::class)
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        engine.stop()
    }

    @Test
    fun `middleware installs before routing regardless of naming`() {
        val registrar = AutoBindingRegistrar(
            container = engine.container,
            beanEngine = engine,
            scanPackages = arrayOf("io.github.darkryh.katalyst.di.internal")
        )

        registrar.registerRouteFunctions()

        val modules = KtorModuleRegistry.consume().sortedBy { it.order }

        assertEquals(3, modules.size)
        assertEquals(listOf(-50, 0, 0), modules.map { it.order })
    }

    @Test
    fun `route function installation injects service parameters`() = testApplication {
        val registrar = AutoBindingRegistrar(
            container = engine.container,
            beanEngine = engine,
            scanPackages = arrayOf("io.github.darkryh.katalyst.di.internal")
        )

        registrar.registerRouteFunctions()
        application {
            KtorModuleRegistry.consume()
                .sortedBy { it.order }
                .forEach { it.install(this) }
        }

        val response = client.clientGet("/injected-route")

        assertEquals("resolved-route-dependency", response.bodyAsText())
    }
}

@Suppress("unused")
fun Application.configureFilters() = katalystMiddleware { }

@Suppress("unused")
fun Route.exposeRoutes() = katalystRouting { }

@Suppress("unused")
fun Route.injectedRoutes(service: RouteInjectedService) = katalystRouting {
    get("/injected-route") {
        call.respondText(service.message)
    }
}

class RouteInjectedService(val message: String)
