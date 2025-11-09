# Phase 1 Implementation Plan - Critical Fixes (P0)

## Overview

**Duration**: 2 weeks
**Goal**: Fix 2 critical data consistency issues
**Impact**: Prevent partial event publishing and adapter failures

---

## Issue #1: Event Publishing Validation (2 days)

### Problem
If event N fails to publish, events 1 through N-1 already published → transaction commits with inconsistent data/events

### Solution
Add validation phase before committing to ensure ALL events are publishable

### Files to Create/Modify

#### 1. Create: EventPublishingValidator.kt
```kotlin
package com.ead.katalyst.events.bus.validation

interface EventPublishingValidator {
    /**
     * Validate that an event can be published.
     * Throws exception if validation fails.
     */
    suspend fun validate(event: DomainEvent): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null
)

class DefaultEventPublishingValidator(
    private val eventBus: ApplicationEventBus
) : EventPublishingValidator {

    override suspend fun validate(event: DomainEvent): ValidationResult {
        // Check if handlers exist for this event type
        if (!eventBus.hasHandlers(event::class)) {
            return ValidationResult(
                isValid = false,
                error = "No handlers registered for event: ${event::class.simpleName}"
            )
        }
        return ValidationResult(isValid = true)
    }
}
```

#### 2. Modify: ApplicationEventBus.kt
```kotlin
class ApplicationEventBus(...) : EventBus {

    // Add method to check if handlers exist
    fun hasHandlers(eventType: KClass<out DomainEvent>): Boolean {
        return !listeners[eventType].isNullOrEmpty()
    }

    // Keep existing publish() method
}
```

#### 3. Modify: EventsTransactionAdapter.kt
```kotlin
class EventsTransactionAdapter(
    private val eventBus: ApplicationEventBus,
    private val validator: EventPublishingValidator  // NEW
) : TransactionAdapter {

    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_COMMIT_VALIDATION -> {  // NEW PHASE
                validateAllEvents(context)
            }
            TransactionPhase.BEFORE_COMMIT -> {
                publishPendingEvents(context)
            }
            TransactionPhase.ON_ROLLBACK -> {
                discardPendingEvents(context)
            }
            else -> Unit
        }
    }

    // NEW: Validate all events before publishing
    private suspend fun validateAllEvents(context: TransactionEventContext) {
        val events = context.getPendingEvents()
        logger.debug("Validating {} pending events before commit", events.size)

        for (event in events) {
            val result = validator.validate(event)
            if (!result.isValid) {
                val error = "Event validation failed: ${result.error}"
                logger.error(error)
                throw EventValidationException(event, error)
            }
        }
        logger.debug("All events validated successfully")
    }

    // Keep existing publishPendingEvents() method
}
```

#### 4. Create: TransactionPhase.kt (UPDATE)
```kotlin
enum class TransactionPhase {
    BEFORE_BEGIN,
    AFTER_BEGIN,
    BEFORE_COMMIT_VALIDATION,  // NEW - Validate before publishing
    BEFORE_COMMIT,
    AFTER_COMMIT,
    ON_ROLLBACK,
    AFTER_ROLLBACK
}
```

#### 5. Modify: DatabaseTransactionManager.kt
```kotlin
override suspend fun <T> transaction(
    workflowId: String?,
    block: suspend Transaction.() -> T
): T {
    // ... existing code ...

    try {
        // ... existing phases ...

        // NEW: Phase for validation (before commit)
        logger.debug("Executing BEFORE_COMMIT_VALIDATION adapters")
        adapterRegistry.executeAdapters(
            TransactionPhase.BEFORE_COMMIT_VALIDATION,
            transactionEventContext,
            failFast = true  // Fail immediately if validation fails
        )

        // Phase 3: BEFORE_COMMIT adapters (still in TX)
        logger.debug("Executing BEFORE_COMMIT adapters")
        adapterRegistry.executeAdapters(
            TransactionPhase.BEFORE_COMMIT,
            transactionEventContext,
            failFast = true
        )

        // ... rest of code ...
    }
}
```

