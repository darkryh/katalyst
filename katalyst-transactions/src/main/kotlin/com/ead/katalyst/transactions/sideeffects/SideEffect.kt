package com.ead.katalyst.transactions.sideeffects

import java.time.Instant

/**
 * Generic transactional side-effect.
 *
 * A side-effect is any operation that should execute as part of a transaction
 * and either commit with the transaction or rollback completely.
 *
 * Examples:
 * - Publishing domain events
 * - Invalidating cache entries
 * - Updating search indices
 * - Publishing messages
 * - Writing audit logs
 * - Executing callbacks
 *
 * **Execution Modes:**
 * - SYNC_BEFORE_COMMIT: Execute before transaction commits (failures rollback)
 * - ASYNC_AFTER_COMMIT: Execute after transaction commits (failures isolated)
 *
 * **Type Parameter T:**
 * - Represents the context type for side-effect execution
 * - Examples: DomainEvent, CacheKey, Document, Message, etc.
 *
 * @param T The type of side-effect context
 *
 * Example:
 * ```kotlin
 * data class EventSideEffect(val event: DomainEvent) : TransactionalSideEffect<DomainEvent> {
 *     override val sideEffectId = event.eventId
 *     override val handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT
 *
 *     override suspend fun execute(context: DomainEvent): SideEffectResult {
 *         eventBus.publish(event)
 *         return SideEffectResult.Success()
 *     }
 *
 *     override suspend fun compensate(result: SideEffectResult) {
 *         // No compensation needed for events
 *     }
 * }
 * ```
 */
interface TransactionalSideEffect<T> {
    /**
     * Unique identifier for this side-effect.
     *
     * Used for:
     * - Logging and debugging
     * - Deduplication
     * - Error reporting
     * - Tracking
     */
    val sideEffectId: String

    /**
     * When this side-effect executes relative to transaction commit.
     */
    val handlingMode: SideEffectHandlingMode

    /**
     * Execute the side-effect.
     *
     * This is the main business logic for the side-effect.
     *
     * For SYNC_BEFORE_COMMIT:
     * - Runs within transaction context
     * - Exceptions cause transaction rollback
     * - Timing-critical operations (cache invalidation, search indexing)
     *
     * For ASYNC_AFTER_COMMIT:
     * - Runs after transaction commits
     * - Exceptions are logged but don't rollback
     * - Fire-and-forget operations (notifications, logging)
     *
     * @param context The side-effect context (event, cache key, document, etc.)
     * @return Result of execution for tracking and compensation
     * @throws Exception If side-effect fails (SYNC mode will rollback)
     */
    suspend fun execute(context: T): SideEffectResult

    /**
     * Compensate (undo) the side-effect if transaction rolls back or on explicit request.
     *
     * Called when:
     * - Transaction fails and rolls back
     * - Compensation is explicitly requested
     * - Later side-effects fail (in SYNC mode)
     *
     * **Important:**
     * - Must be idempotent (safe to call multiple times)
     * - Should not throw exceptions (log errors instead)
     * - Will be called in reverse order of side-effect execution
     *
     * @param result The result from execute() that needs compensation
     */
    suspend fun compensate(result: SideEffectResult) {
        // Default: no compensation
    }
}

/**
 * Controls when side-effects are executed.
 *
 * - SYNC_BEFORE_COMMIT: Execute before transaction commits
 *   - Failures cause transaction rollback
 *   - Strong consistency (all-or-nothing)
 *   - Suitable for critical operations
 *
 * - ASYNC_AFTER_COMMIT: Execute after transaction commits
 *   - Failures don't rollback transaction
 *   - Eventual consistency
 *   - Suitable for non-critical operations
 */
enum class SideEffectHandlingMode {
    /**
     * Synchronous mode: Side-effects execute BEFORE transaction commits.
     *
     * **Guarantees:**
     * - If side-effect fails, entire transaction is rolled back
     * - Strong consistency (all-or-nothing)
     * - Developer doesn't need compensation logic
     * - Simple API: side-effects just throw on error
     *
     * **Trade-offs:**
     * - Slower (side-effects block commit)
     * - Long-running operations slow down transaction
     *
     * **Use Cases:**
     * - Cache invalidation (must be consistent)
     * - Search index updates (must be consistent)
     * - Critical validations (must not commit if fail)
     */
    SYNC_BEFORE_COMMIT,

