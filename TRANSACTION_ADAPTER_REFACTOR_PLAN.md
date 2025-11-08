# Transaction Adapter Refactoring Plan

## Overview

Refactor the transaction system from tightly-coupled event publishing to a **decoupled adapter-based architecture**, similar to the feature mechanism in DI core. Each module (persistence, events, etc.) registers its own `TransactionAdapter` to handle lifecycle concerns independently.

---

## Architecture Goals

✅ **Decoupling**: Modules manage their own transaction concerns
✅ **Extensibility**: New modules can add adapters without modifying core
✅ **Single Responsibility**: Each adapter handles one concern
✅ **No Circular Dependencies**: Events module doesn't need to import transactions
✅ **Reusability**: Adapters can be composed and tested independently

---

## Phase 1: Design TransactionAdapter Interface

**File**: `katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/adapter/TransactionAdapter.kt`

```kotlin
package com.ead.katalyst.transactions.adapter

import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase

/**
 * Adapter for module-specific transaction lifecycle handling.
 *
 * Modules register adapters to handle their concerns during transaction phases:
 * - Persistence module: manages database-specific logic
 * - Events module: manages event publishing
 * - Future modules: add their own adapters
 *
 * Each adapter is independent and doesn't know about other modules.
 */
interface TransactionAdapter {
    /**
     * Module name for identification and logging
     */
    fun name(): String

    /**
     * Execute adapter logic for a specific transaction phase.
     *
     * Adapters execute in priority order. Exceptions are logged but don't fail the transaction.
     *
     * @param phase The transaction phase
     * @param context The transaction context with event queue and metadata
     */
    suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext)

    /**
     * Execution priority (higher = earlier execution)
     *
     * Default: 0 (execute last)
     * Common: Persistence=10, Events=5, Logging=0
     */
    fun priority(): Int = 0
}
```

---

## Phase 2: Create TransactionAdapterRegistry

**File**: `katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/adapter/TransactionAdapterRegistry.kt`

```kotlin
package com.ead.katalyst.transactions.adapter

import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry for transaction adapters.
 *
 * Adapters are sorted by priority and executed in order during transaction phases.
 */
class TransactionAdapterRegistry {
    private val adapters = CopyOnWriteArrayList<TransactionAdapter>()
    private val logger = LoggerFactory.getLogger(TransactionAdapterRegistry::class.java)

    /**
     * Register a transaction adapter.
     *
     * @param adapter The adapter to register
     */
    fun register(adapter: TransactionAdapter) {
        adapters.add(adapter)
        // Re-sort by priority (higher first)
        adapters.sortByDescending { it.priority() }
        logger.info("Registered transaction adapter: {} (priority: {})", adapter.name(), adapter.priority())
    }

    /**
     * Remove an adapter (mainly for testing/cleanup).
     *
     * @param adapter The adapter to remove
     */
    fun unregister(adapter: TransactionAdapter) {
        adapters.remove(adapter)
        logger.debug("Unregistered transaction adapter: {}", adapter.name())
    }

    /**
     * Call all adapters for a specific phase.
     *
     * Executes adapters in priority order. Exceptions are logged but don't fail the transaction.
     *
     * @param phase The transaction phase
     * @param context The transaction context
     */
    suspend fun executeAdapters(phase: TransactionPhase, context: TransactionEventContext) {
        if (adapters.isEmpty()) {
            logger.debug("No adapters registered for phase: {}", phase)
            return
        }

        logger.debug("Executing {} adapter(s) for phase: {}", adapters.size, phase)

        for (adapter in adapters) {
            try {
                adapter.onPhase(phase, context)
            } catch (e: Exception) {
                logger.error(
                    "Error in transaction adapter {} during {}: {}",
                    adapter.name(),
                    phase,
                    e.message,
                    e
                )
                // Continue executing other adapters even if one fails
            }
        }
    }

    /**
     * Get all registered adapters (for testing).
     */
    fun getAdapters(): List<TransactionAdapter> = adapters.toList()

    /**
     * Clear all adapters (for testing/cleanup).
     */
    fun clear() {
        adapters.clear()
    }
}
```

---

## Phase 3: Refactor DatabaseTransactionManager

**Changes to**: `katalyst-transactions/.../DatabaseTransactionManager.kt`

Replace hook registry with adapter registry:

