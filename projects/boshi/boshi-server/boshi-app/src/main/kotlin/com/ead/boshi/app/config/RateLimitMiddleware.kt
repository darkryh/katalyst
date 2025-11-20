package com.ead.boshi.app.config

import com.ead.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

/**
 * Install and configure Ktor's RateLimit plugin
 * Protects email API endpoints from abuse
 */
@Suppress("unused")
fun Application.installRateLimit() = katalystMiddleware {
    val config = RateLimitConfigImpl.loadConfig()

    if (!config.enabled) {
        return@katalystMiddleware
    }

    install(RateLimit) {
        // Global rate limit: 10 requests per second by default
        global {
            rateLimiter(limit = config.requestsPerSecond, refillPeriod = config.refillPeriodSeconds.seconds)
        }

        // Per-route rate limits
        register(RateLimitName("emailSend")) {
            // Email send endpoint: 100 requests per minute
            rateLimiter(limit = config.emailSendLimit, refillPeriod = 60.seconds)
        }

        register(RateLimitName("statusCheck")) {
            // Status check endpoint: 100 requests per minute
            rateLimiter(limit = config.statusCheckLimit, refillPeriod = 60.seconds)
        }
    }
}
