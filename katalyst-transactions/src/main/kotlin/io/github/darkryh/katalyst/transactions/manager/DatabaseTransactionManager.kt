package io.github.darkryh.katalyst.transactions.manager

import io.github.darkryh.katalyst.transactions.adapter.TransactionAdapter
import io.github.darkryh.katalyst.transactions.adapter.TransactionAdapterRegistry
import io.github.darkryh.katalyst.transactions.config.BackoffStrategy
import io.github.darkryh.katalyst.transactions.config.RetryPolicy
import io.github.darkryh.katalyst.transactions.config.TransactionExceptionSeverity
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.context.TransactionEventContext
import io.github.darkryh.katalyst.transactions.context.TransactionScopeContext
import io.github.darkryh.katalyst.transactions.context.TransactionScopeState
import io.github.darkryh.katalyst.transactions.exception.DeadlockException
import io.github.darkryh.katalyst.transactions.exception.TransactionFailedException
import io.github.darkryh.katalyst.transactions.exception.TransactionTimeoutException
import io.github.darkryh.katalyst.transactions.exception.isTransient
import io.github.darkryh.katalyst.transactions.hooks.TransactionPhase
import io.github.darkryh.katalyst.transactions.workflow.CurrentWorkflowContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import kotlin.reflect.KClass
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
    private val adapterRegistry: TransactionAdapterRegistry = TransactionAdapterRegistry(),
    private val defaultTransactionConfig: TransactionConfig = TransactionConfig()
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
        config: TransactionConfig?,
        block: suspend Transaction.() -> T
    ): T {
        val activeConfig = config ?: defaultTransactionConfig
        val currentScope = currentCoroutineContext()[TransactionScopeContext]

        // Join only when both scope and connection are still active/valid.
        val existingTransaction = ExposedTransactionManager.currentOrNull()
        if (existingTransaction != null && currentScope != null && currentScope.state == TransactionScopeState.ACTIVE) {
            if (!isTransactionJoinable(existingTransaction)) {
                currentScope.state = TransactionScopeState.CLOSED
                logger.warn(
                    "Existing transaction scope found but connection is invalid/closed; starting a new root transaction " +
                        "(workflowId={}, txScopeId={})",
                    workflowId ?: currentScope.workflowId ?: CurrentWorkflowContext.get() ?: "unknown",
                    currentScope.transactionId
                )
            } else {
                currentScope.depth += 1
                logger.debug(
                    "Joining existing transaction context (workflowId={}, txScopeId={}, depth={})",
                    workflowId ?: currentScope.workflowId ?: CurrentWorkflowContext.get() ?: "unknown",
                    currentScope.transactionId,
                    currentScope.depth
                )
                return try {
                    block(existingTransaction)
                } finally {
                    currentScope.depth -= 1
                }
            }
        } else if (existingTransaction != null && currentScope == null) {
            logger.warn(
                "Exposed current transaction exists without scope context; treating as non-joinable and starting new root transaction " +
                    "(workflowId={})",
                workflowId ?: CurrentWorkflowContext.get() ?: "unknown"
            )
        } else if (existingTransaction != null && currentScope?.state != TransactionScopeState.ACTIVE) {
            logger.warn(
                "Exposed current transaction exists but scope state is {}; treating as non-joinable and starting new root transaction " +
                    "(workflowId={}, txScopeId={})",
                currentScope?.state,
                workflowId ?: currentScope?.workflowId ?: CurrentWorkflowContext.get() ?: "unknown",
                currentScope?.transactionId ?: "unknown"
            )
        }

        val previousWorkflowId = CurrentWorkflowContext.get()
        val resolvedWorkflowId = workflowId ?: previousWorkflowId ?: UUID.randomUUID().toString()
        CurrentWorkflowContext.set(resolvedWorkflowId)

        try {
            // Retry loop with backoff
            var lastException: Exception? = null
            val maxAttempts = activeConfig.retryPolicy.maxRetries + 1

            repeat(maxAttempts) { attempt ->
                try {
                    // Create transaction contexts for this attempt.
                    val transactionEventContext = TransactionEventContext()
                    val transactionScopeContext = TransactionScopeContext(
                        transactionId = UUID.randomUUID().toString(),
                        workflowId = resolvedWorkflowId
                    )

                    // Use exception-based timeout detection so nullable transaction results
                    // are not misclassified as timeouts.
                    return withTimeout(activeConfig.timeout) {
                        executeTransactionBlock(
                            txId = resolvedWorkflowId,
                            config = activeConfig,
                            transactionEventContext = transactionEventContext,
                            transactionScopeContext = transactionScopeContext,
                            block = block
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    val timeoutException = TransactionTimeoutException(
                        message = "Transaction timeout after ${activeConfig.timeout}",
                        transactionId = resolvedWorkflowId,
                        attemptNumber = attempt,
                        timeout = activeConfig.timeout,
                        cause = e
                    )
                    lastException = timeoutException
                    val isRetryable = shouldRetry(timeoutException, activeConfig, attempt, maxAttempts)

                    if (isRetryable) {
                        logger.warn(
                            "Attempt {} failed for transaction {} - {}: {}. Retrying...",
                            attempt + 1,
                            resolvedWorkflowId,
                            timeoutException::class.simpleName,
                            timeoutException.message
                        )

                        if (attempt < maxAttempts - 1) {
                            val delayMs = calculateBackoffDelay(attempt, activeConfig.retryPolicy)
                            logger.debug("Waiting {}ms before retry", delayMs)
                            delay(delayMs)
                        }
                    } else {
                        logNonRetryableException(
                            transactionId = resolvedWorkflowId,
                            exception = timeoutException,
                            config = activeConfig
                        )
                        throw timeoutException
                    }
                } catch (e: Exception) {
                    lastException = e
                    val isRetryable = shouldRetry(e, activeConfig, attempt, maxAttempts)

                    if (isRetryable) {
                        logger.warn(
                            "Attempt {} failed for transaction {} - {}: {}. Retrying...",
                            attempt + 1,
                            resolvedWorkflowId,
                            e::class.simpleName,
                            e.message
                        )

                        if (attempt < maxAttempts - 1) {
                            val delayMs = calculateBackoffDelay(attempt, activeConfig.retryPolicy)
                            logger.debug("Waiting {}ms before retry", delayMs)
                            delay(delayMs)
                        }
                    } else {
                        logNonRetryableException(
                            transactionId = resolvedWorkflowId,
                            exception = e,
                            config = activeConfig
                        )
                        throw e
                    }
                }
            }

            // All retries exhausted
            throw when (lastException) {
                is TransactionTimeoutException -> lastException
                else -> TransactionFailedException(
                    message = "Transaction failed after $maxAttempts attempts",
                    transactionId = resolvedWorkflowId,
                    finalAttemptNumber = maxAttempts,
                    totalRetries = activeConfig.retryPolicy.maxRetries,
                    cause = lastException
                )
            }
        } finally {
            if (previousWorkflowId != null) {
                CurrentWorkflowContext.set(previousWorkflowId)
            } else {
                CurrentWorkflowContext.clear()
            }
        }
    }

    internal fun isTransactionJoinable(transaction: JdbcTransaction): Boolean {
        return try {
            val rawConnection = transaction.connection.connection
            val jdbcConnection = rawConnection as? Connection ?: return false
            if (jdbcConnection.isClosed) {
                return false
            }
            // Drivers can throw for isValid; treat that as "unknown but usable".
            runCatching { jdbcConnection.isValid(1) }.getOrElse { true }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes the actual transaction block with event context.
     */
    private suspend fun <T> executeTransactionBlock(
        txId: String,
        config: TransactionConfig,
        transactionEventContext: TransactionEventContext,
        transactionScopeContext: TransactionScopeContext,
        block: suspend Transaction.() -> T
    ): T {
        return try {
            // Phase 1: BEFORE_BEGIN adapters
            if (config.phaseLoggingEnabled) {
                logger.debug("Executing BEFORE_BEGIN adapters for workflow: {}", txId)
            }
            adapterRegistry.executeAdapters(
                TransactionPhase.BEFORE_BEGIN,
                transactionEventContext,
                phaseLoggingEnabled = config.phaseLoggingEnabled
            )

            // Phase 2-6: Execute the transaction with contexts
            val result = withContext(transactionEventContext + transactionScopeContext) {
                // Phase 2: AFTER_BEGIN adapters (within context)
                if (config.phaseLoggingEnabled) {
                    logger.debug("Executing AFTER_BEGIN adapters")
                }
                adapterRegistry.executeAdapters(
                    TransactionPhase.AFTER_BEGIN,
                    transactionEventContext,
                    phaseLoggingEnabled = config.phaseLoggingEnabled
                )

                // DON'T pass dispatcher to preserve coroutine context inheritance
                // The context elements must be inherited by the transaction block
                suspendTransaction(database) {
                    if (config.phaseLoggingEnabled) {
                        logger.debug(
                            "Transaction context established, executing block (workflowId={}, txScopeId={})",
                            txId,
                            transactionScopeContext.transactionId
                        )
                    }
                    val outcome = block()

                    // Phase 3: BEFORE_COMMIT_VALIDATION adapters (P0 Critical)
                    // NEW: Validate critical aspects (e.g., all pending events have handlers)
                    // This phase MUST succeed or transaction rolls back
                    if (config.phaseLoggingEnabled) {
                        logger.debug("Executing BEFORE_COMMIT_VALIDATION adapters inside transaction")
                    }
                    adapterRegistry.executeAdapters(
                        TransactionPhase.BEFORE_COMMIT_VALIDATION,
                        transactionEventContext,
                        failFast = true,
                        phaseLoggingEnabled = config.phaseLoggingEnabled
                    )

                    // Phase 4: BEFORE_COMMIT adapters (still in TX)
                    if (config.phaseLoggingEnabled) {
                        logger.debug("Executing BEFORE_COMMIT adapters inside transaction")
                    }
                    adapterRegistry.executeAdapters(
                        TransactionPhase.BEFORE_COMMIT,
                        transactionEventContext,
                        failFast = true,
                        phaseLoggingEnabled = config.phaseLoggingEnabled
                    )

                    outcome
                }
            }

            // Phase 5: AFTER_COMMIT adapters (after transaction commits)
            if (config.phaseLoggingEnabled) {
                logger.debug("Executing AFTER_COMMIT adapters")
            }
            adapterRegistry.executeAdapters(
                TransactionPhase.AFTER_COMMIT,
                transactionEventContext,
                phaseLoggingEnabled = config.phaseLoggingEnabled
            )
            transactionScopeContext.state = TransactionScopeState.COMPLETED

            logger.info("Transaction succeeded for workflow: {}", txId)
            result
        } catch (e: Exception) {
            // Avoid duplicate ERROR logs. Final severity is logged by retry/finalization flow.
            logger.debug(
                "Transaction attempt failed for workflow: {} - {}: {}",
                txId,
                e::class.simpleName,
                e.message,
                e
            )
            transactionScopeContext.state = TransactionScopeState.ROLLED_BACK

            // Phase 5: ON_ROLLBACK adapters
            if (config.phaseLoggingEnabled) {
                logger.debug("Executing ON_ROLLBACK adapters")
            }
            try {
                withContext(transactionEventContext) {
                    adapterRegistry.executeAdapters(
                        TransactionPhase.ON_ROLLBACK,
                        transactionEventContext,
                        phaseLoggingEnabled = config.phaseLoggingEnabled
                    )
                }
            } catch (adapterError: Exception) {
                logger.warn("Error in ON_ROLLBACK adapter, continuing rollback", adapterError)
            }

            // Phase 6: AFTER_ROLLBACK adapters
            if (config.phaseLoggingEnabled) {
                logger.debug("Executing AFTER_ROLLBACK adapters")
            }
            try {
                adapterRegistry.executeAdapters(
                    TransactionPhase.AFTER_ROLLBACK,
                    transactionEventContext,
                    phaseLoggingEnabled = config.phaseLoggingEnabled
                )
            } catch (adapterError: Exception) {
                logger.warn("Error in AFTER_ROLLBACK adapter, continuing", adapterError)
            }

            throw e
        } finally {
            transactionScopeContext.state = TransactionScopeState.CLOSED
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
        val isRetryable = shouldRetryException(
            exception = exception,
            retryPolicy = config.retryPolicy,
            attempt = attempt,
            maxAttempts = maxAttempts
        )
        if (!isRetryable && attempt >= maxAttempts - 1) {
            logger.debug("Maximum retry attempts (${maxAttempts}) reached")
        }
        if (!isRetryable && exception.matchesAny(config.retryPolicy.nonRetryableExceptions)) {
            logger.debug("Exception ${exception::class.simpleName} is explicitly non-retryable")
        }
        if (isRetryable && exception.matchesAny(config.retryPolicy.retryableExceptions)) {
            logger.debug("Exception ${exception::class.simpleName} is explicitly retryable")
        }
        val isTransient = isRetryable && (
            exception.isTransient() ||
                exception.causeChain().filterIsInstance<Exception>().any { it.isTransient() } ||
                isDeadlockException(exception) ||
                exception.isTransientSqlConnectionIssue()
            )
        if (isTransient) {
            logger.debug("Exception ${exception::class.simpleName} detected as transient error, retrying")
        }
        return isRetryable
    }

    private fun logNonRetryableException(
        transactionId: String,
        exception: Exception,
        config: TransactionConfig
    ) {
        when (config.exceptionSeverityClassifier.classify(exception, config)) {
            TransactionExceptionSeverity.INFO -> {
                logger.info(
                    "Non-retryable exception in transaction {} - {}: {}",
                    transactionId,
                    exception::class.simpleName,
                    exception.message
                )
            }

            TransactionExceptionSeverity.WARN -> {
                logger.warn(
                    "Non-retryable exception in transaction {} - {}: {}",
                    transactionId,
                    exception::class.simpleName,
                    exception.message
                )
            }

            TransactionExceptionSeverity.ERROR -> {
                logger.error(
                    "Non-retryable exception in transaction {} - {}: {}",
                    transactionId,
                    exception::class.simpleName,
                    exception.message,
                    exception
                )
            }
        }
    }

    /**
     * Determines if an exception is a database deadlock.
     */
    private fun isDeadlockException(exception: Exception): Boolean {
        return exception.causeChain().any { cause ->
            when (cause) {
                is DeadlockException -> true
                is SQLException -> {
                    val sqlState = cause.sqlState
                    val errorCode = cause.errorCode
                    // MySQL deadlock: 1213, lock timeout: 1205
                    // PostgreSQL deadlock: 40P01, serialization: 40001
                    errorCode in setOf(1213, 1205) ||
                        sqlState in setOf("40P01", "40001")
                }
                else -> false
            }
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

internal fun shouldRetryException(
    exception: Exception,
    retryPolicy: RetryPolicy,
    attempt: Int,
    maxAttempts: Int
): Boolean {
    if (attempt >= maxAttempts - 1) return false

    // Non-retryable explicit rules must always win.
    if (exception.matchesAny(retryPolicy.nonRetryableExceptions)) return false

    if (exception.matchesAny(retryPolicy.retryableExceptions)) return true

    return exception.isTransient() ||
        exception.causeChain().filterIsInstance<Exception>().any { it.isTransient() } ||
        exception.isTransientSqlConnectionIssue()
}

internal fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

internal fun Throwable.matchesAny(types: Set<KClass<out Exception>>): Boolean =
    causeChain().any { cause -> cause is Exception && types.any { it.isInstance(cause) } }

internal fun Throwable.isTransientSqlConnectionIssue(): Boolean =
    causeChain().filterIsInstance<SQLException>().any { sqlException ->
        val state = sqlException.sqlState ?: return@any false
        state == "08003" || state.startsWith("08") || state.startsWith("40")
    }
