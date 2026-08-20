@file:Suppress("unused")

package io.github.darkryh.katalyst.analysis.fixtures.app

import io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.github.darkryh.katalyst.ktor.websocket.katalystWebSockets
import io.ktor.server.application.Application
import io.ktor.server.routing.Route

// Genuine entrypoints: each calls a Katalyst DSL function, so analysis (like the runtime) must
// recognise them via bytecode even though nothing in this module references them.

fun Route.greetingRoutes() = katalystRouting { }

fun Application.greetingMiddleware() = katalystMiddleware { }

fun Route.greetingWebSockets() = katalystWebSockets { }

fun Application.greetingExceptionHandlers() = katalystExceptionHandler { }

// A decoy: it has a Ktor receiver and a route-ish name but calls no DSL, so it must NOT be
// discovered and should instead raise an INVALID_DSL_SIGNATURE diagnostic.
fun Route.forgottenRoutes() {
    // no katalyst* DSL call here
}
