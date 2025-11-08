# General Transaction Compensation & Rollback Pattern

## Problem: Transaction Reliability for ALL Operations

### Current State
```
Database operations in TX ✅
Post-TX operations (events, API, cache, etc) ❌ NOT in TX

If post-TX operation fails:
├─ DB: Committed (can't rollback)
├─ Operation: Failed
└─ State: INCONSISTENT
```

### Desired State (Like Spring Boot @Transactional)
```
Database operations in TX ✅
Post-TX operations in TX ✅
API calls in TX ✅
Notifications in TX ✅
Cache updates in TX ✅

If ANYTHING fails:
├─ DB: Rolled back ✅
├─ All operations: Rolled back ✅
└─ State: CONSISTENT ✅
```

---

## Solution: Transaction Compensation Pattern

**Concept**: Keep ALL operations within transaction scope. If any fails, run compensation (rollback) for previous operations.

### Architecture

```
Transaction Context
├─ DB Operations (atomic)
├─ Compensation Handlers (registered during operations)
├─ Event Publishing (within TX)
├─ API Calls (within TX)
├─ Cache Updates (within TX)
├─ Notifications (within TX)
└─ Custom Operations (within TX)

If ANY operation fails:
├─ Run compensation handlers in reverse order
├─ Rollback DB automatically
└─ Return to consistent state ✅
```

### How It Works

```
1. Start Transaction
2. Operation 1: Save user to DB
   └─ Register: DELETE from users WHERE id=123
3. Operation 2: Publish UserCreatedEvent
   └─ Register: Don't publish to handlers
4. Operation 3: Send welcome email
   └─ Register: Retry or mark failed (email can be async)
5. Operation 4: Update cache
   └─ Register: Invalidate cache
6. Operation 5: Call external API
   └─ Register: Undo API call

If Operation 5 fails:
├─ Run compensation for Op 4 (invalidate cache ✅)
├─ Run compensation for Op 3 (undo email ⚠️)
├─ Run compensation for Op 2 (undo event publishing ✅)
├─ Run compensation for Op 1 (undo user save ✅)
├─ Run DB rollback ✅
└─ Return error to caller
```

---

## New Component: TransactionCompensation

### Core Interface

```kotlin
// katalyst-transactions/context/TransactionCompensation.kt

/**
 * Represents a compensating action (rollback operation).
 * Executed in reverse order if transaction fails.
 */
interface CompensationHandler {
    /**
     * Compensation name for logging
     */
    fun name(): String

    /**
     * Execute compensation (rollback)
     * Exceptions are logged but don't stop other compensations
     */
    suspend fun compensate()
}

/**
 * Transaction context with compensation support
 */
interface CompensationContext {
    /**
     * Register a compensation handler
     * Handlers executed in reverse order (LIFO)
     */
    fun registerCompensation(handler: CompensationHandler)

    /**
     * Register inline compensation
     */
    fun registerCompensation(name: String, action: suspend () -> Unit)

    /**
     * Get all registered compensations
     */
    fun getCompensations(): List<CompensationHandler>

    /**
     * Clear all compensations
     */
    fun clearCompensations()
}

/**
 * Extended TransactionEventContext with compensation support
 */
class TransactionEventContext(
    private val compensations: MutableList<CompensationHandler> = mutableListOf()
) : CompensationContext {

    override fun registerCompensation(handler: CompensationHandler) {
        compensations.add(handler)
    }

    override fun registerCompensation(name: String, action: suspend () -> Unit) {
        registerCompensation(object : CompensationHandler {
            override fun name() = name
            override suspend fun compensate() = action()
        })
    }

    override fun getCompensations(): List<CompensationHandler> = compensations.toList()

    override fun clearCompensations() = compensations.clear()

    /**
     * Execute all compensations in reverse order
     */
    suspend fun executeCompensations() {
        logger.debug("Executing {} compensation(s)", compensations.size)
        // Execute in reverse order (LIFO)
        for (handler in compensations.reversed()) {
            try {
                logger.debug("Executing compensation: {}", handler.name())
                handler.compensate()
                logger.debug("Compensation succeeded: {}", handler.name())
            } catch (e: Exception) {
                logger.error("Compensation failed: {} - {}", handler.name(), e.message, e)
                // Continue with other compensations
            }
        }
        compensations.clear()
    }
}
```

### Updated DatabaseTransactionManager

