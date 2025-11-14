package com.ead.katalyst.example.service

import com.ead.katalyst.example.api.dto.LoginRequest
import com.ead.katalyst.example.api.dto.RegisterRequest
import com.ead.katalyst.example.infra.database.repositories.AuthAccountRepository
import com.ead.katalyst.testing.core.KatalystTestEnvironment
import com.ead.katalyst.testing.core.inMemoryDatabaseConfig
import com.ead.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import com.ead.katalyst.database.DatabaseFactory

class AuthenticationServiceIntegrationTest {

    private lateinit var environment: KatalystTestEnvironment

    @BeforeTest
    fun bootstrap() {
        environment = katalystTestEnvironment {
            database(inMemoryDatabaseConfig())
            scan("com.ead.katalyst.example")
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
