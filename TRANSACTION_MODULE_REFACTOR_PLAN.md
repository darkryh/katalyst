# Complete Transaction Module Refactor Plan

## Objective
Centralize all transaction management logic into `katalyst-transactions` module and validate the entire transactionality process.

---

## Phase 1: Module Structure Analysis

### Current State (Messy)
```
katalyst-core/
  └─ DatabaseTransactionManager ← MOVE TO katalyst-transactions

katalyst-events-bus/
  ├─ TransactionContext ← MOVE TO katalyst-transactions
  ├─ TransactionAwareEventBus
  └─ publishPendingEvents() ← Depends on transaction features

katalyst-persistence/
  ├─ Repository pattern
  └─ DatabaseModule (creates DatabaseTransactionManager) ← UPDATE DEPS
```

### Target State (Clean)
```
katalyst-core/
  └─ (core interfaces & utilities - no transaction logic)

katalyst-transactions/ ← NEW responsibility
  ├─ context/
  │  ├─ TransactionContext
  │  ├─ TransactionContextElement
  │  └─ TransactionContextExtensions
  ├─ manager/
  │  ├─ DatabaseTransactionManager
  │  └─ TransactionManager interface
  ├─ hooks/
  │  ├─ TransactionHook interface
  │  ├─ TransactionPhase enum
  │  └─ Hook listeners
  └─ TransactionModule (DI configuration)

katalyst-events-bus/ ← Depends on katalyst-transactions
  ├─ TransactionAwareEventBus
  └─ publishPendingEvents()

katalyst-persistence/ ← Depends on katalyst-transactions
  ├─ Repository pattern
  └─ DatabaseModule
```

### Dependency Graph (Target)
```
katalyst-core
    ↓
katalyst-transactions  ← Fundamental layer
    ↓
    ├─→ katalyst-events-bus
    ├─→ katalyst-persistence
    ├─→ katalyst-scheduler (future)
    └─→ katalyst-caching (future)

katalyst-events ← Independent
    ↓
katalyst-events-bus
```

---

## Phase 2: katalyst-transactions Module Design

### 2.1 Directory Structure
```
katalyst-transactions/
├─ src/main/kotlin/com/ead/katalyst/transactions/
│  ├─ context/
│  │  ├─ TransactionContext.kt          [MOVE from events-bus]
│  │  └─ TransactionContextExtensions.kt [NEW]
│  ├─ manager/
│  │  ├─ DatabaseTransactionManager.kt   [MOVE from core]
│  │  └─ TransactionManager.kt           [NEW - interface]
│  ├─ hooks/
│  │  ├─ TransactionHook.kt             [NEW]
│  │  ├─ TransactionPhase.kt            [NEW]
│  │  └─ TransactionHookRegistry.kt     [NEW]
│  ├─ errors/
│  │  ├─ TransactionException.kt        [NEW]
│  │  └─ TransactionTimeoutException.kt [NEW]
│  └─ TransactionModule.kt              [NEW - DI]
└─ src/test/kotlin/...
   ├─ TransactionContextTests.kt        [NEW]
   ├─ DatabaseTransactionManagerTests.kt [NEW]
   └─ TransactionHookTests.kt           [NEW]
```

### 2.2 Core Components

#### TransactionContext (MOVE)
```kotlin
// From: katalyst-events-bus/context/TransactionContext.kt
// To: katalyst-transactions/context/TransactionContext.kt

class TransactionEventContext : AbstractCoroutineContextElement(Key) {
    fun queueEvent(event: DomainEvent)
    fun getPendingEvents(): List<DomainEvent>
    fun clearPendingEvents()
    fun hasPendingEvents(): Boolean
}
```

#### TransactionManager Interface (NEW)
```kotlin
// katalyst-transactions/manager/TransactionManager.kt

interface TransactionManager {
    suspend fun <T> transaction(block: suspend () -> T): T
    fun addHook(hook: TransactionHook)
    fun removeHook(hook: TransactionHook)
}
```

#### TransactionHook Interface (NEW)
```kotlin
// katalyst-transactions/hooks/TransactionHook.kt

enum class TransactionPhase {
    BEFORE_BEGIN,
    AFTER_BEGIN,
    BEFORE_COMMIT,
    AFTER_COMMIT,
    ON_ROLLBACK,
    AFTER_ROLLBACK
}

interface TransactionHook {
    suspend fun onPhase(phase: TransactionPhase, context: TransactionContext)
    fun priority(): Int = 0  // For ordering
}
```

