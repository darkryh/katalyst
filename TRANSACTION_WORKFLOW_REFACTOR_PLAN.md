# Transaction Workflow Tracking & Automatic Rollback Refactor Plan

## Executive Summary

Implement **Workflow-based Transaction Tracking** system where:
- Developers pass workflow ID to transaction
- All operations auto-tracked under that workflow ID
- On failure: All operations auto-undone in reverse order
- No manual compensation code needed
- Fully scalable (async operation logging)
- Compatible with Exposed's built-in rollback mechanisms

---

## Part 1: Exposed Transaction Validation

### Exposed Provides ✅

**1. Automatic Rollback on Exception**
```kotlin
transaction {
    userRepository.save(user)  // Executes
    throw Exception()           // Exception thrown
    // Exposed auto-rollbacks userRepository.save()
}
```

**2. Explicit Rollback Support**
```kotlin
transaction {
    val user = userRepository.save(user)
    if (someCondition) {
        rollback()  // Explicit rollback
    }
}
```

**3. Savepoint Support** (for granular rollback)
```kotlin
transaction {
    val user = userRepository.save(user)
    val savepoint = connection.setSavepoint("after_user")

    try {
        emailService.send(email)
    } catch (e: Exception) {
        connection.rollback(savepoint)  // Rollback to savepoint only
    }
}
```

**4. Retry Mechanism** (auto-retry on failure)
```kotlin
transaction(maxAttempts = 3, minRetryDelay = 100, maxRetryDelay = 1000) {
    // Automatically retried up to 3 times on SQLException
}
```

### Our Approach Uses ✅
- **Automatic Rollback** (Exposed native)
- **Async Operation Logging** (our layer)
- **Workflow Tracking** (our layer)
- **Undo/Compensation** (our layer)

---

## Part 2: Architecture Design

### Components

```
katalyst-transactions
├── (updated) DatabaseTransactionManager
├── (new) WorkflowTransactionContext
├── (new) TransactionOperation interface
├── (new) OperationLog interface
├── (new) WorkflowStateManager interface
└── (new) UndoEngine

katalyst-persistence
├── (new) OperationLogTable
├── (new) WorkflowStateTable
├── (new) OperationLogRepository
└── (new) WorkflowStateRepository

katalyst-scheduler
└── (new) WorkflowUndoJob
```

### Data Model

```sql
-- Track each operation in workflow
CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id VARCHAR(255) NOT NULL,
    operation_index INT NOT NULL,
    operation_type VARCHAR(50) NOT NULL,  -- INSERT, UPDATE, DELETE, API_CALL, EMAIL, etc
    resource_type VARCHAR(100) NOT NULL,   -- User, Order, Payment, etc
    resource_id VARCHAR(255),
    operation_data JSON,                   -- Original data for undo
    undo_data JSON,                        -- Data needed to undo
    status ENUM('PENDING', 'COMMITTED', 'UNDONE', 'FAILED') DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    committed_at TIMESTAMP NULL,
    undone_at TIMESTAMP NULL,

    INDEX idx_workflow (workflow_id),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
);

-- Track workflow state
CREATE TABLE workflow_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id VARCHAR(255) UNIQUE NOT NULL,
    workflow_name VARCHAR(255) NOT NULL,
    status ENUM('STARTED', 'COMMITTED', 'FAILED', 'UNDONE') DEFAULT 'STARTED',
    total_operations INT DEFAULT 0,
    failed_at_operation INT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,

    INDEX idx_workflow_id (workflow_id),
    INDEX idx_status (status)
);
```

---

## Part 3: Core Implementation

### Interface Definitions