#### 6. Create: EventValidationException.kt
```kotlin
package com.ead.katalyst.events.bus.exception

class EventValidationException(
    val event: DomainEvent,
    message: String
) : RuntimeException("Event validation failed for ${event.eventType()}: $message")
```

### Testing

```kotlin
class EventPublishingValidationTest {

    @Test
    fun `transaction aborts if event has no handlers`() {
        // Setup: No handler registered for TestEvent

        // Act & Assert
        assertThrows<EventValidationException> {
            transactionManager.transaction {
                eventBus.publish(TestEventWithNoHandler())
            }
        }

        // Verify: Transaction rolled back, event not published
    }

    @Test
    fun `transaction commits if all events have handlers`() {
        // Setup: Register handlers for all events
        eventBus.register(TestEventHandler())

        // Act
        val result = transactionManager.transaction {
            repository.save(entity)
            eventBus.publish(TestEvent(entity.id))
        }

        // Assert: Transaction committed, event published
        assertEquals(entity.id, result.id)
    }

    @Test
    fun `validation happens before publish`() {
        // Verify: Validation phase executes before publish phase
    }
}
```

### Effort Estimate
- Implementation: 1 day
- Testing: 0.5 day
- **Total: 1.5 days**

---

## Issue #2: Adapter Failure Handling (3 days)

### Problem
If EventsTransactionAdapter fails in BEFORE_COMMIT phase, database commits anyway → inconsistent state

### Solution
Mark critical adapters, track execution state, rollback transaction if critical adapter fails

### Files to Create/Modify

#### 1. Modify: TransactionAdapter.kt
```kotlin
interface TransactionAdapter {
    fun name(): String
    fun priority(): Int = 0
    suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext)

    // NEW: Mark adapters as critical (failures should rollback transaction)
    fun isCritical(): Boolean = false
}
```

#### 2. Modify: EventsTransactionAdapter.kt
```kotlin
class EventsTransactionAdapter(
    private val eventBus: ApplicationEventBus,
    private val validator: EventPublishingValidator
) : TransactionAdapter {

    override fun isCritical(): Boolean = true  // NEW: Events are critical

    // ... rest of code ...
}
```

#### 3. Create: AdapterExecutionResult.kt
```kotlin
package com.ead.katalyst.transactions.adapter

data class AdapterExecutionResult(
    val adapter: TransactionAdapter,
    val phase: TransactionPhase,
    val success: Boolean,
    val error: Exception? = null,
    val duration: Long
)

data class PhaseExecutionResults(
    val phase: TransactionPhase,
    val results: List<AdapterExecutionResult>
) {
    fun hasCriticalFailures(): Boolean {
        return results.any { !it.success && it.adapter.isCritical() }
    }

    fun getCriticalFailures(): List<AdapterExecutionResult> {
        return results.filter { !it.success && it.adapter.isCritical() }
    }
}
```

#### 4. Modify: TransactionAdapterRegistry.kt
```kotlin
class TransactionAdapterRegistry {

    /**
     * Execute all adapters for a specific transaction phase.
     * Returns execution results with tracking.
     */
    suspend fun executeAdapters(
        phase: TransactionPhase,
        context: TransactionEventContext,
        failFast: Boolean = false
    ): PhaseExecutionResults {  // NEW: Track results
        if (adapters.isEmpty()) {
            logger.debug("No adapters registered for phase: {}", phase)
            return PhaseExecutionResults(phase, emptyList())
        }

        logger.debug("Executing {} adapter(s) for phase: {}", adapters.size, phase)
        val results = mutableListOf<AdapterExecutionResult>()

        for (adapter in adapters) {
            val startTime = System.currentTimeMillis()
            try {
                logger.debug("Executing adapter {} for phase {}", adapter.name(), phase)
                adapter.onPhase(phase, context)

                results.add(
                    AdapterExecutionResult(
                        adapter = adapter,
                        phase = phase,
                        success = true,
                        error = null,
                        duration = System.currentTimeMillis() - startTime
                    )
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                logger.error(
                    "Error in transaction adapter {} during {}: {}",
                    adapter.name(),
                    phase,
                    e.message,
                    e
                )

                results.add(
                    AdapterExecutionResult(
                        adapter = adapter,
                        phase = phase,
                        success = false,
                        error = e,
                        duration = duration
                    )
                )

                if (failFast && adapter.isCritical()) {  // NEW: Check if critical
                    throw TransactionAdapterException(
                        "Critical adapter failed: ${adapter.name()}",
                        e
                    )
                }

                if (failFast && !adapter.isCritical()) {
                    // Non-critical adapter failed but failFast=true, continue
                }
            }
        }

        logger.debug("Finished executing adapters for phase: {}", phase)
        return PhaseExecutionResults(phase, results)
    }
}
```

