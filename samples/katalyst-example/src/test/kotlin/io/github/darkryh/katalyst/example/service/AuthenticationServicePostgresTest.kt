package io.github.darkryh.katalyst.example.service

import io.github.darkryh.katalyst.config.DatabaseConfig
import io.github.darkryh.katalyst.example.api.dto.LoginRequest
import io.github.darkryh.katalyst.example.api.dto.RegisterRequest
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import org.junit.jupiter.api.Assumptions.assumeTrue

class AuthenticationServicePostgresTest {

    private val container = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var environment: KatalystTestEnvironment

    @BeforeTest
    fun setUp() {
        val startResult = runCatching { container.start() }
        assumeTrue(startResult.isSuccess, "Docker not available: ${startResult.exceptionOrNull()?.message}")
        val config = DatabaseConfig(
            url = container.jdbcUrl,
            driver = container.driverClassName,
            username = container.username,
            password = container.password
        )

        environment = katalystTestEnvironment {
            database(config)
            scan("io.github.darkryh.katalyst.example")
        }
    }

    @AfterTest
    fun tearDown() {
        runCatching { environment.close() }
        if (container.isRunning) {
            runCatching { container.stop() }
        }
    }

    @Test
    fun `register flow works against postgres`() = runBlocking {
        val service = environment.get<AuthenticationService>()
        val email = "pg-${System.currentTimeMillis()}@example.com"
        val registerResponse = service.register(
            RegisterRequest(
                email = email,
                password = "Sup3rSecure!",
                displayName = "Postgres User"
            )
        )
        assertTrue(registerResponse.token.isNotBlank())

        val loginResponse = service.login(LoginRequest(email = email, password = "Sup3rSecure!"))
        assertEquals(registerResponse.email, loginResponse.email)
    }
}
