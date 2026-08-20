package io.github.darkryh.katalyst.transactions.manager

import io.github.darkryh.katalyst.transactions.config.RetryPolicy
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.exception.TransactionTimeoutException
import io.github.darkryh.katalyst.transactions.workflow.CurrentWorkflowContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DatabaseTransactionManagerJoinBehaviorTest {

    @Test
    fun `nested transaction joins same active transaction scope`() = runBlocking {
        val database = testDatabase("nested_join_same_scope")
        val manager = DatabaseTransactionManager(database)

        manager.transaction {
            val outerIdentity = System.identityHashCode(this)

            val nestedIdentity = manager.transaction {
                System.identityHashCode(this)
            }

            assertEquals(outerIdentity, nestedIdentity)
        }
    }

    @Test
    fun `sequential root transactions use different transaction instances`() = runBlocking {
        val database = testDatabase("sequential_root_transactions")
        val manager = DatabaseTransactionManager(database)

        var firstIdentity = 0
        var secondIdentity = 0

        manager.transaction {
            firstIdentity = System.identityHashCode(this)
        }
        manager.transaction {
            secondIdentity = System.identityHashCode(this)
        }

        assertNotEquals(firstIdentity, secondIdentity)
    }

    @Test
    fun `workflow context is cleared after successful transaction`() = runBlocking {
        val database = testDatabase("workflow_context_cleanup")
        val manager = DatabaseTransactionManager(database)

        CurrentWorkflowContext.clear()
        assertNull(CurrentWorkflowContext.get())

        manager.transaction {
            // no-op
        }

        assertNull(CurrentWorkflowContext.get())
        CurrentWorkflowContext.clear()
    }

    @Test
    fun `existing outer workflow context is restored after transaction`() = runBlocking {
        val database = testDatabase("workflow_context_restore")
        val manager = DatabaseTransactionManager(database)

        CurrentWorkflowContext.set("outer-workflow")
        manager.transaction {
            assertEquals("outer-workflow", CurrentWorkflowContext.get())
        }
        assertEquals("outer-workflow", CurrentWorkflowContext.get())
        CurrentWorkflowContext.clear()
    }

    @Test
    fun `transient failure retries and succeeds while cleaning workflow context`() = runBlocking {
        val database = testDatabase("retry_then_success")
        val manager = DatabaseTransactionManager(database)
        var attempts = 0

        val result = manager.transaction(
            config = TransactionConfig(
                retryPolicy = RetryPolicy(maxRetries = 1)
            )
        ) {
            attempts++
            if (attempts == 1) {
                throw RuntimeException("wrapped", SQLException("Connection is closed", "08003"))
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertNull(CurrentWorkflowContext.get())
    }

    @Test
    fun `transaction joinability is false after transaction closes`() = runBlocking {
        val database = testDatabase("joinability_closed_tx")
        val manager = DatabaseTransactionManager(database)

        var transactionRef: JdbcTransaction? = null
        suspendTransaction(database) {
            transactionRef = this
            assertTrue(manager.isTransactionJoinable(this))
        }

        val closedTransaction = requireNotNull(transactionRef)
        assertFalse(manager.isTransactionJoinable(closedTransaction))
    }

    @Test
    fun `nullable transaction result returns null without timeout retry`() = runBlocking {
        val database = testDatabase("nullable_result_without_timeout")
        val manager = DatabaseTransactionManager(database)
        var attempts = 0

        val result: String? = manager.transaction(
            config = TransactionConfig(
                retryPolicy = RetryPolicy(maxRetries = 2)
            )
        ) {
            attempts++
            null
        }

        assertNull(result)
        assertEquals(1, attempts)
        assertNull(CurrentWorkflowContext.get())
    }

    @Test
    fun `real timeout still maps to TransactionTimeoutException and retries`() = runBlocking {
        val database = testDatabase("real_timeout_retry")
        val manager = DatabaseTransactionManager(database)
        var attempts = 0

        val exception = assertFailsWith<TransactionTimeoutException> {
            manager.transaction(
                config = TransactionConfig(
                    timeout = 10.toDuration(DurationUnit.MILLISECONDS),
                    retryPolicy = RetryPolicy(
                        maxRetries = 1,
                        backoffStrategy = io.github.darkryh.katalyst.transactions.config.BackoffStrategy.IMMEDIATE
                    )
                )
            ) {
                attempts++
                delay(30)
                "never"
            }
        }

        assertTrue(exception.message?.contains("Transaction timeout") == true)
        assertEquals(2, attempts)
        assertNull(CurrentWorkflowContext.get())
    }

    @Test
    fun `timeout on first attempt then nullable success returns null`() = runBlocking {
        val database = testDatabase("timeout_then_nullable_success")
        val manager = DatabaseTransactionManager(database)
        var attempts = 0

        val result: String? = manager.transaction(
            config = TransactionConfig(
                timeout = 150.toDuration(DurationUnit.MILLISECONDS),
                retryPolicy = RetryPolicy(
                    maxRetries = 1,
                    backoffStrategy = io.github.darkryh.katalyst.transactions.config.BackoffStrategy.IMMEDIATE
                )
            )
        ) {
            attempts++
            if (attempts == 1) {
                delay(300)
                "never"
            } else {
                null
            }
        }

        assertNull(result)
        assertEquals(2, attempts)
        assertNull(CurrentWorkflowContext.get())
    }

    private fun testDatabase(name: String): Database {
        return Database.connect(
            url = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
    }
}