#### 5. Create: TransactionAdapterException.kt
```kotlin
package com.ead.katalyst.transactions.adapter

class TransactionAdapterException(
    message: String,
    cause: Exception? = null
) : RuntimeException(message, cause)
```

#### 6. Modify: DatabaseTransactionManager.kt
```kotlin
override suspend fun <T> transaction(
    workflowId: String?,
    block: suspend Transaction.() -> T
): T {
    // ... existing code ...

    try {
        // ... existing code ...

        // Phase: BEFORE_COMMIT_VALIDATION adapters
        val validationResults = adapterRegistry.executeAdapters(
            TransactionPhase.BEFORE_COMMIT_VALIDATION,
            transactionEventContext,
            failFast = true
        )

        // NEW: Check for critical failures
        if (validationResults.hasCriticalFailures()) {
            val failures = validationResults.getCriticalFailures()
            logger.error("Critical adapter(s) failed during validation: {}",
                failures.map { it.adapter.name() })
            throw TransactionAdapterException(
                "Critical adapter(s) failed: ${failures.map { it.adapter.name() }}"
            )
        }

        // Phase: BEFORE_COMMIT adapters
        val beforeCommitResults = adapterRegistry.executeAdapters(
            TransactionPhase.BEFORE_COMMIT,
            transactionEventContext,
            failFast = true
        )

        // NEW: Check for critical failures
        if (beforeCommitResults.hasCriticalFailures()) {
            val failures = beforeCommitResults.getCriticalFailures()
            logger.error("Critical adapter(s) failed before commit: {}",
                failures.map { it.adapter.name() })
            throw TransactionAdapterException(
                "Critical adapter(s) failed: ${failures.map { it.adapter.name() }}"
            )
        }

        // Now commit is safe
        // ... rest of code ...

    } catch (e: Exception) {
        logger.error("Transaction failed, rolling back", e)
        // ... rollback logic ...
        throw e
    }
}
```

### Testing

```kotlin
class AdapterFailureHandlingTest {

    @Test
    fun `transaction rolls back if critical adapter fails`() {
        // Setup
        val failingAdapter = FailingCriticalAdapter()
        transactionManager.addAdapter(failingAdapter)

        // Act & Assert
        assertThrows<TransactionAdapterException> {
            transactionManager.transaction {
                repository.save(entity)
            }
        }

        // Verify: Transaction rolled back
        assertNull(repository.findById(entity.id))
    }

    @Test
    fun `transaction succeeds even if non-critical adapter fails`() {
        // Setup
        val failingAdapter = FailingNonCriticalAdapter()
        transactionManager.addAdapter(failingAdapter)

        // Act
        val result = transactionManager.transaction {
            repository.save(entity)
        }

        // Assert: Transaction committed despite non-critical adapter failure
        assertNotNull(repository.findById(entity.id))
    }

    @Test
    fun `execution results track adapter state`() {
        // Verify tracking of success/failure per adapter
    }
}
```

### Effort Estimate
- Implementation: 1.5 days
- Testing: 1 day
- **Total: 2.5 days**

---

## Issue #3: Event Deduplication (4 days)

### Problem
Service retries cause duplicate events to be published

### Solution
Add event IDs and deduplication checking before publishing

### Files to Create/Modify

#### 1. Modify: DomainEvent.kt
```kotlin
interface DomainEvent {
    val eventId: String  // NEW: Unique event identifier
    fun eventType(): String
}

// In Example implementation
data class UserRegisteredEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    val accountId: Long,
    val email: String,
    val displayName: String
) : DomainEvent {
    override fun eventType() = "user.registered"
}
```

