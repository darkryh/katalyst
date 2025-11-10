# Phase 2: Production Readiness Implementation Plan

## Overview

Phase 2 adds three critical production-ready features to the Katalyst transaction system:

1. **Transaction Timeout & Retry** - Prevent hanging, handle transient failures
2. **Transaction Metrics & Observability** - Monitor performance, enable alerting
3. **Distributed Transactions/Saga** - Support multi-step transactions across services

**Timeline**: 3 weeks (12 days)
**Status**: Ready to start

---

## Feature 1: Transaction Timeout & Retry (4 days)

### 1.1 Transaction Configuration

**New File**: `TransactionConfig.kt`

```kotlin
enum class BackoffStrategy {
    EXPONENTIAL,      // 1s, 2s, 4s, 8s...
    LINEAR,           // 1s, 2s, 3s, 4s...
    IMMEDIATE         // No delay between retries
}

data class RetryPolicy(
    val maxRetries: Int = 3,
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val jitterFactor: Double = 0.1,  // Add 10% randomness
    val retryableExceptions: Set<KClass<out Exception>> = setOf(
        SQLException::class,
        TimeoutException::class,
        DeadlockException::class,
        IOException::class
    ),
    val nonRetryableExceptions: Set<KClass<out Exception>> = setOf(
        ValidationException::class,
        AuthenticationException::class,
        DataIntegrityViolationException::class
    )
)

data class TransactionConfig(
    val timeout: Duration = 30.seconds,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.READ_COMMITTED
)

enum class TransactionIsolationLevel {
    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}
```

### 1.2 Timeout Implementation

**Modify**: `DatabaseTransactionManager.kt`

```kotlin
override suspend fun <T> transaction(
    workflowId: String?,
    config: TransactionConfig = TransactionConfig(),
    block: suspend Transaction.() -> T
): T {
    val txId = workflowId ?: UUID.randomUUID().toString()
    var lastException: Exception? = null

    repeat(config.retryPolicy.maxRetries + 1) { attempt ->
        try {
            return withTimeoutOrNull(config.timeout) {
                newSuspendedTransaction(null, database) {
                    block()
                }
            } ?: throw TransactionTimeoutException(
                "Transaction timeout after ${config.timeout}",
                txId,
                attempt
            )
        } catch (e: Exception) {
            lastException = e

            if (!shouldRetry(e, config.retryPolicy, attempt)) {
                throw e
            }

            if (attempt < config.retryPolicy.maxRetries) {
                val delayMs = calculateBackoffDelay(
                    attempt,
                    config.retryPolicy
                )
                delay(delayMs)
            }
        }
    }

    throw lastException ?: TransactionFailedException(txId)
}

private fun shouldRetry(
    exception: Exception,
    policy: RetryPolicy,
    attempt: Int
): Boolean {
    // Don't retry if max attempts reached
    if (attempt >= policy.maxRetries) return false

    // Check if exception is retryable
    val exceptionClass = exception::class

    // Explicitly non-retryable
    if (policy.nonRetryableExceptions.any { exceptionClass == it }) {
        return false
    }

    // Explicitly retryable
    if (policy.retryableExceptions.any { exceptionClass == it }) {
        return true
    }

    // Default: retry common transient errors
    return when (exception) {
        is SQLException -> isDatabaseTransientError(exception)
        is IOException -> true
        is TimeoutException -> true
        else -> false
    }
}

private fun calculateBackoffDelay(attempt: Int, policy: RetryPolicy): Long {
    val baseDelay = when (policy.backoffStrategy) {
        BackoffStrategy.EXPONENTIAL -> {
            policy.initialDelayMs * Math.pow(2.0, attempt.toDouble()).toLong()
        }
        BackoffStrategy.LINEAR -> {
            policy.initialDelayMs * (attempt + 1)
        }
        BackoffStrategy.IMMEDIATE -> {
            0L
        }
    }

    val cappedDelay = minOf(baseDelay, policy.maxDelayMs)

    // Add jitter
    val jitter = (cappedDelay * policy.jitterFactor * Math.random()).toLong()
    return cappedDelay + jitter
}
```

### 1.3 Custom Exceptions

**New File**: `TransactionExceptions.kt`