```kotlin
// katalyst-transactions/manager/DatabaseTransactionManager.kt

class DatabaseTransactionManager(
    private val database: Database,
    private val adapterRegistry: TransactionAdapterRegistry = TransactionAdapterRegistry()
) : TransactionManager {

    override suspend fun <T> transaction(block: suspend Transaction.() -> T): T {
        logger.debug("Starting transaction with compensation support")

        val context = TransactionEventContext()

        return try {
            // Execute adapters
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_BEGIN, context)

            val result = withContext(context) {
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_BEGIN, context)

                // Execute block (operations register compensations)
                newSuspendedTransaction(null, database) {
                    try {
                        block()
                    } catch (e: Exception) {
                        // On block failure: Execute compensations BEFORE rollback
                        logger.error("Block failed, executing compensations", e)
                        context.executeCompensations()
                        throw e  // Then rollback DB automatically
                    }
                }
            }

            // Success: All operations succeeded
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_COMMIT, context)
            logger.debug("Transaction succeeded, no compensations needed")

            // Clear compensations - no longer needed
            context.clearCompensations()

            adapterRegistry.executeAdapters(TransactionPhase.AFTER_COMMIT, context)

            result
        } catch (e: Exception) {
            logger.error("Transaction failed", e)

            // Ensure compensations executed
            try {
                context.executeCompensations()
            } catch (compensationError: Exception) {
                logger.error("Error during compensation execution", compensationError)
            }

            adapterRegistry.executeAdapters(TransactionPhase.ON_ROLLBACK, context)
            adapterRegistry.executeAdapters(TransactionPhase.AFTER_ROLLBACK, context)

            throw e
        }
    }
}
```

---

## Usage Examples

### Example 1: User Registration with All Operations in TX

```kotlin
class UserRegistrationService(
    private val transactionManager: DatabaseTransactionManager,
    private val userRepository: UserRepository,
    private val cacheManager: CacheManager,
    private val emailService: EmailService,
    private val eventBus: EventBus,
    private val externalAPI: ExternalUserService
) : Service {

    suspend fun registerUser(request: RegisterRequest): User {
        return transactionManager.transaction {
            // Operation 1: Save user
            val user = userRepository.save(User.from(request))

            // Register compensation: Delete user if anything fails
            context.registerCompensation("DeleteUser") {
                userRepository.delete(user.id)
                logger.info("Compensation: User deleted")
            }

            // Operation 2: Publish event
            eventBus.publish(UserRegisteredEvent(user.id))

            // Register compensation: Don't publish
            context.registerCompensation("SkipEventPublishing") {
                // Events were queued but not published yet
                // Compensation: They will be discarded anyway
                logger.info("Compensation: Event publishing skipped")
            }

            // Operation 3: Invalidate cache
            cacheManager.invalidate("users:*")

            // Register compensation: Reload cache
            context.registerCompensation("ReloadCache") {
                cacheManager.reload("users")
                logger.info("Compensation: Cache reloaded")
            }

            // Operation 4: Send welcome email
            emailService.sendWelcomeEmail(user.email)

            // Register compensation: Mark as unsent
            context.registerCompensation("UnsendEmail") {
                emailService.markAsFailed(user.email)
                logger.info("Compensation: Email marked as unsent")
            }

            // Operation 5: Sync with external API
            externalAPI.createUser(user)

            // Register compensation: Delete from external API
            context.registerCompensation("DeleteFromExternalAPI") {
                externalAPI.deleteUser(user.id)
                logger.info("Compensation: User deleted from external API")
            }

            user
        }

        // All succeeded: All compensations cleared, no rollback
        // Any failed: All compensations executed in reverse order, everything rolled back
    }
}
```

### Example 2: Order Processing with Multiple Services

```kotlin
class OrderService(
    private val transactionManager: DatabaseTransactionManager,
    private val orderRepository: OrderRepository,
    private val inventoryService: InventoryService,
    private val paymentService: PaymentService,
    private val shippingService: ShippingService,
    private val eventBus: EventBus
) : Service {

    suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        return transactionManager.transaction {
            // Step 1: Reserve inventory
            inventoryService.reserve(orderDTO.items)

            context.registerCompensation("ReleaseInventory") {
                inventoryService.release(orderDTO.items)
                logger.info("Compensation: Inventory released")
            }

            // Step 2: Process payment
            val paymentResult = paymentService.charge(orderDTO.amount)

            context.registerCompensation("RefundPayment") {
                paymentService.refund(paymentResult.transactionId)
                logger.info("Compensation: Payment refunded")
            }

            // Step 3: Create order in DB
            val order = orderRepository.save(Order.from(orderDTO))

            context.registerCompensation("DeleteOrder") {
                orderRepository.delete(order.id)
                logger.info("Compensation: Order deleted")
            }

            // Step 4: Book shipping
            val shipmentId = shippingService.book(order)

            context.registerCompensation("CancelShipment") {
                shippingService.cancel(shipmentId)
                logger.info("Compensation: Shipment cancelled")
            }

            // Step 5: Publish event
            eventBus.publish(OrderCreatedEvent(order.id))

            order
        }

        // Success: Order completely processed ✅
        // Failure at any step: Everything compensated ✅
    }
}
```

---

## Migration Phases

### Phase 1: Core Infrastructure
- [ ] Enhance `TransactionEventContext` with compensation support
- [ ] Add `CompensationHandler` interface
- [ ] Update `DatabaseTransactionManager` to execute compensations on failure