```kotlin
class DatabaseTransactionManager(
    private val database: Database,
    private val adapterRegistry: TransactionAdapterRegistry = TransactionAdapterRegistry()
) : TransactionManager {

    override suspend fun <T> transaction(block: suspend Transaction.() -> T): T {
        val transactionEventContext = TransactionEventContext()

        return try {
            // Phase 1: BEFORE_BEGIN
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_BEGIN, transactionEventContext)

            // Phase 2-5: Execute transaction with context
            val result = withContext(transactionEventContext) {
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_BEGIN, transactionEventContext)

                newSuspendedTransaction(null, database) {
                    logger.debug("Transaction context established, executing block")
                    block()
                }
            }

            // Phase 6: BEFORE_COMMIT
            adapterRegistry.executeAdapters(TransactionPhase.BEFORE_COMMIT, transactionEventContext)

            // Phase 7: AFTER_COMMIT
            adapterRegistry.executeAdapters(TransactionPhase.AFTER_COMMIT, transactionEventContext)

            result
        } catch (e: Exception) {
            logger.error("Transaction failed, rolling back", e)

            // Phase 8: ON_ROLLBACK
            try {
                adapterRegistry.executeAdapters(TransactionPhase.ON_ROLLBACK, transactionEventContext)
            } catch (hookError: Exception) {
                logger.warn("Error in ON_ROLLBACK adapter", hookError)
            }

            // Phase 9: AFTER_ROLLBACK
            try {
                adapterRegistry.executeAdapters(TransactionPhase.AFTER_ROLLBACK, transactionEventContext)
            } catch (hookError: Exception) {
                logger.warn("Error in AFTER_ROLLBACK adapter", hookError)
            }

            throw e
        }
    }

    override fun addAdapter(adapter: TransactionAdapter) {
        adapterRegistry.register(adapter)
    }
}
```

---

## Phase 4: Create PersistenceTransactionAdapter

**File**: `katalyst-persistence/src/main/kotlin/com/ead/katalyst/database/adapter/PersistenceTransactionAdapter.kt`

```kotlin
package com.ead.katalyst.database.adapter

import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory

/**
 * Transaction adapter for persistence concerns.
 *
 * Handles database-specific transaction lifecycle:
 * - Connection validation
 * - Schema initialization
 * - Transaction state logging
 */
class PersistenceTransactionAdapter : TransactionAdapter {
    private val logger = LoggerFactory.getLogger(PersistenceTransactionAdapter::class.java)

    override fun name(): String = "Persistence"

    override fun priority(): Int = 10  // High priority - run early

    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.BEFORE_BEGIN -> {
                logger.debug("Preparing database connection")
                // Initialize connection pool, validation, etc.
            }
            TransactionPhase.AFTER_BEGIN -> {
                logger.debug("Transaction started, validating connection")
                // Log transaction ID, validate isolation level
            }
            TransactionPhase.BEFORE_COMMIT -> {
                logger.debug("Preparing to commit transaction")
                // Final validation, flush pending writes
            }
            TransactionPhase.AFTER_COMMIT -> {
                logger.debug("Transaction committed successfully")
                // Connection cleanup, state reset
            }
            TransactionPhase.ON_ROLLBACK -> {
                logger.debug("Rolling back transaction")
                // Clear pending writes, reset state
            }
            TransactionPhase.AFTER_ROLLBACK -> {
                logger.debug("Transaction rolled back, cleaning up connection")
                // Final cleanup
            }
        }
    }
}
```

---

## Phase 5: Create EventsTransactionAdapter

**File**: `katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus/adapter/EventsTransactionAdapter.kt`

```kotlin
package com.ead.katalyst.events.bus.adapter

import com.ead.katalyst.transactions.adapter.TransactionAdapter
import com.ead.katalyst.transactions.context.TransactionEventContext
import com.ead.katalyst.transactions.hooks.TransactionPhase
import org.slf4j.LoggerFactory

/**
 * Transaction adapter for event publishing concerns.
 *
 * Handles event-specific transaction lifecycle:
 * - Publishing queued events after commit
 * - Discarding events on rollback
 * - Event bus cleanup
 */
class EventsTransactionAdapter(
    private val eventBus: ApplicationEventBus
) : TransactionAdapter {
    private val logger = LoggerFactory.getLogger(EventsTransactionAdapter::class.java)

    override fun name(): String = "Events"

    override fun priority(): Int = 5  // Medium priority

    override suspend fun onPhase(phase: TransactionPhase, context: TransactionEventContext) {
        when (phase) {
            TransactionPhase.AFTER_COMMIT -> {
                publishPendingEvents(eventBus, context)
            }
            TransactionPhase.ON_ROLLBACK -> {
                discardPendingEvents(context)
            }
            else -> {
                // Other phases don't require event handling
            }
        }
    }

    private suspend fun publishPendingEvents(
        eventBus: ApplicationEventBus,
        context: TransactionEventContext
    ) {
        val pendingEvents = context.getPendingEvents()
        if (pendingEvents.isEmpty()) {
            logger.debug("No pending events to publish")
            return
        }

        logger.debug("Publishing {} pending events after transaction commit", pendingEvents.size)
        for (event in pendingEvents) {
            try {
                eventBus.publish(event)
            } catch (e: Exception) {
                logger.error("Failed to publish event after commit: {}", e.message, e)
            }
        }
        context.clearPendingEvents()
    }

    private fun discardPendingEvents(context: TransactionEventContext) {
        if (context.hasPendingEvents()) {
            logger.debug(
                "Discarding {} pending events due to rollback",
                context.getPendingEventCount()
            )
            context.clearPendingEvents()
        }
    }
}
```

