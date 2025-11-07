package com.ead.katalyst.example

import com.ead.katalyst.routes.katalystMiddleware
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*


@Suppress("unused")
fun Application.configureManualHttp(/*cannot be injected by params*/) = katalystMiddleware {
    install(ContentNegotiation) {
        json()
    }
}