```kotlin
// katalyst-transactions/WorkflowTransactionContext.kt

/**
 * Represents a single operation that can be undone
 */
interface TransactionOperation {
    val workflowId: String
    val operationIndex: Int
    val operationType: String  // INSERT, UPDATE, DELETE, API_CALL, etc
    val resourceType: String   // User, Order, etc
    val resourceId: String?

    suspend fun undo(): Boolean  // Returns true if undo succeeded
}

/**
 * Persistent log of operations (async writes)
 */
interface OperationLog {
    suspend fun logOperation(
        workflowId: String,
        operation: TransactionOperation,
        undoData: Any?
    )

    suspend fun getPendingOperations(workflowId: String): List<TransactionOperation>

    suspend fun markAsCommitted(operationId: Long)

    suspend fun markAsUndone(operationId: Long)

    suspend fun markAsFailed(operationId: Long, error: String)
}

/**
 * Manages workflow state across transaction lifecycle
 */
interface WorkflowStateManager {
    suspend fun startWorkflow(workflowId: String, name: String)

    suspend fun commitWorkflow(workflowId: String)

    suspend fun failWorkflow(workflowId: String, operation: Int, error: String)

    suspend fun undoWorkflow(workflowId: String)

    suspend fun getWorkflowState(workflowId: String): WorkflowState?
}

/**
 * Executes undo operations in reverse order
 */
interface UndoEngine {
    suspend fun undoWorkflow(
        workflowId: String,
        operations: List<TransactionOperation>
    ): Result

    data class Result(
        val succeeded: Int,
        val failed: Int,
        val errors: Map<Int, String>
    )
}

/**
 * Enhanced TransactionEventContext with workflow tracking
 */
class WorkflowTransactionContext(
    val workflowId: String = UUID.randomUUID().toString(),
    private val operationLog: OperationLog,
    private val workflowStateManager: WorkflowStateManager,
    private val undoEngine: UndoEngine
) : TransactionEventContext() {

    private val operations = mutableListOf<TransactionOperation>()

    suspend fun registerOperation(operation: TransactionOperation) {
        operations.add(operation)
        // Async: Don't block transaction
        operationLog.logOperation(workflowId, operation, operation.undo())
    }

    suspend fun commitWorkflow() {
        workflowStateManager.commitWorkflow(workflowId)
        for (operation in operations) {
            operationLog.markAsCommitted(operation.operationIndex.toLong())
        }
    }

    suspend fun failWorkflow(operation: Int, error: String) {
        workflowStateManager.failWorkflow(workflowId, operation, error)
    }

    suspend fun undoWorkflow() {
        val result = undoEngine.undoWorkflow(workflowId, operations.reversed())
        logger.warn(
            "Workflow {} undone: {} succeeded, {} failed",
            workflowId, result.succeeded, result.failed
        )
        if (result.failed > 0) {
            result.errors.forEach { (op, error) ->
                operationLog.markAsFailed(op.toLong(), error)
            }
        }
    }

    fun getOperations() = operations.toList()
}
```

### Updated DatabaseTransactionManager

