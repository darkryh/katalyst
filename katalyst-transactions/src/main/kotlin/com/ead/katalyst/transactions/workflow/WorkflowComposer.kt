package com.ead.katalyst.transactions.workflow

import org.slf4j.LoggerFactory

/**
 * Builder/composer for constructing multi-step workflows.
 *
 * Supports:
 * - Chaining multiple operations in sequence
 * - Named checkpoints for recovery
 * - Conditional execution based on previous results
 * - Structured error handling per step
 *
 * **Example**:
 * ```kotlin
 * val workflow = WorkflowComposer("user_registration")
 *     .step("create_account") {
 *         authAccountRepository.save(account)
 *     }
 *     .checkpoint("account_created")
 *     .step("create_profile") {
 *         userProfileRepository.save(profile)
 *     }
 *     .checkpoint("profile_created")
 *     .step("send_welcome_email") {
 *         emailService.sendWelcome(email)
 *     }
 *     .build()
 *
 * transactionManager.transaction(workflow.id) {
 *     workflow.execute()
 * }
 * ```
 *
 * **Checkpoint-based Recovery**:
 * If execution fails after a checkpoint, resume can restart from that checkpoint
 * without re-executing earlier steps.
 */
class WorkflowComposer(
    val workflowName: String,
    val workflowId: String = java.util.UUID.randomUUID().toString()
) {
    private val logger = LoggerFactory.getLogger(WorkflowComposer::class.java)

    private val steps = mutableListOf<WorkflowStep>()
    private val checkpoints = mutableListOf<WorkflowCheckpoint>()

    /**
     * Add a step to the workflow.
     *
     * @param name Step name for logging
     * @param handler The operation to execute
     * @return this for chaining
     */
    fun step(
        name: String,
        handler: suspend () -> Unit
    ): WorkflowComposer {
        steps.add(WorkflowStep(name, handler))
        logger.debug("Added workflow step: {}", name)
        return this
    }

    /**
     * Add a checkpoint (named recovery point).
     *
     * If workflow fails after this checkpoint, execution can resume from here.
     *
     * @param name Checkpoint name
     * @return this for chaining
     */
    fun checkpoint(name: String): WorkflowComposer {
        checkpoints.add(WorkflowCheckpoint(name, steps.size))
        logger.debug("Added workflow checkpoint: {} at step {}", name, steps.size)
        return this
    }

    /**
     * Build the workflow composition.
     *
     * @return An executable workflow
     */
    fun build(): ComposedWorkflow {
        return ComposedWorkflow(
            name = workflowName,
            id = workflowId,
            steps = steps.toList(),
            checkpoints = checkpoints.toList()
        )
    }

    /**
     * Execute the workflow immediately.
     *
     * Must be called within a transaction context.
     *
     * @return Workflow execution result
     */
    suspend fun execute(): WorkflowExecutionResult {
        return build().execute()
    }

    /**
     * Resume workflow from a checkpoint.
     *
     * @param checkpointName The checkpoint to resume from
     * @return Workflow execution result
     */
    suspend fun resumeFrom(checkpointName: String): WorkflowExecutionResult {
        return build().resumeFrom(checkpointName)
    }

    /**
     * Get all steps in this workflow.
     */
    fun getSteps(): List<WorkflowStep> = steps.toList()

    /**
     * Get all checkpoints in this workflow.
     */
    fun getCheckpoints(): List<WorkflowCheckpoint> = checkpoints.toList()
}

/**
 * Represents a workflow step (single operation).
 */
data class WorkflowStep(
    val name: String,
    val handler: suspend () -> Unit
)

/**
 * Represents a checkpoint (named recovery point) in a workflow.
 */
data class WorkflowCheckpoint(
    val name: String,
    val stepIndex: Int  // Index in steps list where to resume
)

/**
 * Represents a compiled/built workflow ready for execution.
 */
