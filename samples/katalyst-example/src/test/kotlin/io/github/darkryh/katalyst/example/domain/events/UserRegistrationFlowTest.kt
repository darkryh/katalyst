package io.github.darkryh.katalyst.example.domain.events

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.example.infra.database.entities.AuthAccountEntity
import io.github.darkryh.katalyst.example.infra.database.repositories.AuthAccountRepository
import io.github.darkryh.katalyst.example.infra.database.repositories.UserProfileRepository
import io.github.darkryh.katalyst.example.util.PasswordHasher
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
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
            scan("io.github.darkryh.katalyst.example")
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
