package com.ead.katalyst.example.config.security

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import com.ead.katalyst.example.config.JwtSettingsService
import io.ktor.server.application.Application
import org.koin.core.context.GlobalContext

@Suppress("unused")
fun Application.securityMiddleware() = katalystMiddleware {
    // Get JwtSettingsService from DI container (auto-discovered and registered)
    val jwtSettings = GlobalContext.get().get<JwtSettingsService>()
    jwtSettings.configure(this@securityMiddleware)
}