    /**
     * Asynchronous mode: Side-effects execute AFTER transaction commits.
     *
     * **Guarantees:**
     * - Fast commit (side-effects don't block)
     * - Eventual consistency
     * - Decoupled systems
     *
     * **Trade-offs:**
     * - If side-effect fails, can't rollback transaction
     * - May have partial state in database
     * - Requires idempotent side-effects
     *
     * **Use Cases:**
     * - Notifications (email, SMS, webhooks)
     * - Audit logging (informational)
     * - Analytics events (non-critical)
     * - Message publishing (async processing)
     */
    ASYNC_AFTER_COMMIT
}

/**
 * Result of a side-effect execution.
 *
 * Stores execution result and metadata for tracking and compensation.
 */
sealed class SideEffectResult {
    /**
     * Side-effect executed successfully.
     */
    data class Success(
        val executedAt: Instant = Instant.now(),
        val metadata: Map<String, Any> = emptyMap()
    ) : SideEffectResult()

    /**
     * Side-effect failed but was handled/retried.
     */
    data class Failed(
        val error: Exception,
        val executedAt: Instant = Instant.now(),
        val retryCount: Int = 0
    ) : SideEffectResult()

    /**
     * Side-effect skipped (deduplication, conditions not met, etc.).
     */
    data class Skipped(
        val reason: String,
        val executedAt: Instant = Instant.now()
    ) : SideEffectResult()
}

/**
 * Classifies exceptions as transient (retryable) or permanent (fail fast).
 *
 * Used by retry logic to determine if a side-effect failure should be retried
 * or failed immediately.
 *
 * **Transient Errors (should retry):**
 * - Timeout exceptions
 * - Connection exceptions
 * - Temporary failures
 *
 * **Permanent Errors (fail fast):**
 * - Null pointer exceptions
 * - Illegal state exceptions
 * - Handler/business logic errors
 *
 * Example:
 * ```kotlin
 * val classifier = DefaultTransientErrorClassifier(
 *     transientPatterns = listOf("timeout", "connection", "temporarily unavailable")
 * )
 *
 * val isTransient = classifier.isTransient(TimeoutException("Request timeout"))
 * // Result: true - will be retried
 *
 * val isPermanent = classifier.isTransient(NullPointerException())
 * // Result: false - will fail fast
 * ```
 */
interface TransientErrorClassifier {
    /**
     * Check if an exception is transient (retryable).
     *
     * @param exception The exception to classify
     * @return true if transient (should retry), false if permanent (fail fast)
     */
    fun isTransient(exception: Exception): Boolean
}

/**
 * Default implementation of TransientErrorClassifier.
 *
 * Classifies errors based on:
 * - Exception type
 * - Exception message patterns
 * - Exception cause chain
 */
