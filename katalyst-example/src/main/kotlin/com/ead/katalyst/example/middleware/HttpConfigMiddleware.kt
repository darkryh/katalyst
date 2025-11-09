package com.ead.katalyst.example.middleware

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*

/**
 * HTTP Configuration Middleware
 *
 * Automatically discovered and installed by Katalyst framework.
 * Configures JSON serialization/deserialization for the application.
 *
 * **Automatic Features:**
 * - ContentNegotiation is installed automatically
 * - JSON serializer configured for request/response handling
 * - No manual installation needed - just define the function and it's discovered
 *
 * **Usage:**
 * The katalystMiddleware DSL automatically discovers this function at:
 * - Application.kt calls scanPackages("com.ead.katalyst.example")
 * - Katalyst scans and discovers all katalystMiddleware functions
 * - Functions are executed in declaration order during application startup
 */
@Suppress("unused")
fun Application.httpConfigMiddleware() = katalystMiddleware {
    install(ContentNegotiation) {
        json()
    }
}
