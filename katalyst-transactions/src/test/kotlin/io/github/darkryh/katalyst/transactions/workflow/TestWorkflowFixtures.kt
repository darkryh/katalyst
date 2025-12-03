package io.github.darkryh.katalyst.transactions.workflow

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test fixtures and mocks for workflow testing.
 *
 * Provides:
 * - In-memory implementations for testing
 * - Test data builders
 * - Mock operations for simulating workflows
 * - Operation tracking for assertions
 */

/**
 * In-memory implementation of OperationLog for testing.
 */
class TestOperationLog : OperationLog {
    private val operations = ConcurrentHashMap<String, MutableList<TransactionOperation>>()
    private val logger = LoggerFactory.getLogger(TestOperationLog::class.java)

    override suspend fun logOperation(
        workflowId: String,
        operationIndex: Int,
        operation: TransactionOperation
    ) {
        operations.computeIfAbsent(workflowId) { mutableListOf() }
            .add(operation)
        logger.debug("Test: Logged operation for workflow {}", workflowId)
    }

    override suspend fun getPendingOperations(workflowId: String): List<TransactionOperation> {
        return operations[workflowId]?.filter { it is SimpleTransactionOperation } ?: emptyList()
    }

    override suspend fun getAllOperations(workflowId: String): List<TransactionOperation> {
        return operations[workflowId] ?: emptyList()
    }

    override suspend fun markAsCommitted(workflowId: String, operationIndex: Int) {
        logger.debug("Test: Marked operation as committed")
    }

    override suspend fun markAllAsCommitted(workflowId: String) {
        logger.debug("Test: Marked all operations as committed")
    }

    override suspend fun markAsUndone(workflowId: String, operationIndex: Int) {
        logger.debug("Test: Marked operation as undone")
    }

    override suspend fun markAsFailed(workflowId: String, operationIndex: Int, error: String) {
        logger.debug("Test: Marked operation as failed: {}", error)
    }

    override suspend fun getFailedOperations(): List<TransactionOperation> {
        return operations.values.flatten()
    }

    override suspend fun deleteOldOperations(beforeTimestamp: Long): Int {
        return 0
    }

    fun getAllLoggedOperations(workflowId: String): List<TransactionOperation> {
        return operations[workflowId] ?: emptyList()
    }

    fun clear() {
        operations.clear()
    }
}

/**
 * In-memory implementation of WorkflowStateManager for testing.
 */
class TestWorkflowStateManager : WorkflowStateManager {
    private val workflows = ConcurrentHashMap<String, WorkflowState>()
    private val logger = LoggerFactory.getLogger(TestWorkflowStateManager::class.java)

    override suspend fun startWorkflow(workflowId: String, workflowName: String) {
        workflows[workflowId] = WorkflowState(
            workflowId = workflowId,
            workflowName = workflowName,
            status = WorkflowStatus.STARTED,
            totalOperations = 0,
            failedAtOperation = null,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            completedAt = null
        )
        logger.debug("Test: Started workflow {}", workflowId)
    }

    override suspend fun commitWorkflow(workflowId: String) {
        workflows[workflowId]?.let { workflow ->
            workflows[workflowId] = workflow.copy(
                status = WorkflowStatus.COMMITTED,
                completedAt = System.currentTimeMillis()
            )
        }
        logger.debug("Test: Committed workflow {}", workflowId)
    }

    override suspend fun failWorkflow(workflowId: String, failedAtOperation: Int, error: String) {
        workflows[workflowId]?.let { workflow ->
            workflows[workflowId] = workflow.copy(
                status = WorkflowStatus.FAILED,
                failedAtOperation = failedAtOperation,
                errorMessage = error,
                completedAt = System.currentTimeMillis()
            )
        }
        logger.debug("Test: Failed workflow {}: {}", workflowId, error)
    }

    override suspend fun markAsUndone(workflowId: String) {
        workflows[workflowId]?.let { workflow ->
            workflows[workflowId] = workflow.copy(
                status = WorkflowStatus.UNDONE,
                completedAt = System.currentTimeMillis()
            )
        }
        logger.debug("Test: Marked workflow as undone {}", workflowId)
    }

    override suspend fun getWorkflowState(workflowId: String): WorkflowState? {
        return workflows[workflowId]
    }

    override suspend fun getFailedWorkflows(): List<WorkflowState> {
        return workflows.values.filter { it.status == WorkflowStatus.FAILED }
    }

    override suspend fun deleteOldWorkflows(beforeTimestamp: Long): Int {
        return 0
    }

    fun clear() {
        workflows.clear()
    }
}

/**
 * Simple in-memory test undo engine that tracks operations.
 */
class TestUndoEngine : UndoEngine {
    private val undoneOperations = mutableListOf<TransactionOperation>()
    private val logger = LoggerFactory.getLogger(TestUndoEngine::class.java)

