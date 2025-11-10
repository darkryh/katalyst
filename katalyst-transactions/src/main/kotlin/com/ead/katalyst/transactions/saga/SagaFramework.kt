package com.ead.katalyst.transactions.saga

import java.time.Instant
import java.util.UUID

/**
 * Status of a saga (distributed transaction).
 *
 * - CREATED: Saga initialized but not started
 * - RUNNING: Saga steps are executing
 * - COMMITTED: All steps completed successfully
 * - COMPENSATING: Compensation (rollback) is in progress
 * - COMPENSATED: Compensation completed successfully
 * - FAILED: Saga or compensation failed
 */
enum class SagaStatus {
    CREATED,
    RUNNING,
    COMMITTED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}

/**
 * Result of a completed saga step.
 *
 * Stores the step name, execution result, and timestamp for tracking.
 *
 * @param T The return type of the step
 * @param stepName Unique identifier for this step
 * @param result The result returned by this step
 * @param executedAt Timestamp when step completed
 */
data class SagaStepResult<T>(
    val stepName: String,
    val result: T,
    val executedAt: Instant = Instant.now()
)

/**
 * A single step in a saga (distributed transaction).
 *
 * Each step represents a unit of work that can be executed and compensated.
 * Steps are chained together to form a multi-step transaction.
 *
 * **Example:**
 * ```kotlin
 * object CreateUserStep : SagaStep<User> {
 *     override val stepName = "create-user"
 *
 *     override suspend fun execute(): User {
 *         return userService.create(request)
 *     }
 *
 *     override suspend fun compensate(result: User) {
 *         userService.delete(result.id)
 *     }
 * }
 * ```
 *
 * **Execution Flow:**
 * 1. execute() runs the forward operation
 * 2. If any step fails, compensate() is called in reverse order
 * 3. Compensation must undo the changes made by execute()
 *
 * **Important:**
 * - execute() must be idempotent (safe to retry)
 * - compensate() must be safe to call even if execute() partially failed
 * - Both must be suspend functions for coroutine support
 */
interface SagaStep<T> {
    /**
     * Unique identifier for this step.
     *
     * Used for logging, error reporting, and step identification.
     */
    val stepName: String

    /**
     * Execute the forward operation.
     *
     * This is the main business logic for this step.
     * Must be idempotent - safe to retry if it fails midway.
     *
     * @return The result of step execution
     * @throws Exception If step fails - will trigger compensation
     */
    suspend fun execute(): T

    /**
     * Compensate (rollback) the operation.
     *
     * Called if this step or a later step fails.
     * Must undo the changes made by execute().
     *
     * **Important:**
     * - Must be safe to call even if execute() partially completed
     * - Must not throw exceptions (log errors instead)
     * - Will be called in reverse order of step execution
     *
     * @param result The result returned by execute()
     */
    suspend fun compensate(result: T)
}

/**
 * Runtime context for a saga execution.
 *
 * Tracks the execution state, completed steps, and errors encountered.
 *
 * **Usage:**
 * ```kotlin
 * val context = SagaContext(sagaId = "saga-123")
 * context.status = SagaStatus.RUNNING
 * context.steps.add(SagaStepResult("step1", result))
 * ```
 */
data class SagaContext(
    /**
     * Unique identifier for this saga.
     */
    val sagaId: String = UUID.randomUUID().toString(),

    /**
     * Timestamp when saga execution started.
     */
    val startTime: Instant = Instant.now(),

    /**
     * Timestamp when saga execution ended (null if still running).
     */
    var endTime: Instant? = null,

    /**
     * Current status of the saga.
     */
    var status: SagaStatus = SagaStatus.CREATED,

    /**
     * Results from each completed step.
     * Stored in execution order for compensation traversal.
     */
    val steps: MutableList<SagaStepResult<*>> = mutableListOf(),

    /**
     * All errors encountered during execution or compensation.
     */
    val errors: MutableList<Exception> = mutableListOf()
) {
    /**
     * Check if saga execution was successful.
     */
    fun isSuccessful(): Boolean = status == SagaStatus.COMMITTED

    /**
     * Check if saga is in a terminal state (won't progress further).
     */
    fun isTerminal(): Boolean = status in setOf(
        SagaStatus.COMMITTED,
        SagaStatus.COMPENSATED,
        SagaStatus.FAILED
    )

    /**
     * Get total execution time if saga has completed.
     */
    fun getDurationMs(): Long? {
        return if (endTime != null) {
            endTime!!.toEpochMilli() - startTime.toEpochMilli()
        } else {
            null
        }
    }

    /**
     * Get number of steps that completed successfully.
     */
    fun getCompletedStepCount(): Int = steps.size

    /**
     * Get number of errors encountered.
     */
    fun getErrorCount(): Int = errors.size

    /**
     * Get human-readable summary of saga state.
     */
    fun getSummary(): String {
        return buildString {
            append("Saga(")
            append("id=$sagaId, ")
            append("status=$status, ")
            append("steps=${steps.size}, ")
            append("errors=${errors.size}")
            if (endTime != null) {
                append(", duration=${getDurationMs()}ms")
            }
            append(")")
        }
    }
}

/**
 * Builder for creating saga steps fluently using DSL.
 *
 * **Example:**
 * ```kotlin
 * val step = sagaStep<User>("create-user") {
 *     execute { userService.create(request) }
 *     compensate { userService.delete(it.id) }
 * }
 * ```
 */
class SagaStepBuilder<T>(val stepName: String) {
    private lateinit var executeBlock: suspend () -> T
    private lateinit var compensateBlock: suspend (T) -> Unit

    fun execute(block: suspend () -> T) {
        executeBlock = block
    }

    fun compensate(block: suspend (T) -> Unit) {
        compensateBlock = block
    }

    fun build(): SagaStep<T> {
        return object : SagaStep<T> {
            override val stepName = this@SagaStepBuilder.stepName
            override suspend fun execute(): T = executeBlock()
            override suspend fun compensate(result: T) = compensateBlock(result)
        }
    }
}

/**
 * DSL function for creating saga steps.
 *
 * **Example:**
 * ```kotlin
 * val step = sagaStep<User>("create-user") {
 *     execute { userService.create(request) }
 *     compensate { userService.delete(it.id) }
 * }
 * ```
 */
fun <T> sagaStep(stepName: String, block: SagaStepBuilder<T>.() -> Unit): SagaStep<T> {
    val builder = SagaStepBuilder<T>(stepName)
    builder.block()
    return builder.build()
}