---

## Phase 6: Update DIConfiguration

**Changes to**: `katalyst-di/.../DIConfiguration.kt`

Register adapters during DI bootstrap:

```kotlin
fun bootstrapKatalystDI(
    databaseConfig: DatabaseConfig,
    scanPackages: Array<String> = emptyArray(),
    features: List<KatalystFeature> = emptyList()
): org.koin.core.Koin {
    // ... existing code ...

    // Register default adapters
    try {
        val transactionManager = koin.get<DatabaseTransactionManager>()
        val adapterRegistry = TransactionAdapterRegistry()

        // Register persistence adapter (always available)
        adapterRegistry.register(PersistenceTransactionAdapter())

        // Register events adapter if event bus is available
        try {
            val eventBus = koin.get<ApplicationEventBus>()
            adapterRegistry.register(EventsTransactionAdapter(eventBus))
            logger.info("Registered Events transaction adapter")
        } catch (e: Exception) {
            logger.debug("Event bus not available, skipping Events adapter")
        }

        logger.info("Registered {} transaction adapter(s)", adapterRegistry.getAdapters().size)
    } catch (e: Exception) {
        logger.warn("Error registering transaction adapters: {}", e.message)
    }

    return koin
}
```

---

## Phase 7: Migration Steps

### Step 1: Update DatabaseTransactionManager
- Remove old hook registry
- Add adapter registry
- Update transaction() method to use executeAdapters()

### Step 2: Create Adapter Interfaces
- Move TransactionAdapter to transactions module
- Create TransactionAdapterRegistry

### Step 3: Create Module-Specific Adapters
- PersistenceTransactionAdapter in persistence module
- EventsTransactionAdapter in events-bus module

### Step 4: Update DI Configuration
- Register adapters during bootstrap
- Remove old event publishing callback logic

### Step 5: Remove Old Code
- Delete old publishPendingEvents() function
- Delete old DatabaseModule event callback logic
- Update DatabaseTransactionManager signature

### Step 6: Test Integration
- Verify event publishing still works
- Verify transactions still work
- Verify adapters execute in correct order

---

## Phase 8: Benefits of This Approach

✅ **Decoupling**
- Events module doesn't import transactions module
- Persistence module doesn't know about events
- Each module is independent

✅ **Extensibility**
- New modules can add adapters without modifying core
- Scheduling module can add SchedulingTransactionAdapter
- Caching module can add CachingTransactionAdapter

✅ **Testing**
- Each adapter can be unit tested independently
- Mock adapters for testing transaction manager
- No need for complex dependency mocking

✅ **Maintainability**
- Clear separation of concerns
- Easy to understand each module's transaction responsibility
- Adapter pattern is familiar to developers

✅ **No Circular Dependencies**
- Events bus doesn't depend on transactions (via callback)
- Transactions can exist independently
- Modules register adapters explicitly

---

## Dependency Graph (After Refactoring)

```
katalyst-transactions
└── Defines TransactionAdapter interface
    └── Each module implements adapters independently

katalyst-persistence
├── Implements PersistenceTransactionAdapter
└── Registers during DI bootstrap

katalyst-events-bus
├── Implements EventsTransactionAdapter
└── Registers during DI bootstrap

katalyst-di
└── Orchestrates adapter registration

[Future Modules]
├── katalyst-caching → CachingTransactionAdapter
├── katalyst-scheduler → SchedulingTransactionAdapter
└── katalyst-audit → AuditTransactionAdapter
```

---

## Implementation Order

1. ✅ **Phase 1-2**: Create TransactionAdapter interface and registry
2. ⬜ **Phase 3**: Refactor DatabaseTransactionManager
3. ⬜ **Phase 4**: Create PersistenceTransactionAdapter
4. ⬜ **Phase 5**: Create EventsTransactionAdapter
5. ⬜ **Phase 6**: Update DIConfiguration
6. ⬜ **Phase 7**: Remove old code and migrate
7. ⬜ **Phase 8**: Test integration end-to-end

---

## Success Metrics

✅ Events are published correctly after transaction commit
✅ Events are discarded on transaction rollback
✅ No circular dependencies between modules
✅ Each adapter executes in correct priority order
✅ Example application works unchanged
✅ New modules can add adapters without framework changes
✅ All existing tests pass

---

## Timeline

- **Phase 1-2**: 30 minutes (design interfaces)
- **Phase 3**: 30 minutes (refactor manager)
- **Phase 4-5**: 45 minutes (create adapters)
- **Phase 6**: 30 minutes (DI integration)
- **Phase 7-8**: 60 minutes (testing and cleanup)

**Total**: ~3 hours for complete refactoring

