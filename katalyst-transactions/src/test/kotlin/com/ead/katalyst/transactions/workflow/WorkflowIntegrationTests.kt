package com.ead.katalyst.transactions.workflow

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Integration tests for workflow system.
 *
 * Tests the full workflow lifecycle including:
 * - Multi-step workflow execution
 * - State machine transitions
 * - Checkpoint recovery
 * - Error handling
 */
@DisplayName("Workflow Integration Tests")
class WorkflowIntegrationTests {

    private lateinit var operationLog: TestOperationLog
    private lateinit var stateManager: TestWorkflowStateManager
    private lateinit var undoEngine: TestUndoEngine
    private lateinit var operationCounter: OperationCounter

    @BeforeEach
    fun setup() {
        operationLog = TestOperationLog()
        stateManager = TestWorkflowStateManager()
        undoEngine = TestUndoEngine()
        operationCounter = OperationCounter()
    }

    @Test
    @DisplayName("Should execute multi-step workflow successfully")
    fun testMultiStepWorkflowExecution() = runTest {
        // Arrange
        val workflow = TestWorkflowBuilder("user-registration")
            .addStep("create-account") { operationCounter.increment("INSERT_ACCOUNT") }
            .addStep("create-profile") { operationCounter.increment("INSERT_PROFILE") }
            .addStep("send-email") { operationCounter.increment("SEND_EMAIL") }
            .build()

        // Act
        val result = workflow.execute()

        // Assert
        assertTrue(result.isSuccessful(), "Workflow should succeed")
        assertEquals(3, result.executedSteps.size, "Should execute 3 steps")
        assertEquals(1, operationCounter.getCount("INSERT_ACCOUNT"))
        assertEquals(1, operationCounter.getCount("INSERT_PROFILE"))
        assertEquals(1, operationCounter.getCount("SEND_EMAIL"))
    }

    @Test
    @DisplayName("Should handle workflow failure and stop execution")
    fun testWorkflowFailureHandling() = runTest {
        // Arrange
        val workflow = TestWorkflowBuilder("failing-workflow")
            .addStep("step-1") { operationCounter.increment("STEP1") }
            .addStep("step-2-fails") { throw RuntimeException("Step 2 failed") }
            .addStep("step-3") { operationCounter.increment("STEP3") }  // Should not execute
            .build()

        // Act
        val result = workflow.execute()

        // Assert
        assertFalse(result.isSuccessful(), "Workflow should fail")
        assertEquals(2, result.executedSteps.size, "Should execute 2 steps before failure")
        assertEquals(1, operationCounter.getCount("STEP1"))
        assertEquals(0, operationCounter.getCount("STEP3"), "Step 3 should not execute")
        assertNotNull(result.getFailedStep(), "Should have a failed step")
    }

    @Test
    @DisplayName("Should support checkpoint-based recovery")
    fun testCheckpointRecovery() = runTest {
        // Arrange
        val workflow = TestWorkflowBuilder("workflow-with-checkpoints")
            .addStep("create-account") { operationCounter.increment("CREATE_ACCOUNT") }
            .addStep("send-welcome") { operationCounter.increment("SEND_WELCOME") }
            .build()

        // Execute and record checkpoint
        val firstRun = workflow.execute()
        assertTrue(firstRun.isSuccessful())

        // Create a new workflow to test resume from checkpoint
        val resumedWorkflow = TestWorkflowBuilder("workflow-resumed")
            .addStep("send-welcome") { operationCounter.increment("SEND_WELCOME") }
            .addStep("activate-account") { operationCounter.increment("ACTIVATE") }
            .build()

        operationCounter.reset()
        val secondRun = resumedWorkflow.execute()

        // Assert
        assertTrue(secondRun.isSuccessful())
        assertEquals(1, operationCounter.getCount("SEND_WELCOME"))
        assertEquals(1, operationCounter.getCount("ACTIVATE"))
    }

    @Test
    @DisplayName("Should track execution timing for each step")
    fun testExecutionTimingTracking() = runTest {
        // Arrange
        val workflow = TestWorkflowBuilder("timing-workflow")
            .addStep("quick-step") { /* instant */ }
            .addStep("slow-step") { Thread.sleep(10) }
            .build()

        // Act
        val result = workflow.execute()

        // Assert
        assertTrue(result.isSuccessful())
        val slowStep = result.executedSteps[1]
        assertTrue(slowStep.durationMs >= 10, "Slow step should take at least 10ms")
    }

    @Test
    @DisplayName("Should transition state machine correctly")
    fun testStateMachineTransitions() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow")
        val verifier = StateMachineVerifier(stateMachine)

        // Assert initial state
        verifier.assertStateIs(WorkflowMachineState.CREATED)

