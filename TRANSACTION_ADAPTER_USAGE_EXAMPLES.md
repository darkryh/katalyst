# Transaction Adapter - Usage Examples

## Scenario 1: Two Sequential Transactions ✅ (Separate Contexts)

```kotlin
class UserOrderService(
    private val transactionManager: DatabaseTransactionManager,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val eventBus: EventBus
) {
    suspend fun createUserWithOrder(userDto: CreateUserDTO, orderDto: CreateOrderDTO) {
        // Transaction 1: Create user
        transactionManager.transaction {
            val user = userRepository.save(User.from(userDto))
            // Event queued in Context1
            eventBus.publish(UserCreatedEvent(user.id))
        }
        // After Tx1 commits: UserCreatedEvent PUBLISHED

        // Transaction 2: Create order
        transactionManager.transaction {
            val order = orderRepository.save(Order.from(orderDto))
            // Event queued in Context2 (NEW SEPARATE CONTEXT)
            eventBus.publish(OrderCreatedEvent(order.id))
        }
        // After Tx2 commits: OrderCreatedEvent PUBLISHED
    }
}
```

**Behavior**:
- Each `transaction {}` call creates a **NEW TransactionEventContext**
- Events are completely isolated between transactions
- Each transaction has its own commit/rollback boundary
- Events publish independently

**Timeline**:
```
Transaction 1 starts
├─ Create user in DB
├─ Queue UserCreatedEvent in Context1
├─ Commit Tx1
└─ Publish UserCreatedEvent ✅

Transaction 2 starts
├─ Create order in DB
├─ Queue OrderCreatedEvent in Context2 (different context)
├─ Commit Tx2
└─ Publish OrderCreatedEvent ✅
```

**Pros**:
- Clear transaction boundaries
- Events publish independently
- Easy to understand

**Cons**:
- If Tx2 fails, Tx1 already committed
- Not ideal if you need both to succeed together

---

## Scenario 2: One Combined Transaction ✅ (RECOMMENDED)

```kotlin
class UserOrderService(
    private val transactionManager: DatabaseTransactionManager,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val eventBus: EventBus
) {
    suspend fun createUserWithOrder(userDto: CreateUserDTO, orderDto: CreateOrderDTO) {
        // ONE TRANSACTION - BOTH OPERATIONS
        transactionManager.transaction {
            // Create user
            val user = userRepository.save(User.from(userDto))
            // Event queued in SAME Context
            eventBus.publish(UserCreatedEvent(user.id))

            // Create order
            val order = orderRepository.save(Order.from(orderDto))
            // Event queued in SAME Context
            eventBus.publish(OrderCreatedEvent(order.id))

            Pair(user, order)
        }
        // After Tx commits: BOTH events published in order ✅
        // If exception: BOTH rollback, NO events published ✅
    }
}
```

**Behavior**:
- Single TransactionEventContext for both operations
- All events queued in same context
- All-or-nothing transaction semantics
- Events publish together after commit

**Timeline**:
```
Transaction starts
├─ Create user in DB
├─ Queue UserCreatedEvent in Context
├─ Create order in DB
├─ Queue OrderCreatedEvent in Context
├─ Commit Tx
├─ Publish UserCreatedEvent ✅
└─ Publish OrderCreatedEvent ✅

If exception at any point:
├─ Rollback everything
├─ Discard BOTH events ✅
└─ Propagate exception
```

**Pros** ✅:
- **Transactional integrity**: Both succeed or both fail
- **Event consistency**: Events publish together
- **No orphaned data**: Can't have user without order
- **Atomic from caller perspective**: One operation
- **RECOMMENDED APPROACH** for related operations

**Cons**:
- Longer transaction duration
- More lock contention if many operations
- All operations tightly coupled

---

## Scenario 3: Nested Transactions ⚠️ (Works, but subtle behavior)