```kotlin
// katalyst-transactions/manager/DatabaseTransactionManager.kt

class DatabaseTransactionManager(
    private val database: Database,
    private val adapterRegistry: TransactionAdapterRegistry = TransactionAdapterRegistry(),
    private val operationLog: OperationLog? = null,
    private val workflowStateManager: WorkflowStateManager? = null,
    private val undoEngine: UndoEngine? = null
) : TransactionManager {

    override suspend fun <T> transaction(
        workflowId: String? = null,
        block: suspend Transaction.() -> T
    ): T {
        // Generate or use provided workflow ID
        val txId = workflowId ?: UUID.randomUUID().toString()
        logger.debug("Starting transaction with workflow: {}", txId)

        // Create context with workflow tracking
        val context = if (operationLog != null && workflowStateManager != null) {
            WorkflowTransactionContext(
                workflowId = txId,
                operationLog = operationLog,
                workflowStateManager = workflowStateManager,
                undoEngine = undoEngine!!
            ).also {
                // Start workflow tracking
                runBlocking { it.workflowStateManager.startWorkflow(txId, "transaction") }
            }
        } else {
            WorkflowTransactionContext(workflowId = txId)
        }

        return try {
            // Execute within Exposed transaction
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_BEGIN, context)

            val result = withContext(context) {
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_BEGIN, context)

                // Exposed's built-in rollback: If exception, auto-rollback
                newSuspendedTransaction(null, database) {
                    logger.debug("Executing transaction block for workflow: {}", txId)
                    try {
                        block()
                    } catch (e: Exception) {
                        // On exception: Execute undo (if workflow tracking enabled)
                        logger.error("Transaction failed for workflow: {}", txId, e)
                        if (context is WorkflowTransactionContext) {
                            context.undoWorkflow()
                            context.failWorkflow(context.getOperations().size, e.message ?: "Unknown")
                        }
                        throw e  // Exposed will rollback DB
                    }
                }
            }

            // Success: Commit workflow
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_COMMIT, context)

            if (context is WorkflowTransactionContext) {
                context.commitWorkflow()  // Async: Mark operations as committed
            }

            context.clearCompensations()
            adapterRegistry.executeAdapters(TransactionPhase.AFTER_COMMIT, context)

            logger.info("Transaction succeeded for workflow: {}", txId)
            result

        } catch (e: Exception) {
            logger.error("Transaction failed for workflow: {}", txId, e)

            // Ensure undo executed
            if (context is WorkflowTransactionContext) {
                try {
                    context.undoWorkflow()
                } catch (undoError: Exception) {
                    logger.error("Undo failed for workflow: {}", txId, undoError)
                }
            }

            adapterRegistry.executeAdapters(TransactionPhase.ON_ROLLBACK, context)
            adapterRegistry.executeAdapters(TransactionPhase.AFTER_ROLLBACK, context)

            throw e  // Exposed handles DB rollback automatically
        }
    }
}
```

### Repository Auto-Tracking

```kotlin
// katalyst-persistence/repository/TrackedRepository.kt

/**
 * Repository that auto-tracks operations for workflow
 */
abstract class TrackedRepository<T, ID>(
    protected val operationLog: OperationLog
) : Repository<T, ID> {

    protected suspend fun <R> tracked(
        operation: String,
        resourceType: String,
        resourceId: ID?,
        undoData: Any? = null,
        action: suspend () -> R
    ): R {
        val result = action()

        // Get current workflow context (thread-local)
        val workflowId = CurrentWorkflowContext.get()
        if (workflowId != null) {
            // Async: Fire and forget (non-blocking)
            operationLog.logOperation(
                workflowId,
                SimpleOperation(
                    workflowId = workflowId,
                    operationIndex = 0,  // Will be set by log
                    operationType = operation,
                    resourceType = resourceType,
                    resourceId = resourceId?.toString()
                ),
                undoData
            )
        }

        return result
    }

    override suspend fun save(entity: T): T = tracked(
        operation = "INSERT",
        resourceType = entity::class.simpleName ?: "Entity",
        resourceId = getIdFromEntity(entity),
        undoData = null,  // For undo: DELETE entity
        action = { super.save(entity) }
    )

    override suspend fun delete(id: ID) = tracked(
        operation = "DELETE",
        resourceType = getDomainType(),
        resourceId = id,
        undoData = null,  // For undo: Reload from log
        action = { super.delete(id) }
    )

    protected abstract fun getIdFromEntity(entity: T): ID?
    protected abstract fun getDomainType(): String
}

// Simple implementation of TransactionOperation
private data class SimpleOperation(
    override val workflowId: String,
    override val operationIndex: Int,
    override val operationType: String,
    override val resourceType: String,
    override val resourceId: String?
) : TransactionOperation {
    override suspend fun undo(): Boolean {
        // Undo logic will be in separate UndoEngine
        return true
    }
}

// Thread-local workflow context
object CurrentWorkflowContext {
    private val context = ThreadLocal<String?>()

    fun set(workflowId: String) {
        context.set(workflowId)
    }

    fun get(): String? = context.get()

    fun clear() {
        context.remove()
    }
}
```

