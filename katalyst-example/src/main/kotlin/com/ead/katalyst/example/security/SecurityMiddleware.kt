package com.ead.katalyst.example.security

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.Application

@Suppress("unused")
fun Application.securityMiddleware() = katalystMiddleware {
    JwtSettings.configure(this@securityMiddleware)
}
