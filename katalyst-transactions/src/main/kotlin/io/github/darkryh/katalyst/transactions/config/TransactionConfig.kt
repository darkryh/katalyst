package io.github.darkryh.katalyst.transactions.config

import io.github.darkryh.katalyst.transactions.exception.isTransient
import java.sql.SQLException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Strategy for calculating backoff delay between retries.
 *
 * Different backoff strategies handle transient failures differently:
 * - EXPONENTIAL: Good for distributed systems, prevents thundering herd
 * - LINEAR: Simpler, predictable delays
 * - IMMEDIATE: No delay, for retries that should happen immediately
 */
enum class BackoffStrategy {
    /**
     * Exponential backoff: 1s, 2s, 4s, 8s, 16s, ...
     *
     * Good for:
     * - Database deadlock recovery
     * - Network timeouts
     * - High contention scenarios
     */
    EXPONENTIAL,

    /**
     * Linear backoff: 1s, 2s, 3s, 4s, 5s, ...
     *
     * Good for:
     * - Predictable scenarios
     * - Testing
     * - Simple transient errors
     */
    LINEAR,

    /**
     * Immediate retry with no delay.
     *
     * Good for:
     * - Lock acquisition
     * - Quick circuit breaker recovery
     * - Optimistic retries
     */
    IMMEDIATE
}

/**
 * Retry policy for transactions.
 *
 * Controls how failed transactions are retried with backoff strategies
 * and exception filtering.
 *
 * **Usage:**
 * ```kotlin
 * val policy = RetryPolicy(
 *     maxRetries = 3,
 *     backoffStrategy = BackoffStrategy.EXPONENTIAL,
 *     initialDelayMs = 1000,
 *     maxDelayMs = 30000
 * )
 *
 * transactionManager.transaction(
 *     config = TransactionConfig(retryPolicy = policy)
 * ) {
 *     // Will retry up to 3 times on transient errors
 * }
 * ```
 */
data class RetryPolicy(
    /**
     * Maximum number of retries after initial attempt.
     *
     * Total attempts = maxRetries + 1 (initial attempt)
     *
     * Default: 3 (so 4 total attempts)
     */
    val maxRetries: Int = 3,

    /**
     * Strategy for calculating backoff delay between retries.
     *
     * Default: EXPONENTIAL (prevents thundering herd)
     */
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,

    /**
     * Initial delay for first retry in milliseconds.
     *
     * Default: 1000ms (1 second)
     */
    val initialDelayMs: Long = 1000,

    /**
     * Maximum delay between retries in milliseconds.
     *
     * Prevents delays from growing unbounded with exponential backoff.
     *
     * Default: 30000ms (30 seconds)
     */
    val maxDelayMs: Long = 30000,

    /**
     * Add randomness to backoff delay to prevent thundering herd.
     *
     * Applied as a percentage: 0.1 = ±10% variance
     *
     * Default: 0.1 (10% jitter)
     */
    val jitterFactor: Double = 0.1,

    /**
     * Exceptions that should trigger automatic retry.
     *
     * Typically transient errors that might succeed on retry:
     * - Database deadlocks
     * - Connection timeouts
     * - Temporary network errors
     *
     * Default: SQL, Timeout, IOException, Deadlock
     */
    val retryableExceptions: Set<KClass<out Exception>> = setOf(
        java.sql.SQLException::class,
        java.util.concurrent.TimeoutException::class,
        java.io.IOException::class
    ),

    /**
     * Exceptions that should NOT be retried.
     *
     * Permanent errors that won't succeed on retry:
     * - Validation errors
     * - Authentication failures
     * - Data integrity violations
     *
     * Default: Validation, Authentication, DataIntegrityViolation
     */
    val nonRetryableExceptions: Set<KClass<out Exception>> = setOf(
        java.lang.IllegalArgumentException::class,
        java.lang.SecurityException::class
    )
)

/**
 * Severity used for transaction exception logging.
 */
enum class TransactionExceptionSeverity {
    INFO,
    WARN,
    ERROR
}

/**
 * Classifies non-retryable transaction exceptions into log severity levels.
 *
 * The classifier is used only when a transaction attempt is not retried.
 */
fun interface TransactionExceptionSeverityClassifier {
    /**
     * @param exception The failure thrown by the transaction.
     * @param config Active transaction config.
     */
    fun classify(
        exception: Exception,
        config: TransactionConfig
    ): TransactionExceptionSeverity
}

/**
 * Default severity classifier for transaction failures.
 *
 * Classification policy:
 * - Retryable/transient/infrastructure failures -> ERROR
 * - Explicit non-retryable policy exceptions -> WARN
 * - Built-in known Katalyst expected/business exceptions -> WARN
 * - Conventional application business exceptions -> WARN
 * - Configured expected business exception types -> WARN
 * - Unknown exceptions -> ERROR
 */
object DefaultTransactionExceptionSeverityClassifier : TransactionExceptionSeverityClassifier {
    /**
     * Explicit built-in allowlist for Katalyst exceptions considered expected/business failures.
     *
     * Kept as FQCN strings to avoid cross-module compile/runtime coupling from transactions module.
     */
    private val knownKatalystExpectedExceptionNames = setOf(
        "io.github.darkryh.katalyst.events.bus.validation.EventValidationException",
        "io.github.darkryh.katalyst.events.exception.EventValidationException",
        "io.github.darkryh.katalyst.scheduler.exception.SchedulerValidationException"
    )

    private val expectedBusinessNameSuffixes = listOf(
        "BadRequestException",
        "BusinessException",
        "ConflictException",
        "DomainException",
        "ForbiddenException",
        "NotFoundException",
        "UnauthorizedException",
        "ValidationException"
    )

