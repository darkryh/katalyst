package com.ead.boshi.auth

import com.ead.boshi.auth.config.AuthConfigImpl
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.ktor.server.application.*
import io.ktor.server.auth.*

/**
 * Install Bearer token authentication middleware
 * Validates API key from Authorization Bearer token header
 */
@Suppress("unused")
fun Application.authenticationMiddleware() = katalystMiddleware {
    val apiKeyExpected = AuthConfigImpl.loadConfig().apiKey

    authentication {
        bearer("api-key") {
            realm = "boshi smtp server"

            authenticate { tokenCredential ->
                val apiKeyReceived = tokenCredential.token

                if (apiKeyReceived == apiKeyExpected) {
                    BearerTokenCredential(apiKeyReceived)
                } else {
                    null
                }
            }
        }
    }
}