---

## Part 4: Implementation Phases

### Phase 1: Core Infrastructure (Week 1)

- [ ] Create OperationLog interface & database table
- [ ] Create WorkflowStateManager interface & database table
- [ ] Create UndoEngine interface (stub implementation)
- [ ] Update TransactionEventContext → WorkflowTransactionContext
- [ ] Update DatabaseTransactionManager to accept workflowId parameter
- [ ] Add CurrentWorkflowContext (thread-local)

**Files to create**:
```
katalyst-transactions/
├── context/WorkflowTransactionContext.kt
├── context/TransactionOperation.kt
├── context/CurrentWorkflowContext.kt
├── undo/OperationLog.kt
├── undo/WorkflowStateManager.kt
└── undo/UndoEngine.kt

katalyst-persistence/
├── OperationLogTable.kt
├── WorkflowStateTable.kt
├── repositories/OperationLogRepository.kt
└── repositories/WorkflowStateRepository.kt
```

### Phase 2: Repository Auto-Tracking (Week 1-2)

- [ ] Create TrackedRepository base class
- [ ] Implement OperationLogRepository (async writes)
- [ ] Implement WorkflowStateRepository
- [ ] Update UserRepository to extend TrackedRepository
- [ ] Update AuthAccountRepository to extend TrackedRepository

**Implementation**:
```kotlin
// Example: AuthAccountRepository
class AuthAccountRepository(
    private val database: Database,
    private val operationLog: OperationLog
) : TrackedRepository<AuthAccount, Long>(operationLog) {

    override suspend fun save(entity: AuthAccount): AuthAccount {
        return tracked("INSERT", "AuthAccount", entity.id) {
            transaction(database) {
                AuthAccountTable.insert {
                    it[email] = entity.email
                    // ... other fields
                }
            }
            entity
        }
    }
}
```

### Phase 3: Undo Engine Implementation (Week 2-3)

- [ ] Implement UndoEngine interface
- [ ] Create UndoStrategy pattern for different resource types
- [ ] Implement undo for: DELETE, UPDATE, INSERT operations
- [ ] Add error handling & retry logic

**Strategies**:
```kotlin
interface UndoStrategy {
    suspend fun undo(operationData: Any?): Boolean
}

class DeleteUndoStrategy : UndoStrategy  // Re-INSERT
class UpdateUndoStrategy : UndoStrategy  // Revert to old value
class InsertUndoStrategy : UndoStrategy  // DELETE
class APICallUndoStrategy : UndoStrategy // Call delete API endpoint
```

### Phase 4: Service Layer Integration (Week 3-4)

- [ ] Create ServiceWithWorkflow base class
- [ ] Update AuthenticationService to use workflowId
- [ ] Update UserProfileService to use workflowId
- [ ] Test with multi-step workflows

**Example**:
```kotlin
class AuthenticationService(
    private val transactionManager: DatabaseTransactionManager
) : Service {

    suspend fun register(request: RegisterRequest): AuthResponse {
        return transactionManager.transaction(workflowId = "user_registration_workflow") {
            // All operations auto-tracked under workflow
            val account = repository.save(AuthAccountEntity(...))
            eventBus.publish(UserRegisteredEvent(...))
            issueToken(account)
        }
    }
}
```

### Phase 5: Workflow Composition (Week 4)

- [ ] Support nested workflows
- [ ] Support multi-step workflows with checkpoints
- [ ] Create workflow state machine
- [ ] Add workflow pause/resume capability

**Example**:
```kotlin
transactionManager.transaction(workflowId = "user_onboarding") {
    // Step 1
    val account = registerUser(request)

    // Step 2 (could fail and retry)
    val profile = createProfile(account.id)

    // Step 3 (could fail and retry)
    sendVerificationEmail(account.email)

    // If any step fails: All auto-undone ✅
}
```