class DefaultTransientErrorClassifier(
    private val transientPatterns: List<String> = DEFAULT_TRANSIENT_PATTERNS
) : TransientErrorClassifier {
    companion object {
        private val DEFAULT_TRANSIENT_PATTERNS = listOf(
            "timeout",
            "timed out",
            "connection",
            "connect",
            "unavailable",
            "temporarily unavailable",
            "io exception",
            "ioexception",
            "socket",
            "broken pipe",
            "reset by peer",
            "connection reset",
            "econnreset",
            "network unreachable",
            "host unreachable",
            "no route to host",
            "resource temporarily unavailable"
        )

        // Exceptions that are always transient
        private val TRANSIENT_EXCEPTION_TYPES = setOf(
            "java.net.ConnectException",
            "java.net.SocketTimeoutException",
            "java.io.InterruptedIOException",
            "java.nio.channels.InterruptedByTimeoutException",
            "java.util.concurrent.TimeoutException",
            "java.lang.InterruptedException"
        )

        // Exceptions that are always permanent
        private val PERMANENT_EXCEPTION_TYPES = setOf(
            "java.lang.NullPointerException",
            "java.lang.IllegalStateException",
            "java.lang.IllegalArgumentException",
            "java.lang.ClassCastException",
            "java.lang.IndexOutOfBoundsException"
        )
    }

    override fun isTransient(exception: Exception): Boolean {
        // Check exception type first
        val exceptionClassName = exception::class.qualifiedName ?: exception::class.simpleName ?: ""

        // Check if this exception type is always permanent
        if (PERMANENT_EXCEPTION_TYPES.contains(exceptionClassName)) {
            return false
        }

        // Check if this exception type is always transient
        if (TRANSIENT_EXCEPTION_TYPES.contains(exceptionClassName)) {
            return true
        }

        // Check exception message against patterns
        val message = exception.message?.lowercase() ?: ""
        for (pattern in transientPatterns) {
            if (message.contains(pattern.lowercase())) {
                return true
            }
        }

        // Check cause chain
        var cause = exception.cause
        while (cause != null) {
            if (isTransient(cause as Exception)) {
                return true
            }
            cause = cause.cause
        }

        // Default: assume permanent (fail fast)
        return false
    }
}

/**
 * Configuration for a specific side-effect type.
 *
 * Allows per-side-effect-type configuration of:
 * - Handling mode (SYNC vs ASYNC)
 * - Timeout
 * - Failure behavior
 * - Retry strategy
 *
 * Example:
 * ```kotlin
 * val eventConfig = SideEffectConfig(
 *     sideEffectId = "UserCreatedEvent",
 *     handlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,
 *     timeoutMs = 5000,
 *     failOnHandlerError = true,
 *     maxRetries = 3,
 *     retryDelayMs = 100,
 *     backoffMultiplier = 2.0
 * )
 *
 * val notificationConfig = SideEffectConfig(
 *     sideEffectId = "SendEmailNotification",
 *     handlingMode = SideEffectHandlingMode.ASYNC_AFTER_COMMIT,
 *     timeoutMs = 30000,
 *     failOnHandlerError = false,
 *     maxRetries = 1  // No retries for async
 * )
 * ```
 */
data class SideEffectConfig(
    /**
     * Unique identifier for this side-effect type.
     */
    val sideEffectId: String,

    /**
     * When to execute side-effects of this type.
     *
     * Default: SYNC_BEFORE_COMMIT for strong consistency
     */
    val handlingMode: SideEffectHandlingMode = SideEffectHandlingMode.SYNC_BEFORE_COMMIT,

    /**
     * Maximum timeout for side-effect execution (ms).
     *
     * Only applies to SYNC_BEFORE_COMMIT mode.
     * If side-effect takes longer, throws TimeoutException.
     *
     * Default: 5000ms
     */
    val timeoutMs: Long = 5000,

    /**
     * Whether to fail transaction if side-effect fails.
     *
     * - true: Side-effect failure = transaction rollback (SYNC_BEFORE_COMMIT only)
     * - false: Side-effect failure = logged but transaction continues
     *
     * Default: true (fail fast)
     */
    val failOnHandlerError: Boolean = true,

    /**
     * Custom metadata for this side-effect type.
     */
    val metadata: Map<String, Any> = emptyMap(),

    /**
     * Maximum number of retry attempts for transient failures.
     *
     * Only applies to SYNC_BEFORE_COMMIT mode.
     * ASYNC_AFTER_COMMIT side-effects do not retry (fire-and-forget).
     *
     * - 1: No retries (fail on first error)
     * - 3: Retry twice after initial failure (default)
     * - 5+: More aggressive retry for critical operations
     *
     * Default: 3
     */
    val maxRetries: Int = 3,

    /**
     * Initial delay between retry attempts (ms).
     *
     * Actual delay = retryDelayMs * (backoffMultiplier ^ (attempt - 1)) + jitter
     *
     * Default: 100ms
     */
    val retryDelayMs: Long = 100,

    /**
     * Exponential backoff multiplier for retry delays.
     *
     * Each retry waits: previousDelay * backoffMultiplier
     *
     * Examples:
     * - 2.0: 100ms, 200ms, 400ms (exponential)
     * - 1.5: 100ms, 150ms, 225ms (moderate backoff)
     * - 1.0: 100ms, 100ms, 100ms (no backoff, constant delay)
     *
     * Default: 2.0 (exponential)
     */
    val backoffMultiplier: Double = 2.0,

    /**
     * Classifier for determining if an exception is transient (retryable).
     *
     * - Transient errors: Will retry up to maxRetries times
     * - Permanent errors: Will fail immediately (no retry)
     *
     * Default: DefaultTransientErrorClassifier (classifies by exception type and message)
     */
    val isTransientError: (Exception) -> Boolean = { e ->
        DefaultTransientErrorClassifier().isTransient(e)
    }
)

