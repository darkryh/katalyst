package com.ead.katalyst.transactions.manager

import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.adapter.TransactionAdapterRegistry
import com.ead.katalyst.transactions.config.BackoffStrategy
import com.ead.katalyst.transactions.config.RetryPolicy
import com.ead.katalyst.transactions.config.TransactionConfig
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.exception.DeadlockException
import com.ead.katalyst.transactions.exception.TransactionFailedException
import com.ead.katalyst.transactions.exception.TransactionTimeoutException
import com.ead.katalyst.transactions.exception.isTransient
import com.ead.katalyst.transactions.hooks.TransactionPhase
import com.ead.katalyst.transactions.workflow.CurrentWorkflowContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*
import kotlin.math.min
import kotlin.math.pow
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager as ExposedTransactionManager

/**
 * Database transaction manager for handling suspended transactions with adapter support.
 *
 * This manager wraps Exposed's `newSuspendedTransaction` to provide:
 * - Clean suspend function API
 * - Automatic transaction management (commit/rollback)
 * - Adapter system for decoupled cross-cutting concerns
 * - Event context for queueing events until after commit
 * - IO dispatcher by default for non-blocking operations
 * - Proper error handling and cleanup
 *
 * **Architecture:**
 * Services use this manager to execute repository operations within a transaction.
 * Repositories do NOT have suspended functions - they execute synchronously within
 * the transaction context provided by this manager.
 *
 * Modules register `TransactionAdapter` instances to handle their concerns:
 * - Persistence module registers adapter for DB state
 * - Events module registers adapter for event publishing
 * - Future modules register adapters for their concerns
 *
 * ```
 * Service Layer (suspend):
 *   └─ transactionManager.transaction {
 *       ├─ Repository.findById(id)        // sync, uses connection from context
 *       ├─ validate(entity)               // sync
 *       ├─ Repository.save(entity)        // sync, uses connection from context
 *       └─ eventDispatcher.dispatch(...)  // sync or suspend
 *   }
 * ```
 *
 * **Transaction Lifecycle with Adapters:**
 * ```
 * 1. transaction { block }
 * 2. Create TransactionEventContext
 * 3. Execute BEFORE_BEGIN adapters
 * 4. Begin Exposed transaction
 * 5. Execute AFTER_BEGIN adapters
 * 6. Execute user block
 * 7. Execute BEFORE_COMMIT_VALIDATION adapters (NEW - P0 critical validation)
 * 8. Execute BEFORE_COMMIT adapters (still in TX)
 * 9. Commit database transaction
 * 10. AFTER_COMMIT hooks (outside TX)
 * 11. Return result
 *
 * Or on error:
 * 1-6. (same as above)
 * 7. Exception thrown in block or validation fails
 * 8. ON_ROLLBACK hooks
 * 9. Rollback database transaction
 * 10. AFTER_ROLLBACK hooks
 * 11. Re-throw exception
 * ```
 *
 * **Example Usage:**
 * ```kotlin
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val transactionManager: DatabaseTransactionManager,
 *     private val eventBus: EventBus
 * ) {
 *     override lateinit var transactionManager: DatabaseTransactionManager
 *
 *     suspend fun createUser(dto: CreateUserDTO): User {
 *         // Validate input (sync)
 *         userValidator.validate(dto)
 *
 *         // Run repository operations in transaction
 *         return transactionManager.transaction {
 *             // All these run within the same database transaction
 *             val existing = userRepository.findByEmail(dto.email)
 *             if (existing != null) {
 *                 throw ValidationException("Email already exists")
 *             }
 *
 *             val user = userRepository.save(User.from(dto))
 *
 *             // This event is queued, not published yet
 *             eventBus.publish(UserCreatedEvent(user.id))
 *
 *             user
 *             // Transaction commits here if no exception
 *         }
 *         // After transaction commits: queued events are published
 *     }
 * }
 * ```
 *
 * **Error Handling:**
 * If any exception is thrown within the transaction block, the transaction
 * will be automatically rolled back. The exception will be propagated to the caller.
 *
 * @property database The Exposed Database instance
 * @property adapterRegistry The registry managing transaction adapters
 */
