package com.ead.boshi.auth

import com.ead.boshi.auth.config.models.AuthConfig
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.github.darkryh.katalyst.ktor.middleware.ktInject
import io.ktor.server.application.*
import io.ktor.server.auth.*

/**
 * Install Bearer token authentication middleware
 * Validates API key from Authorization Bearer token header
 */
fun Application.authenticationMiddleware() = katalystMiddleware {
    val authConfig by ktInject<AuthConfig>()

    authentication {
        bearer("api-key") {
            realm = "boshi smtp server"

            authenticate { tokenCredential ->
                val apiKeyReceived = tokenCredential.token

                if (apiKeyReceived == authConfig.apiKey) {
                    BearerTokenCredential(apiKeyReceived)
                } else {
                    null
                }
            }
        }
    }
}
