package io.github.darkryh.katalyst.example.service

import io.github.darkryh.katalyst.example.api.dto.LoginRequest
import io.github.darkryh.katalyst.example.api.dto.RegisterRequest
import io.github.darkryh.katalyst.example.infra.database.repositories.AuthAccountRepository
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import io.github.darkryh.katalyst.database.DatabaseFactory

class AuthenticationServiceIntegrationTest {

    private lateinit var environment: KatalystTestEnvironment

    @BeforeTest
    fun bootstrap() {
        environment = katalystTestEnvironment {
            database(inMemoryDatabaseConfig())
            scan("io.github.darkryh.katalyst.example")
        }
    }

    @AfterTest
    fun teardown() {
        environment.close()
    }

    @Test
    fun `registers and logs in a user via service layer`() = runBlocking {
        val service = environment.get<AuthenticationService>()
        val repository = environment.get<AuthAccountRepository>()
        val registerRequest = RegisterRequest(
            email = "integration@example.com",
            password = "Sup3rSecure!",
            displayName = "Integration Test"
        )

        val registerResponse = service.register(registerRequest)
        assertEquals(registerRequest.email.lowercase(), registerResponse.email)
        assertTrue(registerResponse.token.isNotBlank())

        val loginResponse = service.login(
            LoginRequest(
                email = registerRequest.email,
                password = registerRequest.password
            )
        )
        assertEquals(registerResponse.email, loginResponse.email)

        verifyPersistedState(repository)
    }

    private fun verifyPersistedState(repository: AuthAccountRepository) {
        val databaseFactory = environment.get<DatabaseFactory>()
        val count = transaction(databaseFactory.database) {
            repository.table.selectAll().count()
        }
        assertEquals(1, count)
    }
}
