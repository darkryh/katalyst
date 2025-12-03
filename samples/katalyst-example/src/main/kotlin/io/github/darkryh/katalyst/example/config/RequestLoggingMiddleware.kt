package io.github.darkryh.katalyst.example.config

import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.util.logging.*

/**
 * Request logging middleware.
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