#### DatabaseTransactionManager (MOVE + ENHANCE)
```kotlin
// From: katalyst-core/database/DatabaseTransactionManager.kt
// To: katalyst-transactions/manager/DatabaseTransactionManager.kt

class DatabaseTransactionManager(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val eventBus: ApplicationEventBus? = null,
    private val hookRegistry: TransactionHookRegistry = TransactionHookRegistry()
) : TransactionManager {

    suspend fun <T> transaction(block: suspend Transaction.() -> T): T {
        // Create context
        // Call hooks: BEFORE_BEGIN
        // Execute transaction
        // Call hooks: BEFORE_COMMIT
        // Commit
        // Call hooks: AFTER_COMMIT
        // Return result
    }
}
```

---

## Phase 3: Detailed Migration Steps

### Step 1: Update katalyst-transactions Module
**Files to create:**
1. `TransactionContext.kt` (move from events-bus)
2. `TransactionContextExtensions.kt` (new)
3. `TransactionManager.kt` (new interface)
4. `DatabaseTransactionManager.kt` (move from core, enhance)
5. `TransactionHook.kt` (new)
6. `TransactionPhase.kt` (new)
7. `TransactionHookRegistry.kt` (new)
8. `TransactionException.kt` (new)
9. `TransactionModule.kt` (new DI config)

**Update katalyst-transactions/build.gradle.kts:**
```gradle
dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Exposed & DB
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // DI
    implementation(libs.koin.core)

    // Logging
    implementation(libs.logback)

    // TEST
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest)
}
```

### Step 2: Update katalyst-core Module
**Actions:**
1. Remove `DatabaseTransactionManager.kt`
2. Update imports in dependent files
3. Add dependency: `implementation(projects.katalystTransactions)`
4. Update `build.gradle.kts`

### Step 3: Update katalyst-events-bus Module
**Files to modify:**
1. Move `TransactionContext.kt` to katalyst-transactions
2. Update `TransactionAwareEventBus.kt` to import from katalyst-transactions
3. Update `EventBusModule.kt`
4. Update `build.gradle.kts` to depend on katalyst-transactions

**Update katalyst-events-bus/build.gradle.kts:**
```gradle
dependencies {
    implementation(projects.katalystCore)
    implementation(projects.katalystTransactions)  ← NEW
    implementation(projects.katalystEvents)
    // ... rest
}
```

### Step 4: Update katalyst-persistence Module
**Files to modify:**
1. `DatabaseModule.kt` - update to use transaction manager from new location
2. Update imports
3. Update `build.gradle.kts`

**Update katalyst-persistence/build.gradle.kts:**
```gradle
dependencies {
    implementation(projects.katalystCore)
    implementation(projects.katalystTransactions)  ← NEW
    // ... rest
}
```

### Step 5: Update katalyst-example Module
**Files to modify:**
1. Any direct imports of transaction classes
2. Verify DI configuration still works

---

## Phase 4: Transaction Flow Validation

### 4.1 Complete Transaction Lifecycle

```
1. SERVICE LAYER
   └─ transactionManager.transaction { block }

2. TRANSACTION INITIALIZATION
   ├─ Create TransactionContext
   ├─ Call TransactionHook: BEFORE_BEGIN
   └─ Begin transaction

3. EXECUTE BLOCK
   ├─ Event published
   │  └─ TransactionAwareEventBus detects context
   │     └─ Event QUEUED in TransactionContext
   ├─ Repository operations
   └─ Business logic

4. COMMIT PHASE
   ├─ Call TransactionHook: BEFORE_COMMIT
   ├─ Exposed commits transaction
   ├─ Call TransactionHook: AFTER_COMMIT
   └─ publishPendingEvents() called

5. POST-COMMIT
   ├─ Pending events published
   ├─ Event handlers execute
   └─ Return to service layer

6. ERROR HANDLING
   ├─ Exception caught
   ├─ Call TransactionHook: ON_ROLLBACK
   ├─ Clear pending events
   ├─ Call TransactionHook: AFTER_ROLLBACK
   └─ Rethrow exception
```

### 4.2 Hook Integration Points

**Event Publishing Hook (katalyst-events-bus)**
```kotlin
class EventPublishingHook(
    private val eventBus: ApplicationEventBus
) : TransactionHook {
    override suspend fun onPhase(phase: TransactionPhase, context: TransactionContext) {
        when (phase) {
            TransactionPhase.AFTER_COMMIT -> {
                publishPendingEvents(eventBus, context, logger)
            }
            TransactionPhase.ON_ROLLBACK -> {
                context.clearPendingEvents()
            }
            else -> {} // Ignore other phases
        }
    }
}
```

