package io.github.darkryh.katalyst.repositories.implementation

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Proves that [WorkflowStateRepository] surfaces real persistence failures instead of
 * silently swallowing them and returning a default value (null / empty list / Unit).
 *
 * Each test points the repository at a real H2 database whose `WorkflowStateTable`
 * schema was intentionally never created, so every call fails with a genuine SQL
 * exception ("table not found") - the same shape of failure a production DB outage
 * would produce. Before the fix, these calls would log the error and quietly return a
 * default (e.g. `null` from [WorkflowStateRepository.getWorkflowState]), which is
 * indistinguishable from "workflow not found" and could make a recovery job believe
 * there is nothing to recover.
 */
class WorkflowStateRepositoryErrorHandlingTest {

    private fun brokenRepository(): WorkflowStateRepository {
        val database = Database.connect(
            url = "jdbc:h2:mem:test_workflow_state_broken_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
        // Intentionally do NOT create WorkflowStateTable - every call below must fail loudly.
        return WorkflowStateRepository(database)
    }

    @Test
    fun `startWorkflow should propagate persistence failure instead of swallowing it`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.startWorkflow("wf-broken", "broken_workflow")
        }
    }

    @Test
    fun `commitWorkflow should propagate persistence failure instead of silently no-oping`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.commitWorkflow("wf-broken")
        }
    }

    @Test
    fun `getWorkflowState should propagate persistence failure instead of returning null`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.getWorkflowState("wf-broken")
        }
    }

    @Test
    fun `getFailedWorkflows should propagate persistence failure instead of returning empty list`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.getFailedWorkflows()
        }
    }

    @Test
    fun `getFailedWorkflowsPage should propagate persistence failure instead of returning empty list`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.getFailedWorkflowsPage(limit = 10, after = null)
        }
    }

    @Test
    fun `deleteOldWorkflows should propagate persistence failure instead of returning 0`() = runTest {
        val repository = brokenRepository()

        assertFailsWith<Exception> {
            repository.deleteOldWorkflows(System.currentTimeMillis())
        }
    }
}
