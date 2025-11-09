package com.ead.katalyst.example.config

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import java.util.*

/**
 * Correlation ID Middleware
 *
 * Automatically discovered and installed by Katalyst framework.
 * Assigns or retrieves a correlation ID for request tracing across distributed systems.
 *
 * **Automatic Features:**
 * - Generates unique correlation ID for each request
 * - Can retrieve from X-Correlation-ID header if provided
 * - Available via call.callId throughout request lifecycle
 * - Useful for distributed tracing and logging
 *
 * **Usage:**
 * In route handlers:
 * ```kotlin
 * post {
 *     val correlationId = call.callId ?: "unknown"
 *     logger.info("Request correlation: $correlationId")
 * }
 * ```
 *
 * Client-side:
 * ```
 * curl -H "X-Correlation-ID: abc-123" http://localhost:8080/api/users
 * ```
 */
@Suppress("unused")
fun Application.correlationIdMiddleware() = katalystMiddleware {
    install(CallId) {
        header("X-Correlation-ID")
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }
}
