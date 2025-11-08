package com.ead.katalyst.example.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

object JwtSettings {
    private val secret = System.getenv("JWT_SECRET") ?: "local-secret"
    private val issuer = System.getenv("JWT_ISSUER") ?: "katalyst-example"
    private val audience = System.getenv("JWT_AUDIENCE") ?: "katalyst-users"
    private val realm = System.getenv("JWT_REALM") ?: "KatalystExample"
    private val expirationSeconds = System.getenv("JWT_EXPIRATION")?.toLongOrNull() ?: 3600L
    private val algorithm = Algorithm.HMAC256(secret)

    fun configure(application: Application) {
        application.install(Authentication) {
            jwt("auth-jwt") {
                this.realm = JwtSettings.realm
                verifier(
                    JWT
                        .require(algorithm)
                        .withIssuer(issuer)
                        .withAudience(audience)
                        .build()
                )
                validate { credential ->
                    val accountId = credential.payload.getClaim("accountId").asLong()
                    if (accountId != null) {
                        AuthPrincipal(accountId, credential.payload.getClaim("email").asString())
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun generateToken(accountId: Long, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("accountId", accountId)
            .withClaim("email", email)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + expirationSeconds * 1000))
            .sign(algorithm)
}

data class AuthPrincipal(
    val accountId: Long,
    val email: String
) : Principal
