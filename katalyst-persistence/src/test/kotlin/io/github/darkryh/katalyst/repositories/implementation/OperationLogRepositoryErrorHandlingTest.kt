package io.github.darkryh.katalyst.repositories.implementation

import io.github.darkryh.katalyst.transactions.workflow.SimpleTransactionOperation
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Proves that [OperationLogRepository] surfaces real persistence failures instead of
 * silently swallowing them and returning a default value (empty list / Unit).
 *
 * Each test points the repository at a real H2 database whose `OperationLogTable`
 * schema was intentionally never created, so every call fails with a genuine SQL
 * exception ("table not found") - the same shape of failure a production DB outage
 * would produce. Before the fix, these calls would log the error and quietly return
 * an empty list / do nothing, hiding the failure from the caller.
 */
class OperationLogRepositoryErrorHandlingTest {

    private fun brokenRepository(): OperationLogRepository {
        val database = Database.connect(
            url = "jdbc:h2:mem:test_operation_log_broken_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        // Intentionally do NOT create OperationLogTable - every call below must fail loudly.
        return OperationLogRepository(database)
    }

    private fun testOperation() = SimpleTransactionOperation(
        workflowId = "wf-broken",
        operationIndex = 0,
        operationType = "INSERT",
        resourceType = "User",
        resourceId = "1"
    )

    @Test
    fun `logOperation should propagate persistence failure instead of swallowing it`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.logOperation("wf-broken", 0, testOperation())
        }
    }

    @Test
    fun `getPendingOperations should propagate persistence failure instead of returning empty list`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.getPendingOperations("wf-broken")
        }
    }

    @Test
    fun `getAllOperations should propagate persistence failure instead of returning empty list`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.getAllOperations("wf-broken")
        }
    }

    @Test
    fun `getFailedOperations should propagate persistence failure instead of returning empty list`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.getFailedOperations()
        }
    }

    @Test
    fun `markAsCommitted should propagate persistence failure instead of silently no-oping`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.markAsCommitted("wf-broken", 0)
        }
    }

    @Test
    fun `markAsUndone should propagate persistence failure instead of silently no-oping`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.markAsUndone("wf-broken", 0)
        }
    }

    @Test
    fun `deleteOldOperations should propagate persistence failure instead of returning 0`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.deleteOldOperations(System.currentTimeMillis())
        }
    }
}
