package com.ead.katalyst.transactions.saga

import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Orchestrator for executing multi-step sagas (distributed transactions).
 *
 * A saga coordinates multiple steps across services/databases.
 * If any step fails, compensation (rollback) happens automatically in reverse order.
 *
 * **Execution Flow:**
 * ```
 * 1. Create orchestrator
 * 2. Execute steps in order
 *    ├─ Step 1: Create User ✓
 *    ├─ Step 2: Create Profile ✓
 *    └─ Step 3: Send Email ✗ FAILS
 * 3. Automatic compensation in reverse order
 *    ├─ Undo Step 2: Delete Profile
 *    └─ Undo Step 1: Delete User
 * 4. Return failure status
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val saga = SagaOrchestrator(sagaId = "user-registration")
 *
 * try {
 *     val user = saga.step(CreateUserStep)
 *     val profile = saga.step(CreateProfileStep(user.id))
 *     saga.step(SendWelcomeEmailStep(user.email))
 *     saga.commit()
 * } catch (e: Exception) {
 *     logger.error("Saga failed: ${e.message}")
 *     // Compensation automatically triggered
 * }
 * ```
 *
 * **Thread Safety:**
 * Not thread-safe by default. Each saga should be used by a single coroutine.
 *
 * **Memory:**
 * - Keeps results from all executed steps for compensation
 * - Suitable for sagas with < 100 steps
 * - For large sagas, consider checkpointing intermediate results
 */
class SagaOrchestrator(
    sagaId: String = UUID.randomUUID().toString(),
    private val context: SagaContext = SagaContext(sagaId)
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SagaOrchestrator::class.java)
    }

    /**
     * List of compensation tasks (step + result) for rollback.
     * Stored as lambdas to avoid type erasure issues.
     */
    private val compensationTasks = mutableListOf<suspend () -> Unit>()

    /**
     * Execute a single step in the saga.
     *
     * Step result is stored for compensation if later steps fail.
     * If step fails, compensation is immediately triggered.
     *
     * **Example:**
     * ```kotlin
     * val user = saga.step(CreateUserStep)
     * val profile = saga.step(CreateProfileStep(user.id))
     * ```
     *
     * @param step The step to execute
     * @return The result from step.execute()
     * @throws Exception If step fails - automatically triggers compensation
     */
    suspend fun <T> step(step: SagaStep<T>): T {
        logger.info("[{}] Starting saga step: {}", context.sagaId, step.stepName)

        try {
            // Execute the step
            val result = step.execute()
            logger.debug("[{}] Step succeeded: {}", context.sagaId, step.stepName)

            // Store result for compensation
            val stepResult = SagaStepResult(step.stepName, result)
            context.steps.add(stepResult)

            // Store compensation task as lambda to avoid type erasure
            compensationTasks.add {
                try {
                    logger.debug("[{}] Compensating step: {}", context.sagaId, step.stepName)
                    step.compensate(result)
                    logger.debug("[{}] Step compensated: {}", context.sagaId, step.stepName)
                } catch (e: Exception) {
                    logger.error(
                        "[{}] Compensation failed for step: {} - {}",
                        context.sagaId,
                        step.stepName,
                        e.message,
                        e
                    )
                    context.errors.add(e)
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("[{}] Step failed: {} - {}", context.sagaId, step.stepName, e.message, e)
            context.errors.add(e)

            // Trigger compensation
            context.status = SagaStatus.COMPENSATING
            compensateAllSteps()

            throw e
        }
    }

    /**
     * Mark saga as successfully completed.
     *
     * Call this after all steps have executed successfully.
     * If any step fails, compensation is automatic - don't call this.
     *
     * **Example:**
     * ```kotlin
     * try {
     *     saga.step(step1)
     *     saga.step(step2)
     *     saga.commit()  // All steps succeeded
     * } catch (e: Exception) {
     *     // Compensation already triggered
     * }
     * ```
     */
    fun commit() {
        context.status = SagaStatus.COMMITTED
        context.endTime = java.time.Instant.now()
        logger.info("[{}] Saga committed successfully: {}", context.sagaId, context.getSummary())
    }

    /**
     * Trigger manual compensation (rollback).
     *
     * Normally not called directly - compensation happens automatically on failure.
     * Can be used to explicitly rollback a saga that's in progress.
     */
    suspend fun compensate() {
        logger.info("[{}] Manual compensation requested", context.sagaId)
        context.status = SagaStatus.COMPENSATING
        compensateAllSteps()
    }

    /**
     * Get the current saga context.
     *
     * @return SagaContext with current status, steps, and errors
     */
    fun getContext(): SagaContext = context

    /**
     * Get saga execution status.
     *
     * @return Current SagaStatus
     */
    fun getStatus(): SagaStatus = context.status

    /**
     * Check if saga execution was successful.
     *
     * @return true if status is COMMITTED
     */
    fun isSuccessful(): Boolean = context.isSuccessful()

    /**
     * Compensate all executed steps in reverse order.
     *
     * Called automatically on step failure.
     * Compensation happens in reverse order of execution.
     *
     * **Example:**
     * If steps executed: [Step1, Step2, Step3]
     * Compensation order: [Step3, Step2, Step1]
     */
    private suspend fun compensateAllSteps() {
        logger.info("[{}] Compensating {} steps", context.sagaId, compensationTasks.size)

        if (compensationTasks.isEmpty()) {
            context.status = SagaStatus.COMPENSATED
            context.endTime = java.time.Instant.now()
            return
        }

        // Compensate in reverse order
        compensationTasks.reversed().forEach { task ->
            task()
        }

        context.status = SagaStatus.COMPENSATED
        context.endTime = java.time.Instant.now()
        logger.info("[{}] Compensation completed: {}", context.sagaId, context.getSummary())
    }
}

/**
 * Builder for creating sagas fluently.
 *
 * **Example:**
 * ```kotlin
 * val result = SagaBuilder("user-registration")
 *     .step(CreateUserStep)
 *     .step(CreateProfileStep(userId))
 *     .step(SendEmailStep(email))
 *     .execute()
 * ```
 */
class SagaBuilder(
    private val sagaId: String = UUID.randomUUID().toString(),
    private val orchestrator: SagaOrchestrator = SagaOrchestrator(sagaId)
) {
    private val steps = mutableListOf<SagaStep<*>>()

    fun step(step: SagaStep<*>): SagaBuilder {
        steps.add(step)
        return this
    }

    suspend fun execute(): SagaContext {
        try {
            steps.forEach { step ->
                @Suppress("UNCHECKED_CAST")
                val typedStep = step as SagaStep<Any>
                orchestrator.step(typedStep)
            }
            orchestrator.commit()
        } catch (e: Exception) {
            // Compensation already triggered by orchestrator
        }
        return orchestrator.getContext()
    }
}

/**
 * DSL extension for creating sagas.
 *
 * **Example:**
 * ```kotlin
 * val result = saga("user-registration") {
 *     step(CreateUserStep)
 *     step(CreateProfileStep(userId))
 *     execute()
 * }
 * ```
 */
fun saga(sagaId: String = UUID.randomUUID().toString()): SagaBuilder {
    return SagaBuilder(sagaId)
}
