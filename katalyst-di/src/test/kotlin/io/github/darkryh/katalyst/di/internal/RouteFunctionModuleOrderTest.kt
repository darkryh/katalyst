package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.di.registry.RegistryManager
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class RouteFunctionModuleOrderTest {

    @BeforeTest
    fun setUp() {
        startKoin { }
    }

    @AfterTest
    fun tearDown() {
        RegistryManager.resetAll()
        stopKoin()
    }

    @Test
    fun `middleware installs before routing regardless of naming`() {
        val registrar = AutoBindingRegistrar(
            koin = GlobalContext.get(),
            scanPackages = arrayOf("io.github.darkryh.katalyst.di.internal")
        )

        registrar.registerRouteFunctions()

        val modules = KtorModuleRegistry.consume().sortedBy { it.order }

        assertEquals(2, modules.size)
        assertEquals(listOf(-50, 0), modules.map { it.order })
    }
}

@Suppress("unused")
fun Application.configureFilters() = katalystMiddleware { }

@Suppress("unused")
fun Route.exposeRoutes() = katalystRouting { }
