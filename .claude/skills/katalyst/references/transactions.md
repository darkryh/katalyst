# Transactions reference

`katalyst-transactions` provides transactional execution, retry, isolation, and the adapter
mechanism that defers event delivery to commit. Every `Service` gets the transaction manager
injected.

## Service helpers

```kotlin
val transactionManager: DatabaseTransactionManager

suspend fun <T> transaction(
    workflowId: String? = null,
    config: TransactionConfig? = null,
    block: suspend Transaction.() -> T
): T

suspend fun <T> workflowTransaction(workflowId: String? = null, config: TransactionConfig? = null, block: suspend Transaction.() -> T): T
suspend fun <T> currentTransaction(block: suspend Transaction.() -> T): T
```

- `transaction` — standard entry point; begins/joins a transaction, commits on success, rolls
  back on exception.
- `workflowTransaction` — transaction tied to a `workflowId`, for multi-step operations tracked
  across phases.
- `currentTransaction` — run within the transaction already in progress on this context.

```kotlin
import io.github.darkryh.katalyst.core.component.Service   // Service/Component live here

class AuthService(private val repo: AuthAccountRepository) : Service {
    suspend fun activate(id: Long) = transaction {
        val acc = repo.findById(id) ?: error("not found")
        repo.save(acc.copy(status = "active"))
    }
}
```

You may also call `transactionManager.transaction { }` directly; the helpers are conveniences.

## TransactionConfig

`io.github.darkryh.katalyst.transactions.config.TransactionConfig` (data class):

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `timeout` | `Duration` | 30 s | Max execution; exceeding cancels with timeout. |
| `retryPolicy` | `RetryPolicy` | `RetryPolicy()` | Retry behavior for failures. |
| `isolationLevel` | `TransactionIsolationLevel` | `READ_COMMITTED` | Concurrency isolation. |
| `expectedBusinessExceptions` | `Set<KClass<out Exception>>` | empty | Marks expected failures for log-severity only; does NOT change retry. |
| `phaseLoggingEnabled` | `Boolean` | `true` | Verbose phase/adapter logging for this tx. |

```kotlin
import io.github.darkryh.katalyst.transactions.config.TransactionConfig
import io.github.darkryh.katalyst.transactions.config.TransactionIsolationLevel
import kotlin.time.Duration.Companion.seconds

suspend fun chargeOnce() = transaction(
    config = TransactionConfig(timeout = 10.seconds, isolationLevel = TransactionIsolationLevel.SERIALIZABLE)
) { /* … */ }
```

## RetryPolicy

`io.github.darkryh.katalyst.transactions.config.RetryPolicy`:

| Field | Type | Meaning |
|-------|------|---------|
| `maxRetries` | `Int` | Max retry attempts. |
| `backoffStrategy` | `BackoffStrategy` | `IMMEDIATE`, `LINEAR`, or `EXPONENTIAL`. |
| `initialDelayMs` | `Long` | First backoff delay. |
| `maxDelayMs` | `Long` | Cap on backoff delay. |
| `jitterFactor` | `Double` | Randomization of backoff (0.0–1.0). |
| `retryableExceptions` | `Set<KClass<…>>` | Types that trigger a retry. |
| `nonRetryableExceptions` | `Set<KClass<…>>` | Types that never retry. |

Default: exponential backoff up to 3 retries.

## TransactionIsolationLevel

`READ_UNCOMMITTED`, `READ_COMMITTED` (default), `REPEATABLE_READ`, `SERIALIZABLE`.

## DatabaseTransactionManager

`io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager` (implements
`TransactionManager`):

| Member | Purpose |
|--------|---------|
| `transaction(workflowId, config, block)` | Run a block transactionally. |
| `addAdapter(adapter)` / `removeAdapter(adapter)` | Register/unregister a `TransactionAdapter`. |
| `getAdapterCount()` | Count of adapters. |

### Adapters and phases

A `TransactionAdapter` participates in transaction phases (`TransactionPhase`), letting
subsystems hook commit/rollback. The event bus registers one so publishing defers to commit;
persistence registers one too. `TransactionPhaseTracer` / `TransactionPhaseEvent` expose tracing.
Apps rarely implement adapters directly — the modules wire them.

## Exceptions

`io.github.darkryh.katalyst.transactions.exception.*`:

| Exception | Meaning |
|-----------|---------|
| `TransactionFailedException` | Failed, will not retry. |
| `TransactionTimeoutException` | Exceeded `timeout`. |
| `DeadlockException` | Deadlock detected. |
| `TransientException` | Retryable transient failure. |

Log severity for non-retryable failures comes from a `TransactionExceptionSeverityClassifier`
(`INFO`/`WARN`/`ERROR`); the default logs expected business failures at `WARN`, others at
`ERROR`.

## Rules of thumb

- Wrap every multi-statement write and every event-publishing operation in a transaction.
- Publish events **inside** the transaction so they defer to commit (see `references/events.md`).
- Use direct Exposed `transaction(databaseFactory.database) { }` only for migrations/one-off ops.
- Make operations idempotent where retries are enabled.
