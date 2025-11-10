package com.ead.katalyst.example.config

import com.ead.katalyst.ktor.middleware.katalystMiddleware
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
