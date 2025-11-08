# Transaction & Event Reliability Refactor Plan

## Problem Analysis

### Current Risk: Event Publishing Failures Are Not Transactional

**Current Flow:**
```
1. DB operations execute          [Within TX]
2. Events queued                  [Within TX]
3. DB transaction COMMITS         [Exits TX]
4. Events published               [Outside TX]  ← ❌ If fails here, DB already committed!
5. If publishing fails:
   ├─ DB: Committed (permanent)
   ├─ Events: Not published
   └─ Result: DATA INCONSISTENCY
```

**Example Scenario:**
```
User registers (DB succeeds ✅)
  ↓
UserRegisteredEvent published (fails ❌)
  ↓
Email handler never runs
Inventory not updated
Audit log missing
  ↓
System in inconsistent state! 😱
```

**Spring Boot Behavior (Synchronous Handlers):**
```
User registers (DB in TX)
  ↓
UserRegisteredEvent published (still in TX)
  ↓
Email handler runs (still in TX)
  ↓
If handler fails: Everything rolled back ✅
If succeeds: Everything commits ✅
```

### Why This Matters

- **Data Inconsistency**: DB changed but events not published
- **Downstream Systems**: External services don't know about changes
- **Audit Trails**: Missing events in audit logs
- **User Notifications**: Email/SMS not sent
- **Cache Invalidation**: Caches not cleared
- **Eventual Consistency**: Broken (never converges)

---

## Solution Approaches

### Option A: Transactional Event Publishing (Spring Boot-like)

**Concept**: Publish events BEFORE commit, within transaction context

**Implementation:**
```kotlin
override suspend fun <T> transaction(block: suspend Transaction.() -> T): T {
    val context = TransactionEventContext()

    try {
        // ... BEFORE_BEGIN, AFTER_BEGIN ...
        val result = newSuspendedTransaction { block() }

        // NEW: Publish events BEFORE commit (still in TX)
        withContext(context) {
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_COMMIT, context)
            publishEventsInTransaction(context)  // ← Runs in TX
        }

        // Commit (if publishing succeeded)
        // Rollback if publishing fails ✅

        return result
    } catch (e: Exception) {
        // Automatic rollback of everything
        throw e
    }
}
```

**Pros:**
- ✅ Atomic: DB + events both commit or both rollback
- ✅ Simple: Minimal code changes
- ✅ Like Spring Boot: Familiar pattern

**Cons:**
- ⚠️ Handler failures block commit (long TX)
- ⚠️ Event handlers run in transaction context (blocking, no async)
- ⚠️ Transactional locks held longer
- ⚠️ Complex handlers slow down commits
- ❌ Handlers must be synchronous (no async operations)

---

### Option B: Event Outbox Pattern (Recommended) ⭐

**Concept**: Store events in DB as part of transaction, publish separately

**Architecture:**
```
Transaction:
  ├─ Save business data (User, Order, etc.)
  ├─ Save events to outbox table (in SAME TX)
  └─ Commit atomically

Separate Process (Event Publisher):
  ├─ Poll outbox table
  ├─ Publish events to handlers
  ├─ Mark as published
  └─ Retry on failure
```

**Implementation Flow:**
```
1. User creates account
2. Within transaction:
   ├─ INSERT into users table
   ├─ INSERT into event_outbox (UserRegisteredEvent)
   └─ COMMIT

3. Separate Publisher Service:
   ├─ SELECT unpublished from event_outbox
   ├─ Publish to handlers
   ├─ UPDATE event_outbox SET published=true
   └─ Retry if fails

4. If handler fails:
   ├─ Event remains in outbox
   ├─ Retry service picks it up
   └─ Eventually published ✅
```

**Pros:**
- ✅ **Atomic**: Events stored with DB data (both commit or both fail)
- ✅ **Reliable**: No event loss
- ✅ **Async**: Publish separately from transaction
- ✅ **Retry**: Failed events automatically retried
- ✅ **Decoupled**: Handlers don't block commits
- ✅ **Industry Standard**: Used by Netflix, Uber, Stripe
- ✅ **Audit Trail**: All events persisted

**Cons:**
- ⚠️ More complex implementation
- ⚠️ Additional database table
- ⚠️ Eventual consistency (slight delay before handlers run)
- ⚠️ Handlers must be idempotent
- ⚠️ Requires background job

---

### Option C: Failed Events Queue Pattern

**Concept**: Try immediate publish after commit, store failures for retry

