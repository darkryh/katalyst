package io.github.darkryh.katalyst.example.config.security

import io.github.darkryh.katalyst.example.config.JwtSettingsService
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.*

@Suppress("unused")
fun Application.securityMiddleware() = katalystMiddleware {
    // Get JwtSettingsService from DI container (auto-discovered and registered)
    val jwtSettings by ktInject<JwtSettingsService>()
    jwtSettings.configure(this@securityMiddleware)
}
