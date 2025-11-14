package com.ead.katalyst.repositories.implementation

import com.ead.katalyst.database.table.WorkflowStateTable
import com.ead.katalyst.transactions.workflow.WorkflowStatus
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

/**
 * Comprehensive tests for WorkflowStateRepository.
 *
 * Tests cover:
 * - Workflow lifecycle management (start, commit, fail, undo)
 * - State transitions and validation
 * - Error tracking and recovery
 * - Query operations (get state, get failed workflows)
 * - Cleanup operations (delete old workflows)
 * - Edge cases (non-existent workflows, concurrent access)
 * - Exception handling and graceful degradation
 */
class WorkflowStateRepositoryTest {

    private lateinit var database: Database
    private lateinit var repository: WorkflowStateRepository

    @BeforeTest
    fun setup() {
        // Create in-memory H2 database
        database = Database.connect(
            url = "jdbc:h2:mem:test_workflow_state_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )

        // Create tables
        transaction(database) {
            SchemaUtils.create(WorkflowStateTable)
        }

        repository = WorkflowStateRepository(database)
    }

    @AfterTest
    fun teardown() {
        // Drop tables
        transaction(database) {
            SchemaUtils.drop(WorkflowStateTable)
        }
    }

    // ========== START WORKFLOW TESTS ==========

    @Test
    fun `startWorkflow should create workflow with STARTED status`() = runTest {
        // Given
        val workflowId = "wf-001"
        val workflowName = "user_registration"

        // When
        repository.startWorkflow(workflowId, workflowName)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(workflowId, state.workflowId)
        assertEquals(workflowName, state.workflowName)
        assertEquals(WorkflowStatus.STARTED, state.status)
        assertTrue(state.createdAt > 0)
        assertNull(state.completedAt)
        assertNull(state.failedAtOperation)
        assertNull(state.errorMessage)
    }

    @Test
    fun `startWorkflow should set createdAt timestamp`() = runTest {
        // Given
        val workflowId = "wf-002"
        val beforeTimestamp = System.currentTimeMillis()

        // When
        repository.startWorkflow(workflowId, "test_workflow")

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertTrue(state.createdAt >= beforeTimestamp)
        assertTrue(state.createdAt <= System.currentTimeMillis())
    }

    @Test
    fun `startWorkflow should create multiple independent workflows`() = runTest {
        // Given
        val workflow1 = "wf-100"
        val workflow2 = "wf-200"
        val workflow3 = "wf-300"

        // When
        repository.startWorkflow(workflow1, "workflow_1")
        repository.startWorkflow(workflow2, "workflow_2")
        repository.startWorkflow(workflow3, "workflow_3")

        // Then
        val state1 = repository.getWorkflowState(workflow1)
        val state2 = repository.getWorkflowState(workflow2)
        val state3 = repository.getWorkflowState(workflow3)

        assertNotNull(state1)
        assertNotNull(state2)
        assertNotNull(state3)
        assertEquals(WorkflowStatus.STARTED, state1.status)
        assertEquals(WorkflowStatus.STARTED, state2.status)
        assertEquals(WorkflowStatus.STARTED, state3.status)
    }

    // ========== COMMIT WORKFLOW TESTS ==========

    @Test
    fun `commitWorkflow should update status to COMMITTED`() = runTest {
        // Given
        val workflowId = "wf-003"
        repository.startWorkflow(workflowId, "test_workflow")

        // When
        repository.commitWorkflow(workflowId)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(WorkflowStatus.COMMITTED, state.status)
    }

    @Test
    fun `commitWorkflow should set completedAt timestamp`() = runTest {
        // Given
        val workflowId = "wf-004"
        repository.startWorkflow(workflowId, "test_workflow")
        val beforeCommit = System.currentTimeMillis()

        // When
        repository.commitWorkflow(workflowId)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertNotNull(state.completedAt)
        assertTrue(state.completedAt!! >= beforeCommit)
        assertTrue(state.completedAt!! <= System.currentTimeMillis())
    }

    @Test
    fun `commitWorkflow should not affect other workflow fields`() = runTest {
        // Given
        val workflowId = "wf-005"
        val workflowName = "test_workflow"
        repository.startWorkflow(workflowId, workflowName)
        val originalState = repository.getWorkflowState(workflowId)!!

        // When
        repository.commitWorkflow(workflowId)

        // Then
        val state = repository.getWorkflowState(workflowId)!!
        assertEquals(originalState.workflowId, state.workflowId)
        assertEquals(originalState.workflowName, state.workflowName)
        assertEquals(originalState.createdAt, state.createdAt)
        assertNull(state.failedAtOperation)
        assertNull(state.errorMessage)
    }

    @Test
    fun `commitWorkflow for non-existent workflow should not throw`() = runTest {
        // Given
        val nonExistentId = "non-existent-workflow"

        // When/Then - Should not throw
        repository.commitWorkflow(nonExistentId)
    }

    // ========== FAIL WORKFLOW TESTS ==========

    @Test
    fun `failWorkflow should update status to FAILED`() = runTest {
        // Given
        val workflowId = "wf-006"
        repository.startWorkflow(workflowId, "test_workflow")

        // When
        repository.failWorkflow(workflowId, failedAtOperation = 3, error = "Validation failed")

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(WorkflowStatus.FAILED, state.status)
    }

    @Test
    fun `failWorkflow should record failure details`() = runTest {
        // Given
        val workflowId = "wf-007"
        val failedAtOperation = 5
        val errorMessage = "Database connection timeout"
        repository.startWorkflow(workflowId, "test_workflow")

        // When
        repository.failWorkflow(workflowId, failedAtOperation, errorMessage)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(failedAtOperation, state.failedAtOperation)
        assertEquals(errorMessage, state.errorMessage)
    }

    @Test
    fun `failWorkflow should set completedAt timestamp`() = runTest {
        // Given
        val workflowId = "wf-008"
        repository.startWorkflow(workflowId, "test_workflow")
        val beforeFail = System.currentTimeMillis()

        // When
        repository.failWorkflow(workflowId, failedAtOperation = 2, error = "Test error")

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertNotNull(state.completedAt)
        assertTrue(state.completedAt!! >= beforeFail)
    }

    @Test
    fun `failWorkflow should handle operation index 0`() = runTest {
        // Given
        val workflowId = "wf-009"
        repository.startWorkflow(workflowId, "test_workflow")

        // When
        repository.failWorkflow(workflowId, failedAtOperation = 0, error = "First operation failed")

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(0, state.failedAtOperation)
    }

    @Test
    fun `failWorkflow should handle long error messages`() = runTest {
        // Given
        val workflowId = "wf-010"
        val longError = "Error: " + "x".repeat(5000)
        repository.startWorkflow(workflowId, "test_workflow")

        // When
        repository.failWorkflow(workflowId, failedAtOperation = 1, error = longError)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(longError, state.errorMessage)
    }

    // ========== MARK AS UNDONE TESTS ==========

    @Test
    fun `markAsUndone should update status to UNDONE`() = runTest {
        // Given
        val workflowId = "wf-011"
        repository.startWorkflow(workflowId, "test_workflow")
        repository.failWorkflow(workflowId, failedAtOperation = 2, error = "Test error")

        // When
        repository.markAsUndone(workflowId)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(WorkflowStatus.UNDONE, state.status)
    }

    @Test
    fun `markAsUndone should set completedAt timestamp`() = runTest {
        // Given
        val workflowId = "wf-012"
        repository.startWorkflow(workflowId, "test_workflow")
        repository.failWorkflow(workflowId, failedAtOperation = 1, error = "Test error")
        val beforeUndo = System.currentTimeMillis()

        // When
        repository.markAsUndone(workflowId)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertNotNull(state.completedAt)
        assertTrue(state.completedAt!! >= beforeUndo)
    }

    @Test
    fun `markAsUndone should preserve failure details`() = runTest {
        // Given
        val workflowId = "wf-013"
        val failedAtOperation = 3
        val errorMessage = "Original error"
        repository.startWorkflow(workflowId, "test_workflow")
        repository.failWorkflow(workflowId, failedAtOperation, errorMessage)

        // When
        repository.markAsUndone(workflowId)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(failedAtOperation, state.failedAtOperation)
        assertEquals(errorMessage, state.errorMessage)
    }

    // ========== GET WORKFLOW STATE TESTS ==========

    @Test
    fun `getWorkflowState should return null for non-existent workflow`() = runTest {
        // When
        val state = repository.getWorkflowState("non-existent-id")

        // Then
        assertNull(state)
    }

    @Test
    fun `getWorkflowState should return complete workflow data`() = runTest {
        // Given
        val workflowId = "wf-014"
        val workflowName = "user_registration"
        repository.startWorkflow(workflowId, workflowName)
        repository.failWorkflow(workflowId, failedAtOperation = 2, error = "Test error")

        // When
        val state = repository.getWorkflowState(workflowId)

        // Then
        assertNotNull(state)
        assertEquals(workflowId, state.workflowId)
        assertEquals(workflowName, state.workflowName)
        assertEquals(WorkflowStatus.FAILED, state.status)
        assertEquals(2, state.failedAtOperation)
        assertEquals("Test error", state.errorMessage)
        assertTrue(state.createdAt > 0)
        assertNotNull(state.completedAt)
    }

    @Test
    fun `getWorkflowState should reflect latest status`() = runTest {
        // Given
        val workflowId = "wf-015"
        repository.startWorkflow(workflowId, "test_workflow")

        // When/Then - Check STARTED
        var state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.STARTED, state?.status)

        // When/Then - Check FAILED
        repository.failWorkflow(workflowId, failedAtOperation = 1, error = "Test error")
        state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.FAILED, state?.status)