#### 2. Create: EventDeduplicationStore.kt
```kotlin
package com.ead.katalyst.events.bus.deduplication

interface EventDeduplicationStore {
    /**
     * Check if event has already been published
     */
    suspend fun isEventPublished(eventId: String): Boolean

    /**
     * Mark event as published
     */
    suspend fun markAsPublished(eventId: String, publishedAtMillis: Long = System.currentTimeMillis())

    /**
     * Delete old published event records (cleanup)
     */
    suspend fun deletePublishedBefore(beforeMillis: Long): Int
}

class InMemoryEventDeduplicationStore : EventDeduplicationStore {
    private val publishedEvents = ConcurrentHashMap<String, Long>()

    override suspend fun isEventPublished(eventId: String): Boolean {
        return publishedEvents.containsKey(eventId)
    }

    override suspend fun markAsPublished(eventId: String, publishedAtMillis: Long) {
        publishedEvents[eventId] = publishedAtMillis
    }

    override suspend fun deletePublishedBefore(beforeMillis: Long): Int {
        val keysToDelete = publishedEvents
            .filter { it.value < beforeMillis }
            .keys
        keysToDelete.forEach { publishedEvents.remove(it) }
        return keysToDelete.size
    }
}
```

#### 3. Modify: EventsTransactionAdapter.kt
```kotlin
class EventsTransactionAdapter(
    private val eventBus: ApplicationEventBus,
    private val validator: EventPublishingValidator,
    private val deduplicationStore: EventDeduplicationStore  // NEW
) : TransactionAdapter {

    private suspend fun publishPendingEvents(context: TransactionEventContext) {
        val pendingEvents = context.getPendingEvents()
        if (pendingEvents.isEmpty()) {
            logger.debug("No pending events to publish before transaction commit")
            return
        }

        logger.debug("Publishing {} pending event(s) before transaction commit", pendingEvents.size)
        for (event in pendingEvents) {
            // NEW: Check if event was already published (deduplication)
            if (deduplicationStore.isEventPublished(event.eventId)) {
                logger.warn(
                    "Duplicate event detected and skipped: {} (id: {})",
                    event::class.simpleName,
                    event.eventId
                )
                continue  // Skip duplicate
            }

            logger.debug("Publishing event: {} (id: {})", event::class.simpleName, event.eventId)
            eventBus.publish(event)

            // NEW: Mark as published
            deduplicationStore.markAsPublished(event.eventId)
        }
        context.clearPendingEvents()
        logger.debug("Finished publishing pending events")
    }
}
```

#### 4. Modify: DIConfiguration.kt (EventsTransactionAdapter registration)
```kotlin
// In EventsModule or similar
val eventDedup = InMemoryEventDeduplicationStore()
val eventsAdapter = EventsTransactionAdapter(
    eventBus,
    validator,
    eventDedup  // NEW
)
transactionManager.addAdapter(eventsAdapter)
```

#### 5. Create: EventDeduplicationCleanupJob.kt (Optional for production)
```kotlin
package com.ead.katalyst.events.bus.deduplication

/**
 * Background job to clean up old published event records.
 * Run periodically to prevent memory growth.
 */
class EventDeduplicationCleanupJob(
    private val deduplicationStore: EventDeduplicationStore,
    private val retentionDays: Int = 7  // Keep 7 days of records
) {

    suspend fun cleanup() {
        val beforeMillis = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val deleted = deduplicationStore.deletePublishedBefore(beforeMillis)
        logger.info("Cleaned up {} old published event records", deleted)
    }
}
```

### Testing

```kotlin
class EventDeduplicationTest {

    @Test
    fun `duplicate event is not published again`() {
        // Setup
        val eventId = "event-123"
        val event = TestEvent(eventId = eventId)

        // Act: Publish same event twice
        transactionManager.transaction {
            eventBus.publish(event)
        }

        transactionManager.transaction {
            eventBus.publish(event)  // Same event ID
        }

        // Assert: Handler called only once
        verify(handler, times(1)).handle(event)
    }

    @Test
    fun `different event IDs are published independently`() {
        // Setup
        val event1 = TestEvent(eventId = "event-1")
        val event2 = TestEvent(eventId = "event-2")

        // Act
        transactionManager.transaction {
            eventBus.publish(event1)
            eventBus.publish(event2)
        }

        // Assert: Both published
        verify(handler, times(2)).handle(any())
    }
}
```

