package com.ead.katalyst.example.service

import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.example.api.dto.LoginRequest
import com.ead.katalyst.example.api.dto.RegisterRequest
import com.ead.katalyst.example.infra.database.repositories.AuthAccountRepository
import com.ead.katalyst.example.testsupport.inMemoryDatabaseConfig
import com.ead.katalyst.example.testsupport.startKatalystForTests
import com.ead.katalyst.example.testsupport.stopKatalystForTests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.Koin

class AuthenticationServiceIntegrationTest {

    private lateinit var koin: Koin

    @BeforeTest
    fun bootstrap() {
        koin = startKatalystForTests(databaseConfig = inMemoryDatabaseConfig())
    }

    @AfterTest
    fun teardown() {
        stopKatalystForTests()
    }

    @Test
    fun `registers and logs in a user via service layer`() = runBlocking {
        val service = koin.get<AuthenticationService>()
        val repository = koin.get<AuthAccountRepository>()
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
        val databaseFactory = koin.get<DatabaseFactory>()
        val count = transaction(databaseFactory.database) {
            repository.table.selectAll().count()
        }
        assertEquals(1, count)
    }
}