    override suspend fun undoWorkflow(
        workflowId: String,
        operations: List<TransactionOperation>
    ): UndoEngine.UndoResult {
        logger.debug("Test: Undoing {} operations for workflow {}", operations.size, workflowId)

        val results = operations.reversed().map { op ->
            undoneOperations.add(op)
            UndoEngine.UndoOperationResult(
                operationIndex = op.operationIndex,
                operationType = op.operationType,
                resourceType = op.resourceType,
                succeeded = true
            )
        }

        return UndoEngine.UndoResult(
            workflowId = workflowId,
            totalOperations = operations.size,
            succeededCount = results.size,
            failedCount = 0,
            results = results
        )
    }

    fun getUndoneOperations(): List<TransactionOperation> = undoneOperations.toList()

    fun clear() {
        undoneOperations.clear()
    }
}

/**
 * Test data builder for creating test workflows.
 */
class TestWorkflowBuilder(val name: String = "test-workflow") {
    private val steps = mutableListOf<Pair<String, suspend () -> Unit>>()
    private val expectedResults = mutableListOf<String>()

    fun addStep(stepName: String, action: suspend () -> Unit): TestWorkflowBuilder {
        steps.add(stepName to action)
        return this
    }

    fun expectResult(result: String): TestWorkflowBuilder {
        expectedResults.add(result)
        return this
    }

    fun build(): ComposedWorkflow {
        val composer = WorkflowComposer(name)
        steps.forEach { (stepName, action) ->
            composer.step(stepName, action)
        }
        return composer.build()
    }

    fun buildWithCheckpoints(): ComposedWorkflow {
        val composer = WorkflowComposer(name)
        var stepCount = 0
        steps.forEach { (stepName, action) ->
            composer.step(stepName, action)
            stepCount++
            if (stepCount % 2 == 0 && stepCount < steps.size) {
                composer.checkpoint("checkpoint_$stepCount")
            }
        }
        return composer.build()
    }
}

/**
 * Operation counter for tracking operations during tests.
 */
class OperationCounter {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun increment(operationType: String) {
        counts.computeIfAbsent(operationType) { AtomicInteger(0) }
            .incrementAndGet()
    }

    fun getCount(operationType: String): Int {
        return counts[operationType]?.get() ?: 0
    }

    fun getTotalCount(): Int {
        return counts.values.sumOf { it.get() }
    }

    fun reset() {
        counts.clear()
    }

    fun getCounts(): Map<String, Int> {
        return counts.mapValues { it.value.get() }
    }
}

/**
 * Test operation that can be configured to succeed or fail.
 */
class ConfigurableTestOperation(
    override val workflowId: String = "test-workflow",
    override val operationType: String,
    override val resourceType: String,
    override val resourceId: String,
    override val operationIndex: Int = 0,
    override val operationData: Map<String, Any?>? = null,
    override val undoData: Map<String, Any?>? = null,
    private val shouldFail: Boolean = false,
    private val failureMessage: String? = null
) : TransactionOperation {

    override suspend fun undo(): Boolean {
        return !shouldFail
    }

    fun fail(): Boolean = shouldFail
}

/**
 * State machine state verifier for testing.
 */
class StateMachineVerifier(private val stateMachine: WorkflowStateMachine) {
    private val transitionLog = mutableListOf<Pair<WorkflowMachineState, WorkflowMachineState>>()

    init {
        stateMachine.onStateChange { from, to ->
            transitionLog.add(from to to)
        }
    }

    fun assertStateIs(expectedState: WorkflowMachineState) {
        val currentState = stateMachine.getState()
        if (currentState != expectedState) {
            throw AssertionError(
                "Expected state $expectedState but was $currentState"
            )
        }
    }

    fun assertTransitionHappened(from: WorkflowMachineState, to: WorkflowMachineState) {
        if (!transitionLog.contains(from to to)) {
            throw AssertionError(
                "Expected transition $from -> $to but did not happen. " +
                "Transitions: $transitionLog"
            )
        }
    }

    fun assertCanTransition(transition: WorkflowStateTransition): Boolean {
        val beforeState = stateMachine.getState()
        val canTransition = stateMachine.transition(transition)
        if (canTransition) {
            stateMachine.getState()  // Move to new state
        } else {
            // Revert if we peeked
        }
        return canTransition
    }

    fun getTransitionLog(): List<Pair<WorkflowMachineState, WorkflowMachineState>> {
        return transitionLog.toList()
    }
}

/**
 * Test metric collector.
 */
class TestMetricCollector {
    private val metrics = ConcurrentHashMap<String, AtomicInteger>()

    fun recordMetric(name: String) {
        metrics.computeIfAbsent(name) { AtomicInteger(0) }
            .incrementAndGet()
    }

    fun getMetric(name: String): Int {
        return metrics[name]?.get() ?: 0
    }

    fun getAllMetrics(): Map<String, Int> {
        return metrics.mapValues { it.value.get() }
    }

    fun reset() {
        metrics.clear()
    }
}