```kotlin
class TransactionTimeoutException(
    message: String,
    val transactionId: String,
    val attemptNumber: Int
) : Exception(message)

class TransactionFailedException(
    val transactionId: String,
    cause: Throwable? = null
) : Exception("Transaction failed: $transactionId", cause)

class DeadlockException(message: String = "Database deadlock detected") : SQLException(message)
```

---

## Feature 2: Transaction Metrics & Observability (5 days)

### 2.1 Metrics Data Model

**New File**: `TransactionMetrics.kt`

```kotlin
data class TransactionMetrics(
    val transactionId: String,
    val workflowId: String? = null,
    val startTime: Instant = Instant.now(),
    var endTime: Instant? = null,
    var status: TransactionStatus = TransactionStatus.RUNNING,
    var operationCount: Int = 0,
    var eventCount: Int = 0,
    var duration: Duration? = null,
    val adapterExecutions: MutableList<AdapterMetrics> = mutableListOf(),
    val errors: MutableList<TransactionError> = mutableListOf(),
    val retryCount: Int = 0,
    val config: TransactionConfig? = null
)

data class AdapterMetrics(
    val adapterName: String,
    val phase: TransactionPhase,
    val startTime: Instant,
    var endTime: Instant? = null,
    var duration: Duration? = null,
    var success: Boolean = false,
    var error: Exception? = null
)

data class TransactionError(
    val timestamp: Instant,
    val phase: TransactionPhase,
    val message: String,
    val stackTrace: String,
    val isRetryable: Boolean
)

enum class TransactionStatus {
    RUNNING,
    COMMITTED,
    ROLLED_BACK,
    TIMEOUT,
    FAILED
}
```

### 2.2 Metrics Collector

**New File**: `TransactionMetricsCollector.kt`

```kotlin
interface MetricsCollector {
    fun startTransaction(transactionId: String, workflowId: String? = null): TransactionMetrics
    fun recordAdapterExecution(transactionId: String, result: AdapterExecutionResult)
    fun recordEventPublished(transactionId: String)
    fun recordOperationExecuted(transactionId: String)
    fun recordError(transactionId: String, phase: TransactionPhase, error: Exception, isRetryable: Boolean)
    fun completeTransaction(transactionId: String, status: TransactionStatus)
    fun getMetrics(transactionId: String): TransactionMetrics?
}

class DefaultMetricsCollector : MetricsCollector {
    private val metricsMap = ConcurrentHashMap<String, TransactionMetrics>()

    override fun startTransaction(transactionId: String, workflowId: String?): TransactionMetrics {
        val metrics = TransactionMetrics(transactionId, workflowId)
        metricsMap[transactionId] = metrics
        return metrics
    }

    override fun recordAdapterExecution(transactionId: String, result: AdapterExecutionResult) {
        metricsMap[transactionId]?.let { metrics ->
            metrics.adapterExecutions.add(
                AdapterMetrics(
                    adapterName = result.adapter.name(),
                    phase = result.phase,
                    startTime = Instant.now(),
                    duration = Duration.ofMillis(result.duration),
                    success = result.success,
                    error = result.error
                )
            )
        }
    }

    override fun recordEventPublished(transactionId: String) {
        metricsMap[transactionId]?.eventCount?.increment()
    }

    override fun recordOperationExecuted(transactionId: String) {
        metricsMap[transactionId]?.operationCount?.increment()
    }

    override fun recordError(
        transactionId: String,
        phase: TransactionPhase,
        error: Exception,
        isRetryable: Boolean
    ) {
        metricsMap[transactionId]?.let { metrics ->
            metrics.errors.add(
                TransactionError(
                    timestamp = Instant.now(),
                    phase = phase,
                    message = error.message ?: "Unknown error",
                    stackTrace = error.stackTraceToString(),
                    isRetryable = isRetryable
                )
            )
        }
    }

    override fun completeTransaction(transactionId: String, status: TransactionStatus) {
        metricsMap[transactionId]?.let { metrics ->
            metrics.endTime = Instant.now()
            metrics.status = status
            metrics.duration = Duration.between(metrics.startTime, metrics.endTime)
        }
    }

    override fun getMetrics(transactionId: String): TransactionMetrics? = metricsMap[transactionId]
}
```

### 2.3 Metrics Exporter