class ComposedWorkflow(
    val name: String,
    val id: String,
    val steps: List<WorkflowStep>,
    val checkpoints: List<WorkflowCheckpoint>
) {
    private val logger = LoggerFactory.getLogger(ComposedWorkflow::class.java)

    /**
     * Execute all steps in order.
     *
     * @return Execution result with status and metrics
     */
    suspend fun execute(): WorkflowExecutionResult {
        return executeFrom(0)
    }

    /**
     * Resume execution from a specific checkpoint.
     *
     * @param checkpointName The checkpoint to resume from
     * @return Execution result
     */
    suspend fun resumeFrom(checkpointName: String): WorkflowExecutionResult {
        val checkpoint = checkpoints.firstOrNull { it.name == checkpointName }
            ?: return WorkflowExecutionResult(
                workflowId = id,
                workflowName = name,
                status = ExecutionStatus.FAILED,
                executedSteps = emptyList(),
                error = "Checkpoint '$checkpointName' not found"
            )

        logger.info("Resuming workflow from checkpoint: {}", checkpointName)
        return executeFrom(checkpoint.stepIndex)
    }

    /**
     * Execute starting from a specific step index.
     *
     * @param fromIndex The index to start from
     * @return Execution result
     */
    private suspend fun executeFrom(fromIndex: Int): WorkflowExecutionResult {
        logger.info("Executing workflow: {} with {} steps, starting from index {}", name, steps.size, fromIndex)

        val executedSteps = mutableListOf<ExecutedStep>()
        val startTime = System.currentTimeMillis()

        for (i in fromIndex until steps.size) {
            val step = steps[i]
            val stepStartTime = System.currentTimeMillis()

            try {
                logger.debug("Executing step {}/{}: {}", i + 1, steps.size, step.name)
                step.handler()

                val duration = System.currentTimeMillis() - stepStartTime
                executedSteps.add(ExecutedStep(step.name, true, duration, null))

                logger.debug("Step succeeded: {} ({}ms)", step.name, duration)
            } catch (e: Exception) {
                logger.error("Step failed: {}", step.name, e)
                val duration = System.currentTimeMillis() - stepStartTime
                executedSteps.add(ExecutedStep(step.name, false, duration, e.message))

                // Return failure result - caller can decide whether to retry/resume
                return WorkflowExecutionResult(
                    workflowId = id,
                    workflowName = name,
                    status = ExecutionStatus.FAILED,
                    executedSteps = executedSteps,
                    error = "Step '${step.name}' failed: ${e.message}",
                    totalDurationMs = System.currentTimeMillis() - startTime
                )
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        logger.info("Workflow execution succeeded: {} ({}ms)", name, totalDuration)

        return WorkflowExecutionResult(
            workflowId = id,
            workflowName = name,
            status = ExecutionStatus.SUCCEEDED,
            executedSteps = executedSteps,
            totalDurationMs = totalDuration
        )
    }
}

/**
 * Represents a single executed step with results.
 */
data class ExecutedStep(
    val name: String,
    val succeeded: Boolean,
    val durationMs: Long,
    val errorMessage: String?
)

/**
 * Status of workflow execution.
 */
enum class ExecutionStatus {
    SUCCEEDED,
    FAILED,
    IN_PROGRESS
}

/**
 * Result of workflow execution.
 */
data class WorkflowExecutionResult(
    val workflowId: String,
    val workflowName: String,
    val status: ExecutionStatus,
    val executedSteps: List<ExecutedStep>,
    val error: String? = null,
    val totalDurationMs: Long? = null
) {
    fun isSuccessful(): Boolean = status == ExecutionStatus.SUCCEEDED

    fun getLastExecutedStep(): ExecutedStep? = executedSteps.lastOrNull()

    fun getFailedStep(): ExecutedStep? = executedSteps.firstOrNull { !it.succeeded }

    override fun toString(): String = buildString {
        append("WorkflowExecutionResult(")
        append("id=$workflowId, ")
        append("status=$status, ")
        append("steps=${executedSteps.size}, ")
        if (error != null) append("error=$error, ")
        if (totalDurationMs != null) append("duration=${totalDurationMs}ms")
        append(")")
    }
}