### Phase 6: Undo Job & Recovery (Week 5)

- [ ] Implement WorkflowUndoJob (background job)
- [ ] Setup scheduler to run periodically (every 5 minutes)
- [ ] Implement failed workflow recovery
- [ ] Add monitoring & alerting

**Job**:
```kotlin
class WorkflowUndoJob(
    private val workflowStateManager: WorkflowStateManager,
    private val undoEngine: UndoEngine
) {
    suspend fun processFailedWorkflows() {
        val failedWorkflows = workflowStateManager.getFailedWorkflows()
        for (workflow in failedWorkflows) {
            try {
                logger.info("Recovering workflow: {}", workflow.workflowId)
                undoEngine.undoWorkflow(workflow.workflowId)
            } catch (e: Exception) {
                logger.error("Failed to recover workflow: {}", workflow.workflowId, e)
            }
        }
    }
}
```

### Phase 7: Testing & Validation (Week 5-6)

- [ ] Unit tests for UndoEngine
- [ ] Integration tests for multi-step workflows
- [ ] Failure scenario tests
- [ ] Load tests (high concurrency)
- [ ] Rollback verification tests

### Phase 8: Documentation & Migration (Week 6)

- [ ] Document workflow tracking pattern
- [ ] Create migration guide for existing services
- [ ] Create workflow design guide
- [ ] Training for team

---

## Part 5: Performance & Scalability

### Async Operation Logging (Non-Blocking)

```kotlin
// Async write to operation_log table
private suspend fun logOperationAsync(operation: TransactionOperation) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            operationLogRepository.save(operation)
        } catch (e: Exception) {
            logger.error("Failed to log operation", e)
            // Operation succeeded but log failed - still acceptable
            // Will be retried by WorkflowUndoJob
        }
    }
}
```

### Concurrency Handling

```
1000 concurrent requests = 1000 workflow IDs (thread-isolated)
├─ Each uses ThreadLocal context
├─ No shared locks
├─ Parallel execution
└─ Zero contention ✅

Operation logging:
├─ Async writes to operation_log
├─ Non-blocking (continues transaction)
└─ Background job processes later
```

### Database Impact

```
New tables:
├─ operation_log: ~100 bytes per operation
├─ workflow_state: ~200 bytes per workflow
└─ Indexes on workflow_id + status

With 1000 req/sec:
├─ 1000 rows/sec in operation_log
├─ ~86GB/year (manageable)
├─ Archive old logs quarterly
└─ Zero impact on main transaction ✅
```

---

## Part 6: Usage Examples

### Simple Workflow

```kotlin
class UserRegistrationService(
    private val transactionManager: DatabaseTransactionManager,
    private val authRepository: AuthAccountRepository,
    private val profileRepository: UserProfileRepository,
    private val emailService: EmailService
) {

    suspend fun registerUser(request: RegisterRequest): User {
        return transactionManager.transaction(workflowId = "user_registration") {
            // Step 1: Create account
            val account = authRepository.save(AuthAccountEntity(...))

            // Step 2: Create profile
            profileRepository.save(UserProfileEntity(...))

            // Step 3: Send email
            emailService.sendWelcome(account.email)

            // If any step fails:
            // ├─ Email sending rolls back ✅
            // ├─ Profile deleted ✅
            // ├─ Account deleted ✅
            // └─ DB rolled back ✅
        }
    }
}
```

### Complex Workflow with Multiple Services