/**
 * Tracks retry context for a side-effect.
 *
 * Records:
 * - Number of attempts (including retries)
 * - Last error encountered
 * - When retry is scheduled
 */
data class SideEffectRetryContext(
    val sideEffectId: String,
    val attemptCount: Int = 0,
    val lastError: Exception? = null,
    val retryScheduledAt: Instant? = null
)

/**
 * Context for side-effect execution.
 *
 * Tracks queued side-effects, executed side-effects, and retry attempts
 * during transaction lifecycle.
 */
data class SideEffectContext(
    /**
     * Side-effects queued but not yet executed.
     */
    val pendingSideEffects: MutableList<TransactionalSideEffect<*>> = mutableListOf(),

    /**
     * Side-effects executed and their results.
     */
    val executedSideEffects: MutableList<Pair<TransactionalSideEffect<*>, SideEffectResult>> = mutableListOf(),

    /**
     * Retry contexts per side-effect ID.
     *
     * Tracks retry attempts, last error, and retry scheduling.
     */
    val retryContexts: MutableMap<String, SideEffectRetryContext> = mutableMapOf()
) {
    /**
     * Queue a side-effect for execution.
     */
    fun queue(sideEffect: TransactionalSideEffect<*>) {
        pendingSideEffects.add(sideEffect)
    }

    /**
     * Get pending side-effects.
     */
    fun getPending(): List<TransactionalSideEffect<*>> = pendingSideEffects.toList()

    /**
     * Clear pending side-effects.
     */
    fun clearPending() {
        pendingSideEffects.clear()
    }

    /**
     * Get count of pending side-effects.
     */
    fun getPendingCount(): Int = pendingSideEffects.size

    /**
     * Record executed side-effect.
     */
    fun recordExecution(sideEffect: TransactionalSideEffect<*>, result: SideEffectResult) {
        executedSideEffects.add(sideEffect to result)
    }

    /**
     * Get all executed side-effects.
     */
    fun getExecuted(): List<Pair<TransactionalSideEffect<*>, SideEffectResult>> = executedSideEffects.toList()

    /**
     * Record retry attempt for a side-effect.
     *
     * @param sideEffectId The side-effect ID
     * @param error The error encountered
     */
    fun recordRetryAttempt(sideEffectId: String, error: Exception) {
        val existing = retryContexts[sideEffectId]
        val newContext = SideEffectRetryContext(
            sideEffectId = sideEffectId,
            attemptCount = (existing?.attemptCount ?: 0) + 1,
            lastError = error,
            retryScheduledAt = Instant.now()
        )
        retryContexts[sideEffectId] = newContext
    }

    /**
     * Get retry context for a side-effect.
     *
     * @param sideEffectId The side-effect ID
     * @return Retry context if found, null otherwise
     */
    fun getRetryContext(sideEffectId: String): SideEffectRetryContext? = retryContexts[sideEffectId]

    /**
     * Get retry attempt count for a side-effect.
     *
     * @param sideEffectId The side-effect ID
     * @return Attempt count (0 if no retries)
     */
    fun getRetryAttemptCount(sideEffectId: String): Int = retryContexts[sideEffectId]?.attemptCount ?: 0
}