### Phase 2: Integration with Adapters
- [ ] Update all adapters to be compensation-aware
- [ ] `EventsTransactionAdapter`: Register compensation to skip publishing
- [ ] `PersistenceTransactionAdapter`: Register compensation for DB rollback

### Phase 3: Service Layer Migration
- [ ] Start registering compensations in existing services
- [ ] UserRegistrationService
- [ ] AuthenticationService
- [ ] Other critical services

### Phase 4: Documentation & Testing
- [ ] Document compensation patterns
- [ ] Create test cases for compensation execution
- [ ] Test failure scenarios at each step

---

## Comparison: All Solutions

| Aspect | Current | Option A (Pre-TX) | Option B (Outbox) | **Option C (Compensation)** |
|--------|---------|----------|----------|---------|
| **Scope** | Events only | Events only | Events only | **All operations** |
| **Atomicity** | ❌ Partial | ✅ Full | ✅ Full | ✅ Full |
| **Rollback** | ❌ Manual | ✅ Auto | ✅ Auto | ✅ Auto |
| **API calls in TX** | ❌ No | ⚠️ Blocks | ✅ Yes | ✅ Yes |
| **Notifications in TX** | ❌ No | ⚠️ Blocks | ✅ Yes | ✅ Yes |
| **Cache in TX** | ❌ No | ⚠️ Blocks | ✅ Yes | ✅ Yes |
| **Complexity** | ✅ Low | ⚠️ Medium | ⚠️ High | ⚠️ Medium |
| **Scalability** | ⚠️ Medium | ❌ Low | ✅ High | ✅ High |
| **Future modules** | ❌ Manual setup | ❌ Manual setup | ❌ Manual setup | ✅ Automatic |
| **Spring Boot like** | ❌ No | ✅ Yes | ❌ No | ✅ Yes |

---

## Advantages of Compensation Pattern

### For Your Use Case

1. **General Purpose**: Works for ANY operation, not just events
2. **Reusable**: Every new module gets it automatically
3. **Developer Friendly**: Just register compensation as you go
4. **Familiar**: Like Spring Boot @Transactional semantics
5. **Reliable**: All operations atomic or fully rolled back
6. **No Event Loss**: Events still part of transaction
7. **API Safe**: External API calls included in TX
8. **Cache Safe**: Cache updates compensated
9. **Notification Safe**: Emails/SMS can be compensated

### Use Case Coverage

```
User Registration:
├─ Save user to DB
├─ Publish event
├─ Send email
├─ Update cache
├─ Sync with external API
└─ All fail together ✅

Order Processing:
├─ Reserve inventory
├─ Process payment
├─ Create order
├─ Book shipment
└─ All fail together ✅

Future Payment Module:
├─ Charge credit card
├─ Update wallet
├─ Log transaction
├─ Notify user
└─ All fail together ✅
```

---

## Code Pattern for New Modules

```kotlin
// Any new module in future gets this pattern automatically

class NewModuleService(
    private val transactionManager: DatabaseTransactionManager,
    private val repo: Repository,
    private val externalService: ExternalService
) : Service {

    suspend fun doSomething(dto: DTO) {
        return transactionManager.transaction {
            // Step 1: DB operation
            val entity = repo.save(Entity.from(dto))

            // Register undo
            context.registerCompensation("UndoDBOperation") {
                repo.delete(entity.id)
            }

            // Step 2: External service call
            externalService.sync(entity)

            // Register undo
            context.registerCompensation("UndoExternalSync") {
                externalService.delete(entity.id)
            }

            entity
        }
        // ✅ Everything either succeeds together or rolls back together
        // ✅ No special setup needed - just use the pattern
    }
}
```

---

## Implementation Strategy

### Short Term (This Sprint)
1. Add compensation support to `TransactionEventContext`
2. Update `DatabaseTransactionManager` to execute compensations
3. Update `EventsTransactionAdapter` to register compensation
4. Document pattern

### Medium Term (Next Sprint)
1. Migrate critical services to use compensation pattern
2. Add comprehensive tests for compensation scenarios
3. Add monitoring/logging for compensation execution
4. Create templates for new services

### Long Term (Roadmap)
1. Make compensation pattern default for all services
2. Build compensation framework (retry, circuit breaker, etc)
3. Add compensation history/audit
4. Support nested compensations

---

## Risk Mitigation

```kotlin
// All compensations wrapped in try-catch
suspend fun executeCompensations() {
    for (handler in compensations.reversed()) {
        try {
            handler.compensate()
        } catch (e: Exception) {
            // Log but continue
            logger.error("Compensation failed: {}", handler.name(), e)
            // Fail-safe: At least DB is rolled back
        }
    }
}
```

---

## Summary

**Before (Current Risk)**:
```
User saved ✅ → Email fails ❌ → Inconsistent ❌
```

**After (Compensation Pattern)**:
```
User saved ✅ → Register delete ✅ → Email fails ❌ → Delete user ✅ → Consistent ✅
```

This pattern provides **Spring Boot-like transaction reliability for ANY operation**, not just events. All new modules reuse it automatically.
