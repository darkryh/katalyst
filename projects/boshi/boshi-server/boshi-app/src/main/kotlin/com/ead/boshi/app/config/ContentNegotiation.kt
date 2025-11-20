package com.ead.boshi.app.config

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

@Suppress("unused")
fun Application.contentNegotiation() = katalystMiddleware {
    install(ContentNegotiation) {
        json()
    }
}