        // Act & Assert transitions
        assertTrue(stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION))
        verifier.assertStateIs(WorkflowMachineState.RUNNING)

        assertTrue(stateMachine.transition(WorkflowStateTransition.COMMIT))
        verifier.assertStateIs(WorkflowMachineState.COMMITTED)

        // Assert transition history
        val history = verifier.getTransitionLog()
        assertEquals(2, history.size)
        assertEquals(WorkflowMachineState.CREATED to WorkflowMachineState.RUNNING, history[0])
        assertEquals(WorkflowMachineState.RUNNING to WorkflowMachineState.COMMITTED, history[1])
    }

    @Test
    @DisplayName("Should prevent invalid state transitions")
    fun testInvalidStateTransitions() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow")

        // Act & Assert - can't go directly to COMMITTED from CREATED
        assertFalse(stateMachine.transition(WorkflowStateTransition.COMMIT))
        assertEquals(WorkflowMachineState.CREATED, stateMachine.getState())
    }

    @Test
    @DisplayName("Should handle failure to undo transition")
    fun testFailureUndoTransition() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow")

        // Act - go to RUNNING then FAILED
        stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION)
        stateMachine.transition(WorkflowStateTransition.FAIL)

        // Assert state
        assertEquals(WorkflowMachineState.FAILED, stateMachine.getState())
        assertTrue(stateMachine.canUndo(), "Should allow undo from FAILED state")

        // Act - begin undo
        assertTrue(stateMachine.transition(WorkflowStateTransition.BEGIN_UNDO))
        assertEquals(WorkflowMachineState.UNDOING, stateMachine.getState())

        // Complete undo
        assertTrue(stateMachine.transition(WorkflowStateTransition.UNDO_COMPLETE))
        assertEquals(WorkflowMachineState.UNDONE, stateMachine.getState())
    }

    @Test
    @DisplayName("Should allow pause and resume")
    fun testPauseAndResume() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow")

        // Act - transition to RUNNING
        stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION)
        assertEquals(WorkflowMachineState.RUNNING, stateMachine.getState())

        // Pause
        assertTrue(stateMachine.transition(WorkflowStateTransition.PAUSE))
        assertEquals(WorkflowMachineState.PAUSED, stateMachine.getState())

        // Resume
        assertTrue(stateMachine.transition(WorkflowStateTransition.RESUME))
        assertEquals(WorkflowMachineState.RUNNING, stateMachine.getState())
    }

    @Test
    @DisplayName("Should reach terminal state")
    fun testTerminalStates() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow")

        // Act - reach COMMITTED state
        stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION)
        stateMachine.transition(WorkflowStateTransition.COMMIT)

        // Assert terminal state
        assertTrue(stateMachine.isTerminal(), "COMMITTED is a terminal state")
        assertFalse(stateMachine.isActive(), "Terminal states are not active")
    }

    @Test
    @DisplayName("Should retry from FAILED state")
    fun testRetryFromFailed() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow")

        // Act - fail then retry
        stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION)
        stateMachine.transition(WorkflowStateTransition.FAIL)
        assertEquals(WorkflowMachineState.FAILED, stateMachine.getState())

        // Retry
        assertTrue(stateMachine.transition(WorkflowStateTransition.RETRY))
        assertEquals(WorkflowMachineState.RUNNING, stateMachine.getState())

        // Complete retry
        assertTrue(stateMachine.transition(WorkflowStateTransition.COMMIT))
        assertEquals(WorkflowMachineState.COMMITTED, stateMachine.getState())
    }

    @Test
    @DisplayName("Should track state history")
    fun testStateHistory() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow")

        // Act - perform multiple transitions
        stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION)
        stateMachine.transition(WorkflowStateTransition.PAUSE)
        stateMachine.transition(WorkflowStateTransition.RESUME)
        stateMachine.transition(WorkflowStateTransition.COMMIT)

        // Assert history
        val history = stateMachine.getStateHistory()
        assertEquals(5, history.size)  // CREATED + 4 transitions
        assertEquals(WorkflowMachineState.CREATED, history[0].state)
        assertEquals(WorkflowMachineState.RUNNING, history[1].state)
        assertEquals(WorkflowMachineState.PAUSED, history[2].state)
        assertEquals(WorkflowMachineState.RUNNING, history[3].state)
        assertEquals(WorkflowMachineState.COMMITTED, history[4].state)
    }

    @Test
    @DisplayName("Should provide descriptive state information")
    fun testStateDescription() = runTest {
        // Arrange
        val stateMachine = WorkflowStateMachine("test-workflow-123")
        stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION)

        // Act
        val description = stateMachine.describe()

        // Assert
        assertTrue(description.contains("test-workflow-123"), "Description should include workflow ID")
        assertTrue(description.contains("RUNNING"), "Description should include current state")
    }

    @Test
    @DisplayName("Should handle concurrent workflow executions")
    fun testConcurrentWorkflowExecutions() = runTest {
        // Arrange
        val workflows = (1..5).map { index ->
            TestWorkflowBuilder("concurrent-workflow-$index")
                .addStep("step-1") { operationCounter.increment("STEP1") }
                .addStep("step-2") { operationCounter.increment("STEP2") }
                .build()
        }

        // Act - execute all workflows
        val results = workflows.map { workflow -> workflow.execute() }

        // Assert
        results.forEach { result ->
            assertTrue(result.isSuccessful(), "All workflows should succeed")
            assertEquals(2, result.executedSteps.size)
        }
        assertEquals(5, operationCounter.getCount("STEP1"))
        assertEquals(5, operationCounter.getCount("STEP2"))
    }
}
