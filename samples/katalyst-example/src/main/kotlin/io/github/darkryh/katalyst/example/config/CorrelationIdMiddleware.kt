package io.github.darkryh.katalyst.example.config

import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import java.util.*

/**
 * Correlation ID middleware for request tracing.
 */
@Suppress("unused")
fun Application.correlationIdMiddleware() = katalystMiddleware {
    install(CallId) {
        header("X-Correlation-ID")
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }
}
