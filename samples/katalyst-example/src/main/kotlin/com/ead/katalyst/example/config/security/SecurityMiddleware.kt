package com.ead.katalyst.example.config.security

import com.ead.katalyst.example.config.JwtSettingsService
import com.ead.katalyst.ktor.extension.ktInject
import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.*

@Suppress("unused")
fun Application.securityMiddleware() = katalystMiddleware {
    // Get JwtSettingsService from DI container (auto-discovered and registered)
    val jwtSettings by ktInject<JwtSettingsService>()
    jwtSettings.configure(this@securityMiddleware)
}
