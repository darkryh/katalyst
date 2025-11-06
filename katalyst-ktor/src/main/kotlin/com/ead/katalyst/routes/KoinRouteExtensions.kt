package com.ead.katalyst.routes

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.ParametersDefinition
import kotlin.LazyThreadSafetyMode

/**
 * Get the Koin instance from the global context.
 *
 * Katalyst initializes Koin globally during startup with initializeKoinStandalone(),
 * which registers all components (services, repositories, validators, etc.) into
 * GlobalContext. Routes access this global context directly.
 *
 * This is more reliable than using the Ktor Koin plugin, which can create
 * separate contexts and cause dependency resolution failures.
 */
fun Application.getKoinInstance(): org.koin.core.Koin {
    // Always use the global Koin context which has all registered components
    // from the Katalyst DI initialization phase
    return GlobalContext.get()
}

/**
 * Lazily resolve a dependency from Koin inside a routing tree.
 */
inline fun <reified T : Any> Route.inject(noinline parameters: ParametersDefinition? = null): Lazy<T> =
    lazy(LazyThreadSafetyMode.NONE) { application.getKoinInstance().get(parameters = parameters) }

/**
 * Resolve a dependency immediately from within a request handler.
 */
inline fun <reified T : Any> ApplicationCall.inject(noinline parameters: ParametersDefinition? = null): T =
    application.getKoinInstance().get(parameters = parameters)

/**
 * Accessor for the Koin container from an [Application].
 */
fun Application.koin() = getKoinInstance()
