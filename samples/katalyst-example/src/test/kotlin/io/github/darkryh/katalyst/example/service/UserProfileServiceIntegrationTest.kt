package io.github.darkryh.katalyst.example.service

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.example.domain.exception.UserExampleValidationException
import io.github.darkryh.katalyst.example.infra.database.entities.AuthAccountEntity
import io.github.darkryh.katalyst.example.infra.database.repositories.AuthAccountRepository
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import io.github.darkryh.katalyst.example.util.PasswordHasher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class UserProfileServiceIntegrationTest {
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
    fun `createProfileForAccount is idempotent per account`() = runBlocking {
        val service = environment.get<UserProfileService>()
        val accountId = seedAccount()

        val first = service.createProfileForAccount(accountId, "Display Name")
        val second = service.createProfileForAccount(accountId, "Another Name")

        assertEquals(first.id, second.id)
        assertEquals("Display Name", second.displayName)
    }

    @Test
    fun `list and fetch profiles`() = runBlocking {
        val service = environment.get<UserProfileService>()
        val accountId = seedAccount()

        val created = service.createProfileForAccount(accountId, "Profile Owner")
        val fetched = service.getProfile(created.id)

        assertEquals(created.id, fetched.id)
        assertTrue(service.listProfiles().any { it.id == created.id })
    }

    @Test
    fun `getProfile throws when missing`() : Unit = runBlocking {
        val service = environment.get<UserProfileService>()

        assertFailsWith<UserExampleValidationException> {
            service.getProfile(987654L)
        }
    }

    private suspend fun seedAccount(): Long {
        val repository = environment.get<AuthAccountRepository>()
        val passwordHasher = environment.get<PasswordHasher>()
        val transactionManager = environment.get<DatabaseTransactionManager>()
        return transactionManager.transaction {
            val saved = repository.save(
                AuthAccountEntity(
                    email = "profile-${System.currentTimeMillis()}@example.com",
                    passwordHash = passwordHasher.hash("Sup3rSecure!"),
                    createdAtMillis = System.currentTimeMillis(),
                    status = "active"
                )
            )
            requireNotNull(saved.id)
        }
    }
}