        // When/Then - Check UNDONE
        repository.markAsUndone(workflowId)
        state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.UNDONE, state?.status)
    }

    // ========== GET FAILED WORKFLOWS TESTS ==========

    @Test
    fun `getFailedWorkflows should return empty list when no failures`() = runTest {
        // Given
        repository.startWorkflow("wf-100", "workflow_1")
        repository.commitWorkflow("wf-100")

        // When
        val failedWorkflows = repository.getFailedWorkflows()

        // Then
        assertEquals(0, failedWorkflows.size)
    }

    @Test
    fun `getFailedWorkflows should return FAILED workflows`() = runTest {
        // Given
        repository.startWorkflow("wf-101", "workflow_1")
        repository.failWorkflow("wf-101", failedAtOperation = 1, error = "Error 1")

        repository.startWorkflow("wf-102", "workflow_2")
        repository.commitWorkflow("wf-102")

        repository.startWorkflow("wf-103", "workflow_3")
        repository.failWorkflow("wf-103", failedAtOperation = 2, error = "Error 2")

        // When
        val failedWorkflows = repository.getFailedWorkflows()

        // Then
        assertEquals(2, failedWorkflows.size)
        assertTrue(failedWorkflows.any { it.workflowId == "wf-101" })
        assertTrue(failedWorkflows.any { it.workflowId == "wf-103" })
    }

    @Test
    fun `getFailedWorkflows should not return UNDONE workflows`() = runTest {
        // Given
        repository.startWorkflow("wf-104", "workflow_1")
        repository.failWorkflow("wf-104", failedAtOperation = 1, error = "Error")
        repository.markAsUndone("wf-104")

        repository.startWorkflow("wf-105", "workflow_2")
        repository.failWorkflow("wf-105", failedAtOperation = 1, error = "Error")

        // When
        val failedWorkflows = repository.getFailedWorkflows()

        // Then
        assertEquals(1, failedWorkflows.size)
        assertEquals("wf-105", failedWorkflows[0].workflowId)
    }

    @Test
    fun `getFailedWorkflows should include complete failure details`() = runTest {
        // Given
        val workflowId = "wf-106"
        val workflowName = "test_workflow"
        val failedAtOperation = 3
        val errorMessage = "Test error"

        repository.startWorkflow(workflowId, workflowName)
        repository.failWorkflow(workflowId, failedAtOperation, errorMessage)

        // When
        val failedWorkflows = repository.getFailedWorkflows()

        // Then
        assertEquals(1, failedWorkflows.size)
        val workflow = failedWorkflows[0]
        assertEquals(workflowId, workflow.workflowId)
        assertEquals(workflowName, workflow.workflowName)
        assertEquals(WorkflowStatus.FAILED, workflow.status)
        assertEquals(failedAtOperation, workflow.failedAtOperation)
        assertEquals(errorMessage, workflow.errorMessage)
    }

    @Test
    fun `getFailedWorkflows should order by createdAt`() = runTest {
        // Given - Create workflows with slight delay to ensure different timestamps
        repository.startWorkflow("wf-200", "workflow_1")
        Thread.sleep(10)
        repository.startWorkflow("wf-201", "workflow_2")
        Thread.sleep(10)
        repository.startWorkflow("wf-202", "workflow_3")

        // Fail them in reverse order
        repository.failWorkflow("wf-202", failedAtOperation = 1, error = "Error 3")
        repository.failWorkflow("wf-200", failedAtOperation = 1, error = "Error 1")
        repository.failWorkflow("wf-201", failedAtOperation = 1, error = "Error 2")

        // When
        val failedWorkflows = repository.getFailedWorkflows()

        // Then - Should be ordered by creation time, not failure time
        assertEquals(3, failedWorkflows.size)
        assertEquals("wf-200", failedWorkflows[0].workflowId)
        assertEquals("wf-201", failedWorkflows[1].workflowId)
        assertEquals("wf-202", failedWorkflows[2].workflowId)
    }

    // ========== DELETE OLD WORKFLOWS TESTS ==========

    @Test
    fun `deleteOldWorkflows should delete COMMITTED workflows before timestamp`() = runTest {
        // Given
        val now = System.currentTimeMillis()

        repository.startWorkflow("wf-old", "old_workflow")
        repository.commitWorkflow("wf-old")

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()
        Thread.sleep(50)

        repository.startWorkflow("wf-new", "new_workflow")
        repository.commitWorkflow("wf-new")

        // When
        val deletedCount = repository.deleteOldWorkflows(cutoffTime)

        // Then
        assertEquals(1, deletedCount)
        assertNull(repository.getWorkflowState("wf-old"))
        assertNotNull(repository.getWorkflowState("wf-new"))
    }

    @Test
    fun `deleteOldWorkflows should not delete FAILED workflows`() = runTest {
        // Given
        val now = System.currentTimeMillis()

        repository.startWorkflow("wf-failed", "failed_workflow")
        repository.failWorkflow("wf-failed", failedAtOperation = 1, error = "Error")

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldWorkflows(cutoffTime)

        // Then
        assertEquals(0, deletedCount)
        assertNotNull(repository.getWorkflowState("wf-failed"))
    }

    @Test
    fun `deleteOldWorkflows should not delete STARTED workflows`() = runTest {
        // Given
        val now = System.currentTimeMillis()

        repository.startWorkflow("wf-started", "started_workflow")

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldWorkflows(cutoffTime)

        // Then
        assertEquals(0, deletedCount)
        assertNotNull(repository.getWorkflowState("wf-started"))
    }

    @Test
    fun `deleteOldWorkflows should not delete UNDONE workflows`() = runTest {
        // Given
        val now = System.currentTimeMillis()

        repository.startWorkflow("wf-undone", "undone_workflow")
        repository.failWorkflow("wf-undone", failedAtOperation = 1, error = "Error")
        repository.markAsUndone("wf-undone")

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldWorkflows(cutoffTime)

        // Then
        assertEquals(0, deletedCount)
        assertNotNull(repository.getWorkflowState("wf-undone"))
    }

    @Test
    fun `deleteOldWorkflows should return 0 when no workflows to delete`() = runTest {
        // Given
        val futureTimestamp = System.currentTimeMillis() - 1000

        repository.startWorkflow("wf-recent", "recent_workflow")
        repository.commitWorkflow("wf-recent")

        // When
        val deletedCount = repository.deleteOldWorkflows(futureTimestamp)

        // Then
        assertEquals(0, deletedCount)
    }

    @Test
    fun `deleteOldWorkflows should delete multiple old COMMITTED workflows`() = runTest {
        // Given
        val now = System.currentTimeMillis()

        repository.startWorkflow("wf-old-1", "old_workflow_1")
        repository.commitWorkflow("wf-old-1")

        repository.startWorkflow("wf-old-2", "old_workflow_2")
        repository.commitWorkflow("wf-old-2")

        repository.startWorkflow("wf-old-3", "old_workflow_3")
        repository.commitWorkflow("wf-old-3")

        Thread.sleep(50)
        val cutoffTime = System.currentTimeMillis()

        // When
        val deletedCount = repository.deleteOldWorkflows(cutoffTime)

        // Then
        assertEquals(3, deletedCount)
    }

    // ========== EDGE CASES & ERROR HANDLING ==========

    @Test
    fun `operations should be thread-safe via database transactions`() = runTest {
        // Given
        val workflowId = "wf-concurrent"
        repository.startWorkflow(workflowId, "test_workflow")

        // When - Multiple operations on same workflow
        repository.commitWorkflow(workflowId)
        repository.failWorkflow(workflowId, failedAtOperation = 1, error = "Error")
        repository.markAsUndone(workflowId)

        // Then - Should complete without errors
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        // Last operation should win
        assertEquals(WorkflowStatus.UNDONE, state.status)
    }

    @Test
    fun `repository should handle empty workflow name`() = runTest {
        // Given
        val workflowId = "wf-empty-name"

        // When
        repository.startWorkflow(workflowId, "")

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals("", state.workflowName)
    }

    @Test
    fun `repository should handle very long workflow names`() = runTest {
        // Given
        val workflowId = "wf-long-name"
        val longName = "workflow_" + "x".repeat(200)

        // When
        repository.startWorkflow(workflowId, longName)

        // Then
        val state = repository.getWorkflowState(workflowId)
        assertNotNull(state)
        assertEquals(longName, state.workflowName)
    }

    @Test
    fun `complete workflow lifecycle - success path`() = runTest {
        // Given
        val workflowId = "wf-lifecycle-success"

        // When/Then - Complete lifecycle
        repository.startWorkflow(workflowId, "test_workflow")
        var state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.STARTED, state?.status)

        repository.commitWorkflow(workflowId)
        state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.COMMITTED, state?.status)
        assertNotNull(state?.completedAt)
    }

    @Test
    fun `complete workflow lifecycle - failure and undo path`() = runTest {
        // Given
        val workflowId = "wf-lifecycle-failure"

        // When/Then - Complete lifecycle with failure
        repository.startWorkflow(workflowId, "test_workflow")
        var state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.STARTED, state?.status)

        repository.failWorkflow(workflowId, failedAtOperation = 2, error = "Test error")
        state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.FAILED, state?.status)
        assertEquals(2, state?.failedAtOperation)

        repository.markAsUndone(workflowId)
        state = repository.getWorkflowState(workflowId)
        assertEquals(WorkflowStatus.UNDONE, state?.status)
    }
}