**Implementation:**
```
1. Transaction commits (DB safe)
2. Try publish events (best-effort)
3. If fails:
   ├─ Store in failed_events table
   └─ Retry service handles them

4. Retry Service:
   ├─ Poll failed_events
   ├─ Republish with exponential backoff
   └─ Mark as published on success
```

**Pros:**
- ✅ Immediate publish for success case
- ✅ Fallback for failures
- ✅ Simpler than outbox

**Cons:**
- ⚠️ Events may not publish
- ⚠️ Handler race conditions possible
- ⚠️ Complex retry logic

---

## Recommended Solution: Event Outbox Pattern

**Why**: Combines reliability of Spring Boot with async scalability of event-driven systems.

### Architecture Design

**New Components:**

```
katalyst-transactions
├── (unchanged) DatabaseTransactionManager
└── (unchanged) TransactionEventContext

katalyst-events
├── (unchanged) DomainEvent
└── (new) OutboxEvent interface

katalyst-events-bus
├── (unchanged) ApplicationEventBus
├── (new) OutboxEventStore interface
├── (new) OutboxAdapter: TransactionAdapter
└── (new) OutboxPublisher service

katalyst-persistence
├── (new) OutboxTable
├── (new) OutboxRepository
└── (new) OutboxEventStore implementation

katalyst-scheduler (new)
└── OutboxPublisherJob: Background job for publishing
```

### Database Schema

```sql
CREATE TABLE event_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(255) NOT NULL,
    event_payload JSON NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    failed_attempts INT DEFAULT 0,
    last_error TEXT NULL,

    INDEX idx_published (published_at),
    INDEX idx_created (created_at)
);
```

### Code Structure

**New Interface:**
```kotlin
// katalyst-events/OutboxEvent.kt
interface OutboxEvent : DomainEvent {
    val outboxId: Long?  // Set by store
    val publishedAt: Long?
}

// katalyst-events-bus/OutboxEventStore.kt
interface OutboxEventStore {
    suspend fun save(event: OutboxEvent): OutboxEvent
    suspend fun markAsPublished(eventId: Long)
    suspend fun getPendingEvents(): List<OutboxEvent>
    suspend fun recordFailure(eventId: Long, error: String)
}
```

**Adapter:**
```kotlin
// katalyst-events-bus/OutboxAdapter.kt
class OutboxAdapter(
    private val outboxStore: OutboxEventStore
) : TransactionAdapter {

    override fun name() = "Outbox"
    override fun priority() = 8  // After persistence (10) but before events (5)

    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_COMMIT -> {
                // Save all pending events to outbox table
                // Still within transaction - atomicity guaranteed
                val events = context.getPendingEvents()
                for (event in events) {
                    outboxStore.save(event as OutboxEvent)
                }
            }
            TransactionPhase.AFTER_COMMIT -> {
                // Don't publish here - let background job do it
                logger.debug("Events saved to outbox for later publication")
            }
            else -> {}
        }
    }
}

// katalyst-scheduler/OutboxPublisherJob.kt
class OutboxPublisherJob(
    private val outboxStore: OutboxEventStore,
    private val eventBus: ApplicationEventBus
) {
    suspend fun publishPending() {
        val pendingEvents = outboxStore.getPendingEvents()

        for (event in pendingEvents) {
            try {
                eventBus.publish(event)
                outboxStore.markAsPublished(event.outboxId!!)
                logger.info("Published event from outbox: {}", event::class.simpleName)
            } catch (e: Exception) {
                outboxStore.recordFailure(event.outboxId!!, e.message!!)
                logger.error("Failed to publish event, will retry: {}", e.message, e)
                // Job retry will pick this up again
            }
        }
    }
}
```

---

## Migration Steps

### Phase 1: Create Event Outbox Infrastructure
- [ ] Define OutboxEvent interface
- [ ] Define OutboxEventStore interface
- [ ] Create OutboxTable (Exposed Table)
- [ ] Create OutboxRepository
- [ ] Create OutboxEventStore implementation

### Phase 2: Create OutboxAdapter
- [ ] Implement OutboxAdapter (saves events to DB)
- [ ] Register in DI bootstrap
- [ ] Update transactionManager to use BEFORE_COMMIT

### Phase 3: Create Background Publisher
- [ ] Implement OutboxPublisherJob
- [ ] Setup scheduler to run periodically
- [ ] Implement retry logic with exponential backoff

### Phase 4: Update Event System
- [ ] Make DomainEvent extend OutboxEvent
- [ ] Update event handlers to be idempotent
- [ ] Disable old EventsTransactionAdapter (keep for backwards compat)

