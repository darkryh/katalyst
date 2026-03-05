package io.github.darkryh.katalyst.transactions.manager

import io.github.darkryh.katalyst.transactions.config.RetryPolicy
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.workflow.CurrentWorkflowContext
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
import kotlin.test.assertTrue

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

    private fun testDatabase(name: String): Database {
        return Database.connect(
            url = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
    }
}