**New File**: `MetricsExporter.kt`

```kotlin
interface MetricsExporter {
    suspend fun export(metrics: TransactionMetrics)
}

class LoggingMetricsExporter : MetricsExporter {
    private val logger = LoggerFactory.getLogger(LoggingMetricsExporter::class.java)

    override suspend fun export(metrics: TransactionMetrics) {
        logger.info(
            "Transaction: {} Status: {} Duration: {}ms Operations: {} Events: {} Adapters: {}",
            metrics.transactionId,
            metrics.status,
            metrics.duration?.toMillis(),
            metrics.operationCount,
            metrics.eventCount,
            metrics.adapterExecutions.size
        )

        metrics.adapterExecutions.forEach { adapter ->
            logger.debug(
                "  - {}: {} ({}ms)",
                adapter.adapterName,
                if (adapter.success) "SUCCESS" else "FAILED",
                adapter.duration?.toMillis()
            )
        }

        metrics.errors.forEach { error ->
            logger.warn(
                "  - Error in {}: {} (retryable: {})",
                error.phase,
                error.message,
                error.isRetryable
            )
        }
    }
}

class MetricsRegistry {
    private val exporters = mutableListOf<MetricsExporter>()

    fun registerExporter(exporter: MetricsExporter) {
        exporters.add(exporter)
    }

    suspend fun exportMetrics(metrics: TransactionMetrics) {
        exporters.forEach { it.export(metrics) }
    }
}
```

---

## Feature 3: Distributed Transactions/Saga Framework (3 days)

### 3.1 Saga Core Interfaces

**New File**: `SagaFramework.kt`

```kotlin
interface SagaStep<T> {
    val stepName: String
    suspend fun execute(): T
    suspend fun compensate(result: T)
}

data class SagaStepResult<T>(
    val stepName: String,
    val result: T,
    val executedAt: Instant = Instant.now()
)

enum class SagaStatus {
    CREATED,
    RUNNING,
    COMMITTED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}

data class SagaContext(
    val sagaId: String = UUID.randomUUID().toString(),
    val startTime: Instant = Instant.now(),
    var endTime: Instant? = null,
    var status: SagaStatus = SagaStatus.CREATED,
    val steps: MutableList<SagaStepResult<*>> = mutableListOf(),
    val errors: MutableList<Exception> = mutableListOf()
)
```

### 3.2 Saga Orchestrator

**New File**: `SagaOrchestrator.kt`

```kotlin
class SagaOrchestrator(
    val sagaId: String = UUID.randomUUID().toString(),
    private val transactionManager: DatabaseTransactionManager
) {
    private val context = SagaContext(sagaId)
    private val steps = mutableListOf<SagaStep<*>>()
    private val logger = LoggerFactory.getLogger(SagaOrchestrator::class.java)

    suspend fun <T> step(step: SagaStep<T>): T {
        logger.info("Starting saga step: {}", step.stepName)

        return try {
            transactionManager.transaction(sagaId) {
                val result = step.execute()
                context.steps.add(SagaStepResult(step.stepName, result))
                steps.add(step)
                result
            }
        } catch (e: Exception) {
            logger.error("Saga step failed: {}", step.stepName, e)
            context.errors.add(e)
            context.status = SagaStatus.COMPENSATING
            compensateAllSteps()
            throw e
        }
    }

    suspend fun execute(): SagaStatus {
        context.status = SagaStatus.RUNNING
        logger.info("Executing saga: {}", sagaId)

        return try {
            // All steps have been executed via step() method
            context.status = SagaStatus.COMMITTED
            logger.info("Saga committed: {}", sagaId)
            SagaStatus.COMMITTED
        } catch (e: Exception) {
            logger.error("Saga failed: {}", sagaId, e)
            context.status = SagaStatus.FAILED
            SagaStatus.FAILED
        } finally {
            context.endTime = Instant.now()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun compensateAllSteps() {
        logger.info("Compensating {} steps", steps.size)

        // Run compensations in reverse order
        steps.reversed().forEach { step ->
            try {
                val result = context.steps.find { it.stepName == step.stepName }
                if (result != null) {
                    val step = step as SagaStep<Any>
                    step.compensate(result.result)
                    logger.info("Compensated step: {}", step.stepName)
                }
            } catch (e: Exception) {
                logger.error("Compensation failed for step: {}", step.stepName, e)
                context.errors.add(e)
            }
        }

        context.status = SagaStatus.COMPENSATED
    }

    fun getContext(): SagaContext = context
}
```