    override fun classify(
        exception: Exception,
        config: TransactionConfig
    ): TransactionExceptionSeverity {
        val retryPolicy = config.retryPolicy

        if (matchesAny(exception, retryPolicy.retryableExceptions) ||
            exception.isTransient() ||
            hasTransientSqlState(exception)
        ) {
            return TransactionExceptionSeverity.ERROR
        }

        if (matchesAny(exception, retryPolicy.nonRetryableExceptions)) {
            return TransactionExceptionSeverity.WARN
        }

        if (isKnownKatalystExpectedException(exception) ||
            hasExpectedBusinessExceptionName(exception) ||
            matchesAny(exception, config.expectedBusinessExceptions)
        ) {
            return TransactionExceptionSeverity.WARN
        }

        return TransactionExceptionSeverity.ERROR
    }

    private fun isKnownKatalystExpectedException(exception: Exception): Boolean =
        exception.causeChain().any { throwable ->
            throwable.classHierarchyNames().any { it in knownKatalystExpectedExceptionNames }
        }

    private fun hasExpectedBusinessExceptionName(exception: Exception): Boolean =
        exception.causeChain().any { throwable ->
            throwable.classHierarchyNames().any { className ->
                expectedBusinessNameSuffixes.any(className::endsWith)
            }
        }

    private fun hasTransientSqlState(exception: Exception): Boolean =
        exception.causeChain().filterIsInstance<SQLException>().any { sqlException ->
            val state = sqlException.sqlState ?: return@any false
            state == "08003" || state.startsWith("08") || state.startsWith("40")
        }

    private fun matchesAny(
        throwable: Throwable,
        types: Set<KClass<out Exception>>
    ): Boolean =
        throwable.causeChain().any { cause -> cause is Exception && types.any { it.isInstance(cause) } }

    private fun Throwable.causeChain(): Sequence<Throwable> =
        generateSequence(this) { it.cause }

    private fun Throwable.classHierarchyNames(): Sequence<String> = sequence {
        var cursor: Class<*>? = this@classHierarchyNames::class.java
        while (cursor != null) {
            yield(cursor.name)
            cursor.interfaces.forEach { yield(it.name) }
            cursor = cursor.superclass
        }
    }
}

/**
 * Isolation level for transaction execution.
 *
 * Determines how concurrent transactions interact:
 * - READ_UNCOMMITTED: Lowest isolation, highest performance
 * - READ_COMMITTED: Prevents dirty reads (most common)
 * - REPEATABLE_READ: Prevents dirty and non-repeatable reads
 * - SERIALIZABLE: Highest isolation, lowest performance
 */
enum class TransactionIsolationLevel {
    /**
     * READ_UNCOMMITTED:
     * - Transaction can read uncommitted changes from other transactions
     * - Allows dirty reads
     * - Highest performance, lowest isolation
     */
    READ_UNCOMMITTED,

    /**
     * READ_COMMITTED (DEFAULT):
     * - Transaction can only read committed changes
     * - Prevents dirty reads
     * - Good balance of performance and isolation
     */
    READ_COMMITTED,

    /**
     * REPEATABLE_READ:
     * - Prevents dirty reads and non-repeatable reads
     * - Snapshots data for duration of transaction
     * - Better isolation, slightly lower performance
     */
    REPEATABLE_READ,

    /**
     * SERIALIZABLE:
     * - Highest isolation level
     * - Transactions execute as if they were serial
     * - Prevents all anomalies
     * - Lowest performance, highest isolation
     */
    SERIALIZABLE
}

/**
 * Configuration for transaction execution.
 *
 * Controls timeout, retry behavior, and isolation level for transactions.
 *
 * **Usage:**
 * ```kotlin
 * val config = TransactionConfig(
 *     timeout = 30.seconds,
 *     retryPolicy = RetryPolicy(maxRetries = 3),
 *     isolationLevel = TransactionIsolationLevel.READ_COMMITTED
 * )
 *
 * transactionManager.transaction(config = config) {
 *     // Transaction with custom timeout, retry, and isolation
 * }
 * ```
 */
data class TransactionConfig(
    /**
     * Maximum duration for transaction execution.
     *
     * If transaction exceeds this duration, it's cancelled with TimeoutException.
     *
     * Default: 30 seconds
     */
    val timeout: Duration = 30.toDuration(DurationUnit.SECONDS),

    /**
     * Retry policy for failed transactions.
     *
     * Defines how many times to retry and with what backoff strategy.
     *
     * Default: Exponential backoff up to 3 retries
     */
    val retryPolicy: RetryPolicy = RetryPolicy(),

    /**
     * Isolation level for the transaction.
     *
     * Determines how this transaction interacts with concurrent transactions.
     *
     * Default: READ_COMMITTED
     */
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.READ_COMMITTED,

    /**
     * Exception types that represent expected business failures for this application.
     *
     * This is used only for log severity classification and does not alter retry behavior.
     */
    val expectedBusinessExceptions: Set<KClass<out Exception>> = emptySet(),

    /**
     * Enables verbose transaction phase logging (phase transitions, per-adapter execution details).
     *
     * This flag only affects transaction internals and does not change global application logging.
     * Disable this in high-throughput environments to reduce log overhead.
     *
     * Default: true
     */
    val phaseLoggingEnabled: Boolean = true,

    /**
     * Classifier that determines log severity for non-retryable transaction failures.
     *
     * Default behavior:
     * - expected business failures -> WARN
     * - infrastructure/unexpected failures -> ERROR
     */
    val exceptionSeverityClassifier: TransactionExceptionSeverityClassifier =
        DefaultTransactionExceptionSeverityClassifier
)
