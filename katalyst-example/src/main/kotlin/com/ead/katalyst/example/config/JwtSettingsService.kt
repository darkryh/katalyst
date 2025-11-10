package com.ead.katalyst.example.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.core.config.ConfigProvider
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.jwt
import org.slf4j.LoggerFactory
import java.util.Date

/**
 * JWT authentication settings service that uses ConfigProvider.
 *
 * Automatically discovered and injected by Katalyst's reflection-based DI.
 *
 * **Configuration Keys:**
 * - `jwt.secret`: HMAC256 signing secret (REQUIRED)
 * - `jwt.issuer`: JWT issuer claim (default: katalyst-example)
 * - `jwt.audience`: JWT audience claim (default: katalyst-users)
 * - `jwt.realm`: Authentication realm (default: KatalystExample)
 * - `jwt.expirationSeconds`: Token TTL in seconds (default: 3600)
 *
 * **Usage:**
 * ```kotlin
 * class AuthenticationService(private val jwt: JwtSettingsService) : Service {
 *     fun generateToken(accountId: Long, email: String): String =
 *         jwt.generateToken(accountId, email)
 * }
 * ```
 */
class JwtSettingsService(private val config: ConfigProvider) : Service {
    companion object {
        private val log = LoggerFactory.getLogger(JwtSettingsService::class.java)
    }

    private val secret: String = config.getString("jwt.secret")
    private val issuer: String = config.getString("jwt.issuer")
    private val audience: String = config.getString("jwt.audience")
    val realm: String = config.getString("jwt.realm")
    private val expirationSeconds: Long = config.getLong("jwt.expirationSeconds", 3600L)

    // Algorithm is computed lazily to ensure secret is available
    private val algorithm: Algorithm by lazy {
        Algorithm.HMAC256(secret)
    }

    init {
        require(secret.isNotBlank()) { "jwt.secret must not be blank" }
        log.debug("JWT settings loaded: issuer=$issuer, audience=$audience")
    }

    /**
     * Configure Ktor application with JWT authentication.
     *
     * Installs the Authentication plugin with JWT verification.
     * Should be called during application module setup.
     *
     * @param application Ktor Application instance
     */
    fun configure(application: Application) {
        application.install(Authentication) {
            jwt("auth-jwt") {
                this.realm = this@JwtSettingsService.realm
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
                        AuthPrincipal(
                            accountId,
                            credential.payload.getClaim("email").asString()
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * Generate a new JWT token.
     *
     * @param accountId User account ID
     * @param email User email address
     * @return Signed JWT token
     */
    fun generateToken(accountId: Long, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("accountId", accountId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationSeconds * 1000))
            .sign(algorithm)
}

/**
 * JWT Principal - authenticated user information extracted from token.
 *
 * @property accountId Long User account ID from JWT
 * @property email String User email from JWT
 */
data class AuthPrincipal(
    val accountId: Long,
    val email: String
) : Principal