### Phase 5: Testing & Validation
- [ ] Test outbox event storage
- [ ] Test event publishing from outbox
- [ ] Test failure scenarios
- [ ] Test idempotency

---

## Comparison: All Approaches

| Aspect | Current (After Commit) | Option A (Pre-commit) | Option C (Failed Queue) | **Option B (Outbox)** |
|--------|-------|-----------|-----------|-----------|
| **Atomicity** | ❌ No | ✅ Yes | ⚠️ Partial | ✅ Yes |
| **Event Loss** | ❌ High | ❌ None | ⚠️ Some | ✅ None |
| **Handler Blocking** | ❌ No | ⚠️ Yes | ❌ No | ✅ No |
| **Async Support** | ✅ Yes | ❌ No | ✅ Yes | ✅ Yes |
| **Complexity** | ✅ Low | ✅ Low | ⚠️ Medium | ⚠️ High |
| **Scalability** | ⚠️ Medium | ❌ Poor | ⚠️ Medium | ✅ Excellent |
| **Eventual Consistency** | ❌ Never | ✅ Immediate | ⚠️ Eventually | ⚠️ Eventually |
| **Industry Use** | ✅ Common | ✅ Common | ⚠️ Rare | ✅✅ Netflix, Uber, Stripe |

---

## Implementation Priority

### Short Term (Reduce Risk)
1. **Add comprehensive error handling** to EventsTransactionAdapter
2. **Implement monitoring & alerting** for event publishing failures
3. **Add audit logging** of all event publishing attempts
4. **Create manual retry process** for failed events

### Medium Term (Improve Reliability)
5. **Implement Option C** (Failed Events Queue) as intermediate step
6. **Add dead letter queue** for permanently failed events
7. **Setup retry job** with exponential backoff

### Long Term (Production Grade)
8. **Implement Event Outbox Pattern** (Option B)
9. **Make events first-class** in domain model
10. **Add event versioning & schema evolution**
11. **Setup event replay** capability

---

## Recommendation

**Use Event Outbox Pattern (Option B)** because:

1. ✅ **Guaranteed Delivery**: Events never lost
2. ✅ **Atomic with DB**: Both succeed or both fail
3. ✅ **Async & Scalable**: Separate publish process
4. ✅ **Proven**: Industry standard at Netflix, Uber, Stripe
5. ✅ **Resilient**: Built-in retry mechanism
6. ✅ **Observable**: All events in DB for audit
7. ✅ **Idempotent**: Can replay events safely

**Immediate Action** (if can't implement outbox immediately):
1. Add error handling to catch publishing failures
2. Log to database for manual replay
3. Add monitoring/alerting
4. Document the current limitation

---

## Code Examples

### Current Risk (DO NOT USE):
```kotlin
❌ transactionManager.transaction {
    userRepository.save(user)
    eventBus.publish(UserCreatedEvent(...))  // Can fail outside TX!
}
```

### Option A - Transactional Publishing:
```kotlin
✅ transactionManager.transaction {
    userRepository.save(user)
    eventBus.publishSync(UserCreatedEvent(...))  // Synced, blocks commit
    // If publishSync fails: Everything rolled back
}
// ⚠️ But handlers block commit, no async
```

### Option B - Event Outbox (RECOMMENDED):
```kotlin
✅ transactionManager.transaction {
    val user = userRepository.save(user)
    outboxStore.save(UserCreatedEvent(...))  // Saves to outbox table
    // Event now in DB, part of transaction ✅
}
// Later: Background job publishes from outbox
// If fails: Event remains for retry ✅
// Handlers run async, don't block ✅
```

---

## Risk Mitigation (Until Outbox Implemented)

```kotlin
class SafeEventPublishing {
    suspend fun publishWithFallback(event: DomainEvent) {
        try {
            eventBus.publish(event)
            logger.info("Event published: {}", event::class.simpleName)
        } catch (e: Exception) {
            logger.error("CRITICAL: Event publishing failed: {}", e.message, e)
            // TODO: Save to failed_events table
            // TODO: Alert operations team
            // TODO: Manual retry process
            // For now: At least log the failure
        }
    }
}
```

---

## Summary

| Current State | Risk | Solution |
|--------------|------|----------|
| Events published AFTER commit | Event loss if publish fails | Use Event Outbox Pattern |
| No retry mechanism | Failed events never published | Add background retry job |
| No audit trail | Can't replay events | Store events in DB |
| Sync handlers would block | Not scalable | Use async + outbox |

**Immediate**: Add error handling & monitoring
**Short-term**: Implement Option C (failed events queue)
**Long-term**: Implement Option B (event outbox - industry standard)