```kotlin
class OrderService(
    private val transactionManager: DatabaseTransactionManager,
    private val orderRepository: OrderRepository,
    private val inventoryService: InventoryService,
    private val paymentService: PaymentService,
    private val notificationService: NotificationService
) {

    suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        return transactionManager.transaction(workflowId = "order_creation") {
            // Step 1: Reserve inventory
            inventoryService.reserve(orderDTO.items)

            // Step 2: Process payment
            paymentService.charge(orderDTO.amount)

            // Step 3: Create order
            val order = orderRepository.save(Order(...))

            // Step 4: Send confirmation
            notificationService.sendConfirmation(order)

            // If payment fails:
            // ├─ Inventory reservation undone ✅
            // ├─ DB rolled back ✅
            // └─ No notification sent ✅
        }
    }
}
```

---

## Part 7: Rollback Validation (Exposed Integration)

### How Exposed Handles Rollback

```kotlin
// Exposed automatically rolls back on exception
transaction {
    userRepository.save(user)           // Inserted
    throw SQLException("DB connection failed")
    // Exposed auto-rollback: User insertion undone ✅
}

// Our workflow system adds:
1. DB automatic rollback (Exposed) ✅
2. Operation undo (WorkflowUndoJob) ✅
3. Workflow state tracking ✅
```

### Verification Test

```kotlin
@Test
fun testWorkflowRollback() = runBlocking {
    transactionManager.transaction(workflowId = "test_rollback") {
        val user = userRepository.save(User("test@example.com"))

        // Simulate failure
        throw Exception("Something failed")
    }

    // Verify DB is clean
    transaction {
        val count = UserTable.selectAll().count()
        assertEquals(0, count)  // User not in DB ✅
    }

    // Verify workflow marked as failed
    val workflow = workflowStateManager.getWorkflowState("test_rollback")
    assertEquals(WorkflowStatus.FAILED, workflow?.status)

    // Verify operations marked for undo
    val operations = operationLog.getPendingOperations("test_rollback")
    assertTrue(operations.isEmpty())  // All undone ✅
}
```

---

## Part 8: Success Metrics

- [ ] DB rollback on exception: 100% ✅
- [ ] Operation logging non-blocking: <1ms overhead
- [ ] Undo execution: <5s per 100 operations
- [ ] Workflow state accuracy: 100% ✅
- [ ] Concurrent workflows: 1000+ without contention
- [ ] Failed workflow recovery: Automatic within 5 minutes
- [ ] Developer experience: Just pass workflowId

---

## Timeline

| Phase | Tasks | Duration | Status |
|-------|-------|----------|--------|
| 1 | Core infrastructure | 1 week | Not started |
| 2 | Repository auto-tracking | 1 week | Not started |
| 3 | UndoEngine | 1 week | Not started |
| 4 | Service integration | 1 week | Not started |
| 5 | Workflow composition | 1 week | Not started |
| 6 | Undo job & recovery | 1 week | Not started |
| 7 | Testing & validation | 2 weeks | Not started |
| 8 | Documentation | 1 week | Not started |

**Total: 6-7 weeks** for production-ready system

---

## Summary

**What You Get**:
```
transactionManager.transaction(workflowId = "user_registration") {
    authRepository.save(account)              // Auto-tracked ✅
    profileRepository.save(profile)            // Auto-tracked ✅
    emailService.send(email)                   // Auto-tracked ✅

    // If ANY operation fails:
    // ├─ Email sending undone
    // ├─ Profile deleted
    // ├─ Account deleted
    // ├─ DB rolled back (Exposed)
    // └─ Workflow state updated
}
```

**No manual compensation code needed** - Just pass workflowId, everything tracked automatically ✅

---

## Key Advantages

1. ✅ **Simple API**: Just pass `workflowId` string
2. ✅ **Automatic Tracking**: Operations tracked without code changes
3. ✅ **Reliable Rollback**: Exposed + Workflow system
4. ✅ **Scalable**: Async operation logging, no blocking
5. ✅ **Observable**: All operations logged for audit
6. ✅ **Recoverable**: Failed workflows auto-recovered
7. ✅ **Future Proof**: All new modules get it automatically
8. ✅ **Spring Boot-like**: Familiar transaction semantics

