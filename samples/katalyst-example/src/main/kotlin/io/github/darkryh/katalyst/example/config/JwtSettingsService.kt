package io.github.darkryh.katalyst.example.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.example.config.security.AuthPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import org.slf4j.LoggerFactory
import java.util.Date

/**
 * JWT authentication settings service.
 *
 * Uses constructor injection to receive ConfigProvider from DI (Phase 1 feature).
 * Configuration keys: jwt.secret (required), jwt.issuer, jwt.audience, jwt.realm, jwt.expirationSeconds
 */
class JwtSettingsService(
    config: ConfigProvider
) : Service {
    companion object {
        private val log = LoggerFactory.getLogger(JwtSettingsService::class.java)
    }

    // Synchronous initialization - all values loaded during construction
    // This ensures errors are detected early (Phase 3) not later
    private val secret: String = config.getString("jwt.secret")
    private val issuer: String = config.getString("jwt.issuer")
    private val audience: String = config.getString("jwt.audience")
    val realm: String = config.getString("jwt.realm")
    private val expirationSeconds: Long = config.getLong("jwt.expirationSeconds", 3600L)

    // Algorithm computed once during initialization
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    init {
        // Validation during construction - fail fast if configuration invalid
        // Errors caught during Phase 3 (component discovery) not later during first use
        require(secret.isNotBlank()) { "jwt.secret must not be blank" }
        log.info("✓ JwtSettingsService initialized")
        log.debug("  issuer: $issuer")
        log.debug("  audience: $audience")
        log.debug("  realm: $realm")
        log.debug("  expirationSeconds: $expirationSeconds")
    }

    /**
     * Configure Ktor application with JWT authentication.
     *
     * Called after JwtSettingsService is fully initialized and validated.
     * All configuration values are already loaded and available.
     * Installs the Authentication plugin with JWT verification.
     *
     * @param application Ktor Application instance
     */
    fun configure(application: Application) {
        log.debug("Configuring JWT authentication for Ktor application")
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