```kotlin
class PaymentService(
    private val transactionManager: DatabaseTransactionManager,
    private val paymentRepository: PaymentRepository,
    private val accountRepository: AccountRepository,
    private val eventBus: EventBus
) {
    suspend fun processPayment(dto: PaymentDTO) {
        // Outer transaction
        transactionManager.transaction {
            val payment = paymentRepository.save(Payment.from(dto))
            eventBus.publish(PaymentInitiatedEvent(payment.id))  // Queued in Outer Context

            // Inner transaction (uses Exposed's nested transaction)
            transactionManager.transaction {
                val account = accountRepository.updateBalance(dto.accountId, -dto.amount)
                eventBus.publish(BalanceUpdatedEvent(account.id))  // Queued in Inner Context
            }
            // After inner commits: BalanceUpdatedEvent published ✅

            payment
        }
        // After outer commits: PaymentInitiatedEvent published ✅
    }
}
```

**Behavior** ⚠️:
- Inner `transaction {}` creates a NEW TransactionEventContext
- Inner context shadows the outer context (coroutine context replacement)
- Events from inner TX publish immediately when inner TX commits
- Outer TX events publish when outer TX commits
- Exposed handles nested TX savepoints automatically

**Timeline**:
```
Outer Transaction starts (Context_Outer)
├─ Create payment
├─ Queue PaymentInitiatedEvent in Context_Outer
├─ Inner Transaction starts (Context_Inner - replaces Context_Outer)
│  ├─ Update account balance
│  ├─ Queue BalanceUpdatedEvent in Context_Inner
│  ├─ Commit inner (savepoint release)
│  └─ Publish BalanceUpdatedEvent ✅ (published early!)
├─ Return to Outer Context
├─ Commit outer
└─ Publish PaymentInitiatedEvent ✅
```

**Important**: ⚠️
- Inner transaction events publish before outer transaction commits
- If outer transaction rolls back, inner events already published
- **This breaks transactional guarantees!**

---

## Scenario 4: Sequential Operations with Rollback Handling

```kotlin
class OrderService(
    private val transactionManager: DatabaseTransactionManager,
    private val orderRepository: OrderRepository,
    private val inventoryRepository: InventoryRepository,
    private val eventBus: EventBus
) {
    suspend fun createOrderWithInventoryCheck(orderDto: CreateOrderDTO): Order? {
        // Try to create order in Tx1
        val order = try {
            transactionManager.transaction {
                val order = orderRepository.save(Order.from(orderDto))
                eventBus.publish(OrderCreatedEvent(order.id))  // Queued in Context1
                order
            }
            // OrderCreatedEvent published here ✅
        } catch (e: Exception) {
            logger.error("Failed to create order", e)
            return null  // Order creation failed, no event published ✅
        }

        // If Tx1 succeeded, try inventory in Tx2
        return try {
            transactionManager.transaction {
                inventoryRepository.reserveStock(orderDto.items)
                eventBus.publish(InventoryReservedEvent(order.id))  // Queued in Context2
                order
            }
            // InventoryReservedEvent published here ✅
        } catch (e: Exception) {
            logger.error("Failed to reserve inventory", e)
            // Order already created and event already published! ⚠️
            // This is a problem: inconsistent state
            null
        }
    }
}
```

**Problem** ⚠️:
- If Tx1 succeeds but Tx2 fails
- Order is created and OrderCreatedEvent published
- But inventory reservation failed
- **Inconsistent state between events and data**

**Better approach**: Use combined transaction ✅

```kotlin
suspend fun createOrderWithInventoryCheck(orderDto: CreateOrderDTO): Order {
    return transactionManager.transaction {
        // Create order
        val order = orderRepository.save(Order.from(orderDto))

        // Check and reserve inventory IN SAME TRANSACTION
        inventoryRepository.reserveStock(orderDto.items)  // Throws if insufficient stock

        // Queue events only if everything succeeds
        eventBus.publish(OrderCreatedEvent(order.id))
        eventBus.publish(InventoryReservedEvent(order.id))

        order
    }
    // If any exception: BOTH rollback, NO events published
    // If all succeed: BOTH published
}
```

---

## Scenario 5: Multiple Repositories in One Transaction