### Effort Estimate
- Implementation: 2 days
- Testing: 1 day
- Production store implementation (optional): 1 day
- **Total: 2-3 days**

---

## Phase 1 Schedule

### Week 1: Tuesday-Wednesday (2 days)
**Event Publishing Validation**
- Create EventPublishingValidator
- Modify ApplicationEventBus
- Modify EventsTransactionAdapter
- Add BEFORE_COMMIT_VALIDATION phase
- Write tests
- Code review

### Week 1: Thursday-Friday (2 days)
**Adapter Failure Handling**
- Create AdapterExecutionResult
- Modify TransactionAdapterRegistry
- Modify DatabaseTransactionManager
- Mark EventsTransactionAdapter as critical
- Write tests
- Code review

### Week 2: Monday-Tuesday (2 days)
**Event Deduplication**
- Create EventDeduplicationStore
- Modify DomainEvent
- Modify EventsTransactionAdapter
- Write tests
- Code review

### Week 2: Wednesday-Friday (3 days)
**Integration Testing & Fixes**
- Chaos testing (random failures in adapters)
- Integration tests (all 3 fixes together)
- Performance tests
- Documentation
- Prepare for Phase 2

---

## Acceptance Criteria

### Issue #1: Event Publishing Validation
- ✅ Transaction aborts if any event has no handlers
- ✅ All events validated before commit
- ✅ Validation errors logged with clear messages
- ✅ No partial event publishing possible

### Issue #2: Adapter Failure Handling
- ✅ Critical adapter failures prevent transaction commit
- ✅ Execution state tracked for each adapter
- ✅ Non-critical adapter failures allow transaction to proceed
- ✅ Clear error messages indicate which adapter failed

### Issue #3: Event Deduplication
- ✅ Events have unique IDs
- ✅ Duplicate events not published
- ✅ Each retry has idempotent result
- ✅ Dedup store cleanup works

---

## Success Metrics

After Phase 1:
- ✅ 100% of events in transaction are published (no partial publishing)
- ✅ Adapter failures prevent inconsistent state
- ✅ Duplicate events handled correctly
- ✅ All existing tests pass
- ✅ No impact on DI mechanism
- ✅ Performance impact < 5%

---

## Risk Mitigation

### Risk: Changes to critical path might break existing functionality
**Mitigation**:
- Comprehensive test coverage (unit + integration)
- Backward compatibility maintained
- All existing tests must pass
- Code review by 2+ team members

### Risk: New phases might slow down transactions
**Mitigation**:
- Performance benchmarks before/after
- Async validation where possible
- Lazy loading of validators

### Risk: Event ID addition might break existing code
**Mitigation**:
- EventId has default UUID generation
- Backward compatible with existing events
- Migration path for old events

---

## Next Steps After Phase 1

1. **Testing & QA** (1 week)
   - Run full integration test suite
   - Performance testing
   - Chaos engineering tests

2. **Documentation** (2-3 days)
   - Update architecture guide
   - Document new phases and adapters
   - Create migration guide for team

3. **Phase 2 Planning** (1-2 days)
   - Schedule Phase 2 (P1 improvements)
   - Plan resource allocation
   - Prepare detailed design for timeout/retry

---

## Resources Needed

- **Team**: 2-3 senior engineers
- **Time**: 2 weeks full-time
- **Testing**: 1 week QA
- **Review**: 2 code reviews per feature
- **Documentation**: 3-4 days tech writer

Total: ~4 weeks with full team

---

## Success Definition

Phase 1 is complete when:
1. ✅ All code merged to master
2. ✅ All tests passing (unit + integration + chaos)
3. ✅ Code review approved by 2+ engineers
4. ✅ Documentation updated
5. ✅ Performance within acceptable limits
6. ✅ No data consistency issues reported in testing
7. ✅ Ready to deploy to staging for final validation

---

**Status**: 🟢 Ready to begin implementation
**Approval**: ✅ Confirmed - No impact on DI library
**Start Date**: ASAP
