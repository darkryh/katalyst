package com.ead.katalyst.example.routes

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*

fun Application.configureHttp() {
    install(ContentNegotiation) {
        json()
    }
}
