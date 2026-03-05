package io.github.darkryh.katalyst.example.service

import io.github.darkryh.katalyst.core.transaction.DatabaseTransactionManager
import io.github.darkryh.katalyst.example.infra.database.entities.AuthAccountEntity
import io.github.darkryh.katalyst.example.infra.database.repositories.AuthAccountRepository
import io.github.darkryh.katalyst.example.util.PasswordHasher
import io.github.darkryh.katalyst.testing.core.KatalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Request-flow reliability regression for issue #7 style behavior:
 * immediate split transaction boundaries in one coroutine flow.
 */
class TransactionBoundaryReliabilityIntegrationTest {

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
    fun `split sequential transactions in same workflow remain reliable`() = runBlocking {
        val transactionManager = environment.get<DatabaseTransactionManager>()
        val repository = environment.get<AuthAccountRepository>()
        val passwordHasher = environment.get<PasswordHasher>()

        repeat(30) { index ->
            val workflowId = "chat-flow-${UUID.randomUUID()}"

            // Transaction A: prepare/create state
            val accountId = transactionManager.transaction(workflowId = workflowId) {
                val saved = repository.save(
                    AuthAccountEntity(
                        email = "chat-$index-${System.currentTimeMillis()}@example.com",
                        passwordHash = passwordHasher.hash("Sup3rSecure!"),
                        createdAtMillis = System.currentTimeMillis(),
                        status = "active"
                    )
                )
                requireNotNull(saved.id)
            }

            // Transaction B: immediate follow-up operation in the same request workflow
            val reloaded = transactionManager.transaction(workflowId = workflowId) {
                repository.findById(accountId)
            }

            assertNotNull(reloaded, "Expected account to be readable in immediate follow-up transaction")
            assertTrue(reloaded.id == accountId)
        }
    }

    @Test
    fun `split sequential transactions without explicit workflow id remain reliable`() = runBlocking {
        val transactionManager = environment.get<DatabaseTransactionManager>()
        val repository = environment.get<AuthAccountRepository>()
        val passwordHasher = environment.get<PasswordHasher>()

        repeat(30) { index ->
            // Transaction A: prepare/create state (no explicit workflow ID)
            val accountId = transactionManager.transaction {
                val saved = repository.save(
                    AuthAccountEntity(
                        email = "chat-noid-$index-${System.currentTimeMillis()}@example.com",
                        passwordHash = passwordHasher.hash("Sup3rSecure!"),
                        createdAtMillis = System.currentTimeMillis(),
                        status = "active"
                    )
                )
                requireNotNull(saved.id)
            }

            // Transaction B: immediate follow-up operation, still no explicit workflow ID
            val reloaded = transactionManager.transaction {
                repository.findById(accountId)
            }

            assertNotNull(reloaded, "Expected account to be readable in immediate follow-up transaction")
            assertTrue(reloaded.id == accountId)
        }
    }
}