**Caching Hook (Future - katalyst-caching)**
```kotlin
class CacheInvalidationHook : TransactionHook {
    override suspend fun onPhase(phase: TransactionPhase, context: TransactionContext) {
        when (phase) {
            TransactionPhase.AFTER_COMMIT -> {
                // Invalidate relevant caches
            }
            TransactionPhase.ON_ROLLBACK -> {
                // Restore cache state
            }
            else -> {}
        }
    }
}
```

---

## Phase 5: Testing Strategy

### 5.1 Unit Tests

**TransactionContextTests.kt**
- ✓ Queue events during transaction
- ✓ Retrieve pending events
- ✓ Clear pending events
- ✓ Check event count
- ✓ Thread-local isolation

**DatabaseTransactionManagerTests.kt**
- ✓ Successful transaction completes
- ✓ Failed transaction rolls back
- ✓ Hooks called in correct order
- ✓ Context available within transaction
- ✓ Context cleaned up after transaction
- ✓ Multiple transactions don't interfere

**TransactionHookTests.kt**
- ✓ Hook registration works
- ✓ Hooks called at correct phases
- ✓ Hook execution order respects priority
- ✓ Exception in hook doesn't break transaction
- ✓ Multiple hooks execute sequentially

### 5.2 Integration Tests

**TransactionAwareEventBusIntegrationTests.kt**
- ✓ Events queued during transaction
- ✓ Events published after commit
- ✓ Events discarded on rollback
- ✓ Multiple events published in order
- ✓ Event handler can access committed data
- ✓ No foreign key constraint errors

**RepositoryTransactionTests.kt**
- ✓ Save creates entity in transaction
- ✓ Update modifies entity in transaction
- ✓ Find operations work within transaction
- ✓ Rollback reverts changes
- ✓ Multiple repositories in same transaction

**End-to-End Tests (katalyst-example)**
- ✓ `AuthenticationService.register()` creates account
- ✓ `UserRegisteredEvent` published after commit
- ✓ `UserProfileService` handler creates profile
- ✓ No foreign key constraint errors
- ✓ All data visible to event handlers

---

## Phase 6: Validation Checklist

### Module Organization
- [ ] katalyst-transactions module created with proper structure
- [ ] All transaction code centralized in katalyst-transactions
- [ ] No circular dependencies
- [ ] Dependency graph is DAG (directed acyclic)

### Code Migration
- [ ] DatabaseTransactionManager moved to katalyst-transactions
- [ ] TransactionContext moved to katalyst-transactions
- [ ] TransactionHook interfaces created
- [ ] All imports updated in dependent modules
- [ ] No broken imports or references

### DI Configuration
- [ ] TransactionModule created and registered
- [ ] DatabaseModule updated to use new locations
- [ ] EventBusModule updated to depend on transactions
- [ ] Koin configuration verified

### Transaction Flow
- [ ] Transaction context created per transaction
- [ ] Hooks called at correct phases
- [ ] Events queued during transaction
- [ ] Events published after commit
- [ ] Events discarded on rollback
- [ ] No orphaned transactions
- [ ] Context cleanup on error

### Testing
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] End-to-end tests pass
- [ ] No race conditions
- [ ] No memory leaks

### Documentation
- [ ] Transaction architecture documented
- [ ] Hook system documented
- [ ] Migration guide created
- [ ] Code comments added
- [ ] Examples provided

---

## Phase 7: Implementation Order

1. **Create katalyst-transactions structure** (Empty module → Full structure)
2. **Move code** (Copy, not delete yet - for safety)
3. **Update imports** (All dependent modules)
4. **Update DI configuration** (Koin modules)
5. **Create hooks system** (TransactionHook, TransactionPhase)
6. **Enhance DatabaseTransactionManager** (Add hook support)
7. **Write unit tests** (Test transaction features)
8. **Write integration tests** (Test with events & repositories)
9. **Verify end-to-end** (Run full example scenario)
10. **Clean up** (Delete old code from original locations)

---

## Phase 8: Rollback Plan

If issues occur:
1. Keep old code in original locations initially
2. Update imports to point to new locations
3. If errors: revert imports, keep both copies temporarily
4. Fix issues in katalyst-transactions
5. Remove old copies once validated

---

## Expected Outcomes

✅ **Clean architecture** - Transaction concerns isolated
✅ **Extensible design** - Hook system enables future features (caching, audit logging, etc)
✅ **Clear dependencies** - DAG dependency graph
✅ **Robust validation** - Comprehensive test coverage
✅ **Future-proof** - Easy to add scheduling, caching, etc with hooks
✅ **No application changes** - Code remains unchanged, framework evolves

---

## Success Criteria

- All tests pass
- No circular dependencies
- Transaction context properly isolated
- Events published only after commit
- Rollback discards events
- Hook system extensible
- Performance comparable or better
- All documentation complete
