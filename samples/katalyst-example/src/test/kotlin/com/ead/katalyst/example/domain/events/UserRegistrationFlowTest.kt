package com.ead.katalyst.example.domain.events

import com.ead.katalyst.core.transaction.DatabaseTransactionManager
import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.repositories.AuthAccountRepository
import com.ead.katalyst.example.infra.database.repositories.UserProfileRepository
import com.ead.katalyst.example.util.PasswordHasher
import com.ead.katalyst.testing.core.KatalystTestEnvironment
import com.ead.katalyst.testing.core.inMemoryDatabaseConfig
import com.ead.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class UserRegistrationFlowTest {

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
    fun `publishing registration event creates profile`() : Unit = runBlocking {
        val authRepository = environment.get<AuthAccountRepository>()
        val profileRepository = environment.get<UserProfileRepository>()
        val passwordHasher = environment.get<PasswordHasher>()
        val transactionManager = environment.get<DatabaseTransactionManager>()

        val persistedAccount = transactionManager.transaction {
            authRepository.save(
                AuthAccountEntity(
                    email = "flow-${System.currentTimeMillis()}@example.com",
                    passwordHash = passwordHasher.hash("Sup3rSecure!"),
                    createdAtMillis = System.currentTimeMillis(),
                    status = "active"
                )
            )
        }

        val eventBus = environment.get<EventBus>()
        val accountId = requireNotNull(persistedAccount.id)
        eventBus.publish(
            UserRegisteredEvent(
                accountId = accountId,
                email = persistedAccount.email,
                displayName = "Flow Observer"
            )
        )

        val profile = transactionManager.transaction {
            profileRepository.findByAccountId(accountId)
        }

        assertNotNull(profile, "Expected profile to be created by event handler")
    }
}
