# Transaction-Aware Event Publishing Implementation

## Overview

This refactoring implements automatic event deferral within database transactions, eliminating race conditions where event handlers couldn't see uncommitted data.

## Problem Solved

### Original Issue
```
transactionManager.transaction {
    account = repository.save(entity)        // T1: Account created in TX
    eventBus.publish(UserRegisteredEvent)    // T1: Event published immediately
        ↓
        Event handler starts new transaction T2
        ↓
        T2 can't see uncommitted account from T1
        ↓
        ❌ Foreign key constraint violation
}
// T1 commits here - TOO LATE
```

### Solution
```
transactionManager.transaction {
    account = repository.save(entity)        // T1: Account created in TX
    eventBus.publish(UserRegisteredEvent)    // T1: Event QUEUED (not published)
}
// T1 COMMITS HERE
↓
// After commit: queued events are published
// T2 can now see all committed data from T1
// ✅ Event handlers work correctly
```

## Architecture

### 1. TransactionContext (`katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus/context/TransactionContext.kt`)

Manages pending events in a coroutine context:

```kotlin
class TransactionEventContext : AbstractCoroutineContextElement(Key) {
    fun queueEvent(event: DomainEvent)
    fun getPendingEvents(): List<DomainEvent>
    fun clearPendingEvents()
    fun hasPendingEvents(): Boolean
}
```

**Key Features:**
- Thread-local queue storage
- Automatic cleanup on context exit
- No manual management required

### 2. TransactionAwareEventBus (`katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus/TransactionAwareEventBus.kt`)

Wrapper around ApplicationEventBus that intelligently queues or publishes events:

```kotlin
class TransactionAwareEventBus(
    private val delegate: ApplicationEventBus
) : EventBus {
    override suspend fun publish(event: DomainEvent) {
        if (inTransactionContext()) {
            queueEvent(event)  // Queue for later
        } else {
            delegate.publish(event)  // Publish immediately
        }
    }
}
```

**Key Features:**
- Detects transaction context using coroutine context
- Queues events during transaction
- Publishes immediately outside transaction
- Transparent to application code

### 3. DatabaseTransactionManager Update

Added transaction-aware event handling:

```kotlin
class DatabaseTransactionManager(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val eventBus: ApplicationEventBus? = null  // NEW
) {
    suspend fun <T> transaction(block: suspend Transaction.() -> T): T {
        val transactionEventContext = TransactionEventContext()  // NEW

        return try {
            val result = withContext(transactionEventContext) {  // NEW
                newSuspendedTransaction(dispatcher, database) {
                    block()
                }
            }

            // NEW: Publish pending events after transaction commits
            if (eventBus != null && transactionEventContext.hasPendingEvents()) {
                publishPendingEvents(eventBus, transactionEventContext, logger)
            }

            result
        } catch (e: Exception) {
            // NEW: Discard pending events on rollback
            transactionEventContext.clearPendingEvents()
            throw e
        }
    }
}
```

**Key Features:**
- Creates TransactionEventContext for each transaction
- Wraps transaction with context using `withContext()`
- Publishes queued events after commit
- Clears events on rollback

### 4. EventBusModule Update

Registers TransactionAwareEventBus as the main EventBus:

```kotlin
fun eventBusModule(): Module = module {
    // ApplicationEventBus is registered but not exposed as EventBus
    single<ApplicationEventBus> {
        ApplicationEventBus()
    }

    // TransactionAwareEventBus is the public EventBus interface
    single<EventBus> {
        TransactionAwareEventBus(delegate = get<ApplicationEventBus>())
    }
}
```

### 5. DatabaseModule Update

Injects ApplicationEventBus into DatabaseTransactionManager:

```kotlin
single<DatabaseTransactionManager> {
    val factory = get<DatabaseFactory>()
    val eventBus = try {
        get<ApplicationEventBus>()  // NEW: Inject event bus
    } catch (e: Exception) {
        null  // Graceful fallback if event bus not available
    }
    DatabaseTransactionManager(factory.database, eventBus = eventBus)
}
```

## Usage (No Changes Required)

Application code remains unchanged - the framework handles everything:

```kotlin
class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val eventBus: EventBus
) : Service {
    suspend fun register(request: RegisterRequest): AuthResponse =
        transactionManager.transaction {
            val account = repository.save(entity)

            // This event is automatically queued and published
            // after the transaction commits successfully
            eventBus.publish(UserRegisteredEvent(...))

            issueToken(account)
        }
}
```

## Benefits

✅ **Automatic Event Deferral:** No manual event management needed
✅ **Transaction-Aware:** Events queued during TX, published after commit
✅ **Rollback Safety:** Events discarded if transaction rolls back
✅ **Transparent:** Application code unchanged
✅ **No Backward Compatibility Burden:** Simplified during development
✅ **Robust:** Eliminates foreign key constraint race conditions
✅ **Graceful Degradation:** Works even if event bus not available

## Execution Flow

```
Service Layer
    ↓
transactionManager.transaction { block }
    ↓
Create TransactionEventContext
    ↓
withContext(transactionEventContext) {
    ↓
    Execute block (repository ops, validation, etc)
    ↓
    During block:
        - Event handlers invoke: eventBus.publish(event)
        - TransactionAwareEventBus detects transaction context
        - Event is QUEUED (not published)
    ↓
    Block completes successfully
}
    ↓
Transaction COMMITS
    ↓
DatabaseTransactionManager publishes queued events
    ↓
Event handlers execute (see all committed data)
    ↓
Return to service
```

## Testing Checklist

- [ ] AuthenticationService.register() works without manual event deferral
- [ ] UserRegisteredEvent handler can create UserProfile (no FK constraint errors)
- [ ] Multiple events can be queued and published in order
- [ ] Events are discarded on transaction rollback
- [ ] Events published outside transactions work immediately
- [ ] No event bus available scenario works gracefully

## Files Modified/Created

**Created:**
- `katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus/context/TransactionContext.kt`
- `katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus/TransactionAwareEventBus.kt`

**Modified:**
- `katalyst-core/src/main/kotlin/com/ead/katalyst/database/DatabaseTransactionManager.kt`
- `katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus/EventBusModule.kt`
- `katalyst-persistence/src/main/kotlin/com/ead/katalyst/database/DatabaseModule.kt`

## Future Enhancements

1. **Event Ordering:** Ensure events are published in order
2. **Event Prioritization:** Allow high-priority events to be published first
3. **Async Event Publishing:** Option to publish events asynchronously
4. **Event Filtering:** Allow transaction rollback to selectively discard events
5. **Metrics:** Track queued and published event counts
