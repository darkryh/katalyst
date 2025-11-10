package com.ead.katalyst.example.config

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*

/**
 * HTTP configuration with JSON serialization.
 */
@Suppress("unused")
fun Application.httpConfigMiddleware() = katalystMiddleware {
    install(ContentNegotiation) {
        json()
    }
}
