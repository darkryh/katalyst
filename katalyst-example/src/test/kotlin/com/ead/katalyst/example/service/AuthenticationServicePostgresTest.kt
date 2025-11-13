package com.ead.katalyst.example.service

import com.ead.katalyst.config.DatabaseConfig
import com.ead.katalyst.example.api.dto.LoginRequest
import com.ead.katalyst.example.api.dto.RegisterRequest
import com.ead.katalyst.example.testsupport.defaultTestFeatures
import com.ead.katalyst.example.testsupport.startKatalystForTests
import com.ead.katalyst.example.testsupport.stopKatalystForTests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.testcontainers.containers.PostgreSQLContainer
import org.junit.jupiter.api.Assumptions.assumeTrue

class AuthenticationServicePostgresTest {

    private val container = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var koin: Koin

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

        koin = startKatalystForTests(
            databaseConfig = config,
            features = defaultTestFeatures()
        )
    }

    @AfterTest
    fun tearDown() {
        runCatching { stopKatalystForTests() }
        if (container.isRunning) {
            runCatching { container.stop() }
        }
    }

    @Test
    fun `register flow works against postgres`() = runBlocking {
        val service = koin.get<AuthenticationService>()
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
