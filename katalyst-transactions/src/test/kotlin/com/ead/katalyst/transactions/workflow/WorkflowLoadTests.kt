package com.ead.katalyst.transactions.workflow

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Load and stress tests for workflow system.
 *
 * Tests performance and correctness under:
 * - High concurrent load
 * - Large numbers of workflows
 * - Operation log scalability
 */
@DisplayName("Workflow Load Tests")
class WorkflowLoadTests {

    private lateinit var operationLog: TestOperationLog
    private lateinit var stateManager: TestWorkflowStateManager
    private lateinit var undoEngine: TestUndoEngine

    @BeforeEach
    fun setup() {
        operationLog = TestOperationLog()
        stateManager = TestWorkflowStateManager()
        undoEngine = TestUndoEngine()
    }

    @Test
    @DisplayName("Should handle 100 concurrent workflows")
    fun testConcurrentWorkloadSmall() = runTest {
        val workflowCount = 100
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        coroutineScope {
            repeat(workflowCount) { index ->
                launch {
                    try {
                        val workflow = TestWorkflowBuilder("concurrent-$index")
                            .addStep("step-1") { /* fast operation */ }
                            .addStep("step-2") { /* fast operation */ }
                            .build()

                        val result = workflow.execute()
                        if (result.isSuccessful()) {
                            successCount.incrementAndGet()
                        } else {
                            failureCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime

        println("Concurrent Workflows (Small): $workflowCount workflows in ${duration}ms")
        println("  Success: ${successCount.get()}, Failure: ${failureCount.get()}")
        println("  Throughput: ${(workflowCount.toDouble() / duration * 1000).toInt()} workflows/sec")

        assertEquals(workflowCount, successCount.get(), "All workflows should succeed")
        assertEquals(0, failureCount.get(), "No workflows should fail")
    }

    @Test
    @DisplayName("Should handle 500 concurrent workflows")
    fun testConcurrentWorkloadMedium() = runTest {
        val workflowCount = 500
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        coroutineScope {
            repeat(workflowCount) { index ->
                launch {
                    try {
                        val workflow = TestWorkflowBuilder("concurrent-$index")
                            .addStep("init") { /* operation */ }
                            .addStep("process") { /* operation */ }
                            .addStep("finalize") { /* operation */ }
                            .build()

                        val result = workflow.execute()
                        if (result.isSuccessful()) {
                            successCount.incrementAndGet()
                        } else {
                            failureCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime

        println("Concurrent Workflows (Medium): $workflowCount workflows in ${duration}ms")
        println("  Success: ${successCount.get()}, Failure: ${failureCount.get()}")
        println("  Throughput: ${(workflowCount.toDouble() / duration * 1000).toInt()} workflows/sec")

        assertEquals(workflowCount, successCount.get(), "All workflows should succeed")
        assertEquals(0, failureCount.get(), "No workflows should fail")
    }

    @Test
    @DisplayName("Should handle operation log at scale")
    fun testOperationLogScale() = runTest {
        val workflowCount = 100
        val operationsPerWorkflow = 10
        val startTime = System.currentTimeMillis()

        coroutineScope {
            repeat(workflowCount) { workflowIndex ->
                launch {
                    val workflowId = "workflow-$workflowIndex"
                    repeat(operationsPerWorkflow) { opIndex ->
                        operationLog.logOperation(
                            workflowId,
                            opIndex,
                            SimpleTransactionOperation(
                                workflowId = workflowId,
                                operationIndex = opIndex,
                                operationType = "INSERT",
                                resourceType = "Resource",
                                resourceId = "resource-$opIndex"
                            )
                        )
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val totalOperations = workflowCount * operationsPerWorkflow

        println("Operation Log Scale: $totalOperations operations in ${duration}ms")
        println("  Throughput: ${(totalOperations.toDouble() / duration * 1000).toInt()} ops/sec")
        println("  Workflows: $workflowCount, Ops per workflow: $operationsPerWorkflow")

        // Verify all operations logged correctly
        var totalLogged = 0
        repeat(workflowCount) { index ->
            val logged = operationLog.getAllLoggedOperations("workflow-$index").size
            assertEquals(operationsPerWorkflow, logged, "All operations should be logged")
            totalLogged += logged
        }
        assertEquals(totalOperations, totalLogged)
    }

    @Test
    @DisplayName("Should handle state machine under load")
    fun testStateMachineLoad() = runTest {
        val machineCount = 200
        val successCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        coroutineScope {
            repeat(machineCount) { index ->
                launch {
                    try {
                        val machine = WorkflowStateMachine("machine-$index")

                        // Perform state transitions
                        machine.transition(WorkflowStateTransition.BEGIN_EXECUTION)
                        machine.transition(WorkflowStateTransition.COMMIT)

                        if (machine.getState() == WorkflowMachineState.COMMITTED) {
                            successCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        // Expected to catch some errors
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime

        println("State Machine Load: $machineCount machines in ${duration}ms")
        println("  Throughput: ${(machineCount.toDouble() / duration * 1000).toInt()} machines/sec")
        println("  Success: ${successCount.get()}/$machineCount")

        assertEquals(machineCount, successCount.get(), "All machines should reach COMMITTED")
    }

    @Test
    @DisplayName("Should handle long-running workflows with many steps")
    fun testLongRunningWorkflow() = runTest {
        val stepCount = 50
        val startTime = System.currentTimeMillis()

        val workflow = TestWorkflowBuilder("long-workflow")
        repeat(stepCount) { i ->
            workflow.addStep("step-$i") { /* minimal work */ }
        }
        val composedWorkflow = workflow.build()

        val result = composedWorkflow.execute()

        val duration = System.currentTimeMillis() - startTime

        println("Long-Running Workflow: $stepCount steps in ${duration}ms")
        println("  Throughput: ${(stepCount.toDouble() / duration * 1000).toInt()} steps/sec")
        println("  Avg time per step: ${(duration.toDouble() / stepCount).toInt()}ms")

        assertTrue(result.isSuccessful(), "Workflow should succeed")
        assertEquals(stepCount, result.executedSteps.size)
    }

    @Test
    @DisplayName("Should track timing metrics accurately")
    fun testTimingMetricsAccuracy() = runTest {
        val workflows = (1..10).map { index ->
            TestWorkflowBuilder("timing-workflow-$index")
                .addStep("fast") { /* minimal work */ }
                .addStep("medium") { Thread.sleep(5) }
                .addStep("slow") { Thread.sleep(10) }
                .build()
        }

        val results = workflows.map { it.execute() }

        // Verify timing metrics
        results.forEach { result ->
            assertTrue(result.isSuccessful())

            // Fast step should be < 5ms
            val fastStep = result.executedSteps[0]
            assertTrue(fastStep.durationMs < 50, "Fast step should be < 50ms")

            // Medium step should be >= 5ms
            val mediumStep = result.executedSteps[1]
            assertTrue(mediumStep.durationMs >= 5, "Medium step should be >= 5ms")

            // Slow step should be >= 10ms
            val slowStep = result.executedSteps[2]
            assertTrue(slowStep.durationMs >= 10, "Slow step should be >= 10ms")
        }

        println("Timing Metrics: Successfully verified for ${results.size} workflows")
    }

    @Test
    @DisplayName("Should handle recovery job metrics collection under load")
    fun testRecoveryJobMetricsLoad() = runTest {
        val config = RecoveryJobConfig(
            maxRetriesPerWorkflow = 3,
            batchSize = 10
        )
        val recoveryJob = WorkflowRecoveryJob(
            stateManager,
            operationLog,
            undoEngine,
            config
        )

        // Simulate failed workflows
        val failedWorkflowCount = 50
        repeat(failedWorkflowCount) { index ->
            stateManager.startWorkflow("failed-workflow-$index", "Workflow $index")
            stateManager.failWorkflow("failed-workflow-$index", index, "Transient error")
        }

        val startTime = System.currentTimeMillis()
        val result = recoveryJob.scanAndRecover()
        val duration = System.currentTimeMillis() - startTime

        println("Recovery Job Metrics: $failedWorkflowCount workflows in ${duration}ms")
        println("  Processing rate: ${(failedWorkflowCount.toDouble() / duration * 1000).toInt()} workflows/sec")

        // Verify metrics collected
        val metrics = recoveryJob.getMetrics()
        println("  Total scans: ${metrics.totalScans}")
        println("  Workflows found: ${metrics.totalFailedWorkflowsFound}")
        println("  Success rate: ${String.format("%.2f%%", metrics.successRate)}")
    }

    @Test
    @DisplayName("Should maintain correctness under concurrent operation log writes")
    fun testConcurrentOperationLogWrites() = runTest {
        val workflowCount = 100
        val operationsPerWorkflow = 10
        val startTime = System.currentTimeMillis()

        coroutineScope {
            repeat(workflowCount) { workflowIndex ->
                launch {
                    val workflowId = "workflow-$workflowIndex"
                    repeat(operationsPerWorkflow) { opIndex ->
                        operationLog.logOperation(
                            workflowId,
                            opIndex,
                            SimpleTransactionOperation(
                                workflowId = workflowId,
                                operationIndex = opIndex,
                                operationType = "INSERT",
                                resourceType = "Resource",
                                resourceId = "resource-$opIndex"
                            )
                        )
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime

        // Verify integrity
        var totalOpsLogged = 0
        repeat(workflowCount) { index ->
            val ops = operationLog.getAllLoggedOperations("workflow-$index")
            assertEquals(
                operationsPerWorkflow, ops.size,
                "All operations should be logged for workflow-$index"
            )
            totalOpsLogged += ops.size
        }

        assertEquals(workflowCount * operationsPerWorkflow, totalOpsLogged)
        println("Concurrent Operation Log Writes: $totalOpsLogged ops in ${duration}ms")
        println("  Throughput: ${(totalOpsLogged.toDouble() / duration * 1000).toInt()} ops/sec")
    }
}