### 3.3 Example Saga Usage

```kotlin
class UserRegistrationSaga(
    private val userService: UserService,
    private val profileService: ProfileService,
    private val notificationService: NotificationService,
    private val transactionManager: DatabaseTransactionManager
) {
    suspend fun execute(request: RegisterRequest): User {
        val saga = SagaOrchestrator(transactionManager = transactionManager)

        // Step 1: Create user account
        val user = saga.step(object : SagaStep<User> {
            override val stepName = "create-user"

            override suspend fun execute(): User {
                return userService.create(request)
            }

            override suspend fun compensate(result: User) {
                userService.delete(result.id)
            }
        })

        // Step 2: Create profile
        val profile = saga.step(object : SagaStep<Profile> {
            override val stepName = "create-profile"

            override suspend fun execute(): Profile {
                return profileService.create(user.id)
            }

            override suspend fun compensate(result: Profile) {
                profileService.delete(result.id)
            }
        })

        // Step 3: Send welcome email
        saga.step(object : SagaStep<Unit> {
            override val stepName = "send-email"

            override suspend fun execute() {
                notificationService.sendWelcome(user.email)
            }

            override suspend fun compensate(result: Unit) {
                // Optional: no need to unsend email
            }
        })

        saga.execute()
        return user
    }
}
```

---

## Implementation Schedule

### Day 1: Transaction Timeout & Retry
- [ ] Create TransactionConfig.kt with enum and data classes
- [ ] Create TransactionExceptions.kt
- [ ] Modify DatabaseTransactionManager.kt with timeout/retry logic
- [ ] Unit tests for timeout and retry

### Day 2: Metrics Infrastructure
- [ ] Create TransactionMetrics.kt with data models
- [ ] Create TransactionMetricsCollector.kt
- [ ] Integrate metrics collection into DatabaseTransactionManager
- [ ] Unit tests for metrics collection

### Day 3: Metrics Exporters & Observability
- [ ] Create MetricsExporter.kt with implementations
- [ ] Integrate metrics exporters
- [ ] Create metrics exporters for monitoring systems
- [ ] Unit tests for exporters

### Day 4: Saga Framework Design & Basics
- [ ] Create SagaFramework.kt with interfaces
- [ ] Create SagaOrchestrator.kt with compensation logic
- [ ] Example saga implementations
- [ ] Unit tests for saga framework

### Days 5+: Integration Tests & Performance Testing
- [ ] Integration tests for all Phase 2 features
- [ ] Performance benchmarking
- [ ] Documentation and final commit

---

## Testing Strategy

### Unit Tests
- Timeout and retry logic
- Backoff calculation
- Metrics collection
- Saga step execution and compensation

### Integration Tests
- End-to-end transactions with timeout
- Transient failure recovery
- Metrics export pipeline
- Multi-step sagas with compensation

### Performance Tests
- Timeout overhead
- Metrics collection overhead
- Saga execution performance
- Large batch operations

---

## Acceptance Criteria

### Feature 1: Timeout & Retry
- [ ] Transactions timeout after configured duration
- [ ] Automatic retry with exponential backoff
- [ ] Deadlock detection and retry
- [ ] Configurable retry policy
- [ ] Transient errors retried, non-retryable errors fail fast

### Feature 2: Metrics & Observability
- [ ] Transaction metrics collected for all transactions
- [ ] Adapter execution tracked with timing
- [ ] Metrics exportable to monitoring systems
- [ ] Query metrics by transaction ID
- [ ] Performance overhead < 10%

### Feature 3: Saga Framework
- [ ] Multi-step transactions supported
- [ ] Compensation on failure
- [ ] Automatic rollback in reverse order
- [ ] Saga state tracked
- [ ] Integration with transaction system

---

## Success Metrics

- ✅ Zero hanging transactions
- ✅ Transient failures automatically recovered
- ✅ Complete transaction visibility
- ✅ Distributed transactions supported
- ✅ All performance targets met
- ✅ 100+ new tests passing