```kotlin
class CompleteRegistrationService(
    private val transactionManager: DatabaseTransactionManager,
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val verificationRepository: VerificationRepository,
    private val eventBus: EventBus
) {
    suspend fun completeUserRegistration(registrationDto: CompleteRegistrationDTO): User {
        return transactionManager.transaction {
            // All operations in same transaction
            val user = userRepository.save(registrationDto.toUser())

            val profile = profileRepository.save(registrationDto.toProfile(user.id))

            val settings = settingsRepository.createDefaults(user.id)

            val verification = verificationRepository.generateToken(user.id)

            // All events queued in same context
            eventBus.publish(UserRegisteredEvent(user.id))
            eventBus.publish(UserProfileCreatedEvent(profile.id))
            eventBus.publish(UserSettingsInitializedEvent(settings.id))
            eventBus.publish(VerificationTokenGeneratedEvent(verification.id))

            user
        }
        // All operations committed atomically
        // All events published atomically
    }
}
```

**Behavior**:
- 4 DB operations in same transaction
- 4 events queued in same context
- All-or-nothing semantics
- All events publish together

---

## Best Practices Summary

### ✅ DO - Use One Transaction For Related Operations

```kotlin
transactionManager.transaction {
    // All related DB ops + event publishes
    // Single atomic unit
}
```

**When**: Related data changes, interdependent operations, must maintain consistency

---

### ✅ DO - Use Sequential Transactions For Independent Operations

```kotlin
transactionManager.transaction {
    // Operation 1
}
transactionManager.transaction {
    // Operation 2 - completely independent
}
```

**When**: Operations are truly independent, failure of one doesn't require rollback of other

---

### ⚠️ AVOID - Nested Transactions with Events

```kotlin
transactionManager.transaction {
    // outer context
    transactionManager.transaction {
        // inner context - breaks transactional guarantees
        // Events publish before outer commits
    }
}
```

**Why**: Inner events publish before outer transaction completes, breaks consistency

**Alternative**: Combine into single transaction

---

### ⚠️ AVOID - Sequential Transactions with Dependencies

```kotlin
// ❌ BAD: If Tx2 fails, Tx1 already published events
transactionManager.transaction {
    createUser()  // Events published
}
transactionManager.transaction {
    createOrder()  // May fail, but user already created
}

// ✅ GOOD: Both in one transaction
transactionManager.transaction {
    createUser()
    createOrder()
    // Events published only if both succeed
}
```

---

## Adapter Behavior Summary

| Scenario | Context | Behavior |
|----------|---------|----------|
| Single Tx | 1 Context | Events queued together, published together ✅ |
| Sequential Tx | Separate Contexts | Each publishes independently ✅ |
| Nested Tx | Inner replaces Outer | Inner publishes early ⚠️ |
| One Tx Fails | 1 Context | All rollback, no events ✅ |
| Seq Tx, Tx2 Fails | Separate Contexts | Tx1 events already published ⚠️ |

---

## Transaction Event Context Flow

```kotlin
// DatabaseTransactionManager.transaction()
override suspend fun <T> transaction(block: suspend Transaction.() -> T): T {
    // Create NEW context for this transaction
    val transactionEventContext = TransactionEventContext()

    return try {
        // All adapters see this context
        adapterRegistry.executeAdapters(TransactionPhase.BEFORE_BEGIN, transactionEventContext)

        val result = withContext(transactionEventContext) {
            // Block executes with this context
            // eventBus.publish() queues in this context
            newSuspendedTransaction(null, database) {
                block()
            }
        }

        // After commit: publish all queued events
        adapterRegistry.executeAdapters(TransactionPhase.AFTER_COMMIT, transactionEventContext)

        result
    } catch (e: Exception) {
        // On rollback: discard all queued events
        adapterRegistry.executeAdapters(TransactionPhase.ON_ROLLBACK, transactionEventContext)
        throw e
    }
}
```

---

## Recommendation

**For user registration + account operations**: Use **one combined transaction** ✅

```kotlin
suspend fun registerUserWithAccount(dto: RegistrationDTO): User {
    return transactionManager.transaction {
        // All operations in same atomic unit
        val user = userRepository.save(dto.toUser())
        val account = accountRepository.create(user.id)

        // Both events queued
        eventBus.publish(UserRegisteredEvent(user.id))
        eventBus.publish(AccountCreatedEvent(account.id))

        // All-or-nothing: if exception, both rollback, no events
        user
    }
}
```

This ensures **transactional integrity** - data and events stay consistent!
