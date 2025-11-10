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
 * JWT authentication settings service with modularized DI injection.
 *
 * **REFACTORED FOR MODULARIZED DI:**
 * - Now uses Service interface (enables proper constructor injection)
 * - Receives ConfigProvider via constructor injection (Phase 3 discovery)
 * - Validates configuration in init block (fail fast approach)
 * - All configuration loaded synchronously (not lazy)
 *
 * **How It Works:**
 * 1. ConfigProviderDIModule registers ConfigProvider in Phase 1
 * 2. Component discovery (Phase 3) finds ConfigProvider in Koin
 * 3. JwtSettingsService constructor is satisfied: Service(config: ConfigProvider)
 * 4. Configuration loaded synchronously during init
 * 5. Validation happens immediately (fail if invalid)
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
 *
 * **Why Service Interface:**
 * Service interface declares constructor dependencies that Katalyst resolves during Phase 3.
 * ConfigProvider is available in Koin from Phase 1, so dependencies are satisfiable.
 * This enables proper constructor injection instead of lazy/manual approaches.
 */
class JwtSettingsService(
    private val config: ConfigProvider  // Constructor injection from Phase 3 discovery
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
