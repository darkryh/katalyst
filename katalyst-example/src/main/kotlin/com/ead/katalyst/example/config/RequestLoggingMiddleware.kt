package com.ead.katalyst.example.config

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.util.logging.*

/**
 * Request Logging Middleware
 *
 * Automatically discovered and installed by Katalyst framework.
 * Logs incoming HTTP requests with method, path, and timing information.
 *
 * **Automatic Features:**
 * - Installed as part of the application lifecycle
 * - Logs request details without manual intervention
 * - No configuration needed - just define and it's discovered
 *
 * **Usage:**
 * Example output:
 * ```
 * [REQUEST] GET /api/users
 * [REQUEST] POST /api/users (206ms)
 * ```
 */
@Suppress("unused")
fun Application.requestLoggingMiddleware() = katalystMiddleware {
    val logger = KtorSimpleLogger("RequestLogger")

    install(CallLogging) {
        logger.info("Request logging middleware installed")
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        val startTime = System.currentTimeMillis()
        val method = call.request.httpMethod.value
        val path = call.request.path()

        logger.info("[$method] $path")

        proceed()

        val duration = System.currentTimeMillis() - startTime
        logger.info("[$method] $path completed in ${duration}ms")
    }
}
