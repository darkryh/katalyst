package io.github.darkryh.katalyst.example.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.darkryh.katalyst.testing.core.config.FakeConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtSettingsServiceTest {
    private val jsonConfig = FakeConfigProvider(
        mapOf(
            "jwt.secret" to "test-secret",
            "jwt.issuer" to "katalyst-example",
            "jwt.audience" to "katalyst-users",
            "jwt.realm" to "TestRealm",
            "jwt.expirationSeconds" to 120L
        )
    )

    @Test
    fun `generateToken encodes expected claims`() {
        val service = JwtSettingsService(jsonConfig)

        val token = service.generateToken(accountId = 42L, email = "user@example.com")
        val verifier = JWT
            .require(Algorithm.HMAC256("test-secret"))
            .withIssuer("katalyst-example")
            .withAudience("katalyst-users")
            .build()

        val decoded = verifier.verify(token)

        assertEquals(42L, decoded.getClaim("accountId").asLong())
        assertEquals("user@example.com", decoded.getClaim("email").asString())
    }

    @Test
    fun `blank secret fails fast`() {
        val provider = FakeConfigProvider(
            mapOf(
                "jwt.secret" to "",
                "jwt.issuer" to "issuer",
                "jwt.audience" to "audience",
                "jwt.realm" to "realm"
            )
        )

        assertFailsWith<IllegalArgumentException> {
            JwtSettingsService(provider)
        }
    }
}