class DatabaseTransactionManager(
    private val database: Database,
    private val adapterRegistry: TransactionAdapterRegistry = TransactionAdapterRegistry()
) : TransactionManager {

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseTransactionManager::class.java)
    }

    /**
     * Executes a block of code within a database transaction with timeout and retry support.
     *
     * **Timeout & Retry Behavior:**
     * - Each attempt has a configurable timeout (default 30 seconds)
     * - Failed attempts are retried with exponential backoff (default 1s, 2s, 4s, 8s...)
     * - Only retryable exceptions trigger retry (transient DB errors, timeouts, deadlocks)
     * - Non-retryable exceptions (validation, auth, constraints) fail immediately
     *
     * **Execution Flow with Retries:**
     * 1. Start attempt #1
     * 2. If timeout: throw TransactionTimeoutException, may retry
     * 3. If transient error: wait backoff delay, retry
     * 4. If non-transient error: fail immediately
     * 5. If success: return result
     * 6. If all retries exhausted: throw TransactionFailedException
     *
     * @param workflowId Optional workflow ID for operation tracking
     * @param config Transaction configuration with timeout, retry policy, isolation level
     * @param T The return type of the block
     * @param block The suspend function to execute within the transaction
     * @return The result of the block
     * @throws TransactionTimeoutException If transaction exceeds timeout (after all retries)
     * @throws TransactionFailedException If all retry attempts failed
     * @throws Exception If block throws non-retryable exception
     */
    override suspend fun <T> transaction(
        workflowId: String?,
        config: TransactionConfig,
        block: suspend Transaction.() -> T
    ): T {
        // If we're already inside an Exposed transaction, just reuse it instead of starting a new one.
        val existingTransaction = ExposedTransactionManager.currentOrNull()
        if (existingTransaction != null) {
            logger.debug(
                "Joining existing transaction context (workflowId={})",
                workflowId ?: CurrentWorkflowContext.get() ?: "unknown"
            )
            return block(existingTransaction)
        }

        // Use provided workflowId or generate a new one
        val txId = workflowId ?: UUID.randomUUID().toString()
        logger.debug("Starting transaction with workflowId: {} (timeout: {})", txId, config.timeout)

        // Set workflow context for auto-tracking
        CurrentWorkflowContext.set(txId)

        // Retry loop with backoff
        var lastException: Exception? = null
        val maxAttempts = config.retryPolicy.maxRetries + 1

        repeat(maxAttempts) { attempt ->
            try {
                // Create transaction event context for queuing events during transaction
                val transactionEventContext = TransactionEventContext()

                // Execute with timeout
                val result = withTimeoutOrNull(config.timeout) {
                    executeTransactionBlock(
                        txId,
                        transactionEventContext,
                        block
                    )
                }

                return result ?: throw TransactionTimeoutException(
                    message = "Transaction timeout after ${config.timeout}",
                    transactionId = txId,
                    attemptNumber = attempt,
                    timeout = config.timeout
                )
            } catch (e: Exception) {
                lastException = e
                val isRetryable = shouldRetry(e, config, attempt, maxAttempts)

                if (isRetryable) {
                    logger.warn(
                        "Attempt {} failed for transaction {} - {}: {}. Retrying...",
                        attempt + 1,
                        txId,
                        e::class.simpleName,
                        e.message
                    )

                    if (attempt < maxAttempts - 1) {
                        val delayMs = calculateBackoffDelay(attempt, config.retryPolicy)
                        logger.debug("Waiting {}ms before retry", delayMs)
                        delay(delayMs)
                    }
                } else {
                    logger.error(
                        "Non-retryable exception in transaction {} - {}: {}",
                        txId,
                        e::class.simpleName,
                        e.message
                    )
                    throw e
                }
            } finally {
                // Clear workflow context on last attempt
                if (attempt == maxAttempts - 1) {
                    CurrentWorkflowContext.clear()
                }
            }
        }

        // All retries exhausted
        CurrentWorkflowContext.clear()
        throw when (lastException) {
            is TransactionTimeoutException -> lastException
            else -> TransactionFailedException(
                message = "Transaction failed after $maxAttempts attempts",
                transactionId = txId,
                finalAttemptNumber = maxAttempts,
                totalRetries = config.retryPolicy.maxRetries,
                cause = lastException
            )
        }
    }

    /**
     * Executes the actual transaction block with event context.
     */
    private suspend fun <T> executeTransactionBlock(
        txId: String,
        transactionEventContext: TransactionEventContext,
        block: suspend Transaction.() -> T
    ): T {
        return try {
            // Phase 1: BEFORE_BEGIN adapters
            logger.debug("Executing BEFORE_BEGIN adapters for workflow: {}", txId)
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_BEGIN, transactionEventContext)

            // Phase 2-6: Execute the transaction with the event context
            val result = withContext(transactionEventContext) {
                // Phase 2: AFTER_BEGIN adapters (within context)
                logger.debug("Executing AFTER_BEGIN adapters")
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_BEGIN, transactionEventContext)

                // DON'T pass dispatcher to preserve coroutine context inheritance
                // The context element (transactionEventContext) must be inherited by the transaction block
                newSuspendedTransaction(null, database) {
                    logger.debug("Transaction context established, executing block")
                    val outcome = block()

                    // Phase 3: BEFORE_COMMIT_VALIDATION adapters (P0 Critical)
                    // NEW: Validate critical aspects (e.g., all pending events have handlers)
                    // This phase MUST succeed or transaction rolls back
                    logger.debug("Executing BEFORE_COMMIT_VALIDATION adapters inside transaction")
                    adapterRegistry.executeAdapters(
                        TransactionPhase.BEFORE_COMMIT_VALIDATION,
                        transactionEventContext,
                        failFast = true
                    )

                    // Phase 4: BEFORE_COMMIT adapters (still in TX)
                    logger.debug("Executing BEFORE_COMMIT adapters inside transaction")
                    adapterRegistry.executeAdapters(
                        TransactionPhase.BEFORE_COMMIT,
                        transactionEventContext,
                        failFast = true
                    )

                    outcome
                }
            }

            // Phase 5: AFTER_COMMIT adapters (after transaction commits)
            logger.debug("Executing AFTER_COMMIT adapters")
            adapterRegistry.executeAdapters(TransactionPhase.AFTER_COMMIT, transactionEventContext)

            logger.info("Transaction succeeded for workflow: {}", txId)
            result
        } catch (e: Exception) {
            logger.error("Transaction failed for workflow: {} - {}", txId, e.message, e)

            // Phase 5: ON_ROLLBACK adapters
            logger.debug("Executing ON_ROLLBACK adapters")
            try {
                withContext(transactionEventContext) {
                    adapterRegistry.executeAdapters(TransactionPhase.ON_ROLLBACK, transactionEventContext)
                }
            } catch (adapterError: Exception) {
                logger.warn("Error in ON_ROLLBACK adapter, continuing rollback", adapterError)
            }

            // Phase 6: AFTER_ROLLBACK adapters
            logger.debug("Executing AFTER_ROLLBACK adapters")
            try {
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_ROLLBACK, transactionEventContext)
            } catch (adapterError: Exception) {
                logger.warn("Error in AFTER_ROLLBACK adapter, continuing", adapterError)
            }

            throw e
        }
    }

    /**
     * Determines if an exception should trigger a retry.
     *
     * Retries are only attempted for transient errors (deadlocks, timeouts, connection issues).
     * Non-retryable exceptions fail immediately.
     */
    private fun shouldRetry(
        exception: Exception,
        config: TransactionConfig,
        attempt: Int,
        maxAttempts: Int
    ): Boolean {
        // Don't retry if we've exhausted all attempts
        if (attempt >= maxAttempts - 1) {
            logger.debug("Maximum retry attempts (${maxAttempts}) reached")
            return false
        }

        // Check if exception is in non-retryable list
        if (config.retryPolicy.nonRetryableExceptions.any { exception::class == it }) {
            logger.debug("Exception ${exception::class.simpleName} is explicitly non-retryable")
            return false
        }

        // Check if exception is in explicitly retryable list
        if (config.retryPolicy.retryableExceptions.any { exception::class == it }) {
            logger.debug("Exception ${exception::class.simpleName} is explicitly retryable")
            return true
        }

        // Check if exception is a general transient error
        val isTransient = exception.isTransient() || isDeadlockException(exception)
        if (isTransient) {
            logger.debug("Exception ${exception::class.simpleName} detected as transient error, retrying")
        }

        return isTransient
    }

    /**
     * Determines if an exception is a database deadlock.
     */
    private fun isDeadlockException(exception: Exception): Boolean {
        return when (exception) {
            is DeadlockException -> true
            is SQLException -> {
                val errorCode = exception.errorCode
                // MySQL deadlock: 1213, lock timeout: 1205
                // PostgreSQL deadlock: 40P01
                errorCode in setOf(1213, 1205, 40001)
            }
            else -> false
        }
    }

    /**
     * Calculates backoff delay for retry attempt.
     *
     * Supports three strategies:
     * - EXPONENTIAL: 1s, 2s, 4s, 8s, 16s... (with jitter to prevent thundering herd)
     * - LINEAR: 1s, 2s, 3s, 4s, 5s...
     * - IMMEDIATE: 0ms (no delay)
     *
     * Formula for exponential: initialDelayMs * 2^attempt (capped at maxDelayMs, plus jitter)
     * Formula for linear: initialDelayMs * (attempt + 1) (capped at maxDelayMs, plus jitter)
     */
    private fun calculateBackoffDelay(
        attempt: Int,
        policy: RetryPolicy
    ): Long {
        val baseDelay = when (policy.backoffStrategy) {
            BackoffStrategy.EXPONENTIAL -> {
                (policy.initialDelayMs * 2.0.pow(attempt.toDouble())).toLong()
            }
            BackoffStrategy.LINEAR -> {
                policy.initialDelayMs * (attempt + 1)
            }
            BackoffStrategy.IMMEDIATE -> {
                0L
            }
        }

        // Cap at maximum delay
        val cappedDelay = min(baseDelay, policy.maxDelayMs)

        // Add jitter: ±(jitterFactor * cappedDelay)
        val jitterAmount = (cappedDelay * policy.jitterFactor).toLong()
        val jitter = (-jitterAmount + Math.random() * 2 * jitterAmount).toLong()

        return cappedDelay + jitter
    }

    /**
     * Registers a transaction adapter.
     *
     * Adapters are executed in priority order during transaction phases.
     * Multiple adapters can be registered; they execute independently.
     *
     * @param adapter The adapter to register
     */
    override fun addAdapter(adapter: TransactionAdapter) {
        adapterRegistry.register(adapter)
    }

    /**
     * Unregisters a transaction adapter.
     *
     * Mainly used for testing/cleanup. In production, adapters typically persist
     * for the application lifetime after registration during DI bootstrap.
     *
     * @param adapter The adapter to remove
     */
    override fun removeAdapter(adapter: TransactionAdapter) {
        adapterRegistry.unregister(adapter)
    }
}
