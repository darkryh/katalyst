# Transactions

The `katalyst-transactions` module provides transactional execution for services, with
configurable timeouts, retry, and isolation. It also coordinates with the event bus so events
published inside a transaction are delivered only after commit. The transaction manager is
injected into every `Service`.

For usage in context, see [Define tables and repositories](../how-to/define-tables-and-repositories.md)
and [Publish and handle events](../how-to/publish-and-handle-events.md).

## Service transaction helpers

`Service` exposes the transaction manager and three suspend helpers. All run their block in a
managed transaction; an exception rolls everything back.

```kotlin
// The injected manager
val transactionManager: DatabaseTransactionManager

suspend fun <T> transaction(
    workflowId: String? = null,
    config: TransactionConfig? = null,
    block: suspend Transaction.() -> T
): T

suspend fun <T> workflowTransaction(
    workflowId: String? = null,
    config: TransactionConfig? = null,
    block: suspend Transaction.() -> T
): T

suspend fun <T> currentTransaction(block: suspend Transaction.() -> T): T
```

```kotlin
class AuthService(private val repository: AuthAccountRepository) : Service {
    suspend fun activate(id: Long) = transaction {
        val account = repository.findById(id)
        repository.save(account.copy(status = "active"))
    }
}
```

- `transaction` — the standard entry point. Begins (or joins) a transaction, commits on
  success, rolls back on failure.
- `workflowTransaction` — a transaction tied to a workflow id, for multi-step operations
  tracked across phases.
- `currentTransaction` — runs the block within the transaction already in progress on the
  current context.

You can also call `transactionManager.transaction { … }` directly; the helpers are
conveniences.

## TransactionConfig

Passed to any helper to tune a single transaction.

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `timeout` | `Duration` | 30 s | Maximum execution time; exceeding it cancels with a timeout. |
| `retryPolicy` | `RetryPolicy` | `RetryPolicy()` | How failed transactions retry. |
| `isolationLevel` | `TransactionIsolationLevel` | `READ_COMMITTED` | Concurrency isolation. |
| `expectedBusinessExceptions` | `Set<KClass<out Exception>>` | empty | Treated as expected failures for log-severity purposes only; does not change retry behavior. |
| `phaseLoggingEnabled` | `Boolean` | `true` | Verbose phase/adapter logging for this transaction. |

```kotlin
import kotlin.time.Duration.Companion.seconds

suspend fun chargeOnce() = transaction(
    config = TransactionConfig(
        timeout = 10.seconds,
        isolationLevel = TransactionIsolationLevel.SERIALIZABLE
    )
) { /* … */ }
```

## RetryPolicy

| Field | Type | Meaning |
|-------|------|---------|
| `maxRetries` | `Int` | Maximum retry attempts. |
| `backoffStrategy` | `BackoffStrategy` | `IMMEDIATE`, `LINEAR`, or `EXPONENTIAL`. |
| `initialDelayMs` | `Long` | First backoff delay. |
| `maxDelayMs` | `Long` | Upper bound on backoff delay. |
| `jitterFactor` | `Double` | Randomization applied to backoff (0.0–1.0). |
| `retryableExceptions` | `Set<KClass<…>>` | Exception types that trigger a retry. |
| `nonRetryableExceptions` | `Set<KClass<…>>` | Exception types that never retry. |

The default policy retries with exponential backoff up to 3 retries.

## TransactionIsolationLevel

`READ_UNCOMMITTED`, `READ_COMMITTED` (default), `REPEATABLE_READ`, `SERIALIZABLE`.

## DatabaseTransactionManager

The implementation of `TransactionManager`, injected into services.

| Member | Purpose |
|--------|---------|
| `transaction(workflowId, config, block)` | Run a block transactionally. |
| `addAdapter(adapter)` / `removeAdapter(adapter)` | Register/unregister a `TransactionAdapter`. |
| `getAdapterCount()` | Number of registered adapters. |

### Transaction adapters

A `TransactionAdapter` participates in transaction phases — for example, the event bus
registers one so publishing defers to commit. Phases are modeled by `TransactionPhase`, and
`TransactionPhaseTracer` / `TransactionPhaseEvent` expose phase tracing. The persistence and
events modules register their adapters automatically; you rarely implement one directly.

## Exceptions

| Exception | Meaning |
|-----------|---------|
| `TransactionFailedException` | The transaction failed and will not be retried. |
| `TransactionTimeoutException` | The transaction exceeded its `timeout`. |
| `DeadlockException` | A database deadlock was detected. |
| `TransientException` | A retryable, transient failure. |

Log severity for non-retryable failures is decided by a
`TransactionExceptionSeverityClassifier` (`INFO`, `WARN`, `ERROR`); the default classifier
logs expected business failures at `WARN` and unexpected ones at `ERROR`.

## See also

- [Persistence](persistence.md) — repositories and `SqlExecutor`.
- [Events](events.md) — transaction-aware publishing.
- [DI & auto-wiring](di-auto-wiring.md) — how `Service` receives the manager.

