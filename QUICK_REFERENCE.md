# Katalyst - Quick Reference Guide

## Three Key Answers

### 1. TrackerRepository Location & Purpose

**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/TrackerRepository.kt`

**Purpose:** Base class for repositories that automatically track all database operations within a workflow context

**Related Classes:**
- `CrudRepository` - Generic CRUD interface (same directory)
- `OperationLog` - Stores tracked operations
- `CurrentWorkflowContext` - Thread-local workflow ID holder

**Key Method:**
```kotlin
protected suspend fun <R> tracked(
    operation: String,      // "INSERT", "UPDATE", "DELETE"
    resourceType: String,   // "User", "Order", etc.
    resourceId: Id? = null, // Entity ID being modified
    action: suspend () -> R // The actual operation
): R
```

**Usage:**
```kotlin
class UserRepository(operationLog: OperationLog) : TrackerRepository<Long, User>(operationLog) {
    override suspend fun save(entity: User) = tracked(
        operation = "INSERT",
        resourceType = "User",
        resourceId = entity.id,
        action = { super.save(entity) }
    )
}
```

---

### 2. Transaction Management Code Location

**Primary File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/manager/DatabaseTransactionManager.kt`

**Module:** `katalyst-transactions`

**Related Classes:**
- `TransactionManager` - Interface (same directory as DatabaseTransactionManager)
- `TransactionAdapter` - Extension point for hooks
- `TransactionPhase` - Lifecycle phases
- `CurrentWorkflowContext` - Workflow tracking
- `OperationLog` - Operation recording

**Key Classes Overview:**

| Class | File | Purpose |
|-------|------|---------|
| `DatabaseTransactionManager` | `transactions/manager/DatabaseTransactionManager.kt` | Main transaction executor with adapter support |
| `TransactionManager` | `transactions/manager/TransactionManager.kt` | Interface definition |
| `TransactionAdapter` | `transactions/adapter/TransactionAdapter.kt` | Cross-cutting concerns hook |
| `TransactionAdapterRegistry` | `transactions/adapter/TransactionAdapterRegistry.kt` | Manages adapters |
| `TransactionPhase` | `transactions/hooks/TransactionPhase.kt` | Lifecycle phases enum |
| `CurrentWorkflowContext` | `transactions/workflow/CurrentWorkflowContext.kt` | Thread-local workflow ID |
| `WorkflowStateManager` | `transactions/workflow/WorkflowStateManager.kt` | State machine |
| `PersistenceTransactionAdapter` | `persistence/database/adapter/PersistenceTransactionAdapter.kt` | DB-specific adapter |

**Transaction Flow:**
```
BEFORE_BEGIN → AFTER_BEGIN → [User Block] → BEFORE_COMMIT → Commit → AFTER_COMMIT
                                                    ↓ (on error)
                                            ON_ROLLBACK → AFTER_ROLLBACK
```

---

### 3. Logging Operations Code Location

**Module:** `katalyst-di` (lifecycle subpackage)

**Files:**

#### a) BootstrapProgressLogger
- **File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/BootstrapProgressLogger.kt`
- **Purpose:** Tracks and displays real-time bootstrap phase progress
- **7 Phases Tracked:**
  1. Koin DI Bootstrap
  2. Scheduler Method Discovery
  3. Component Discovery
  4. Database Schema Initialization
  5. Transaction Adapter Registration
  6. Application Initialization Hooks
  7. Ktor Engine Startup
- **Icons:** ⏳ (running), ✓ (complete), ✗ (failed), ⊘ (skipped), ○ (pending)

#### b) DiscoverySummaryLogger
- **File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/DiscoverySummaryLogger.kt`
- **Purpose:** Displays consolidated summary of discovered components
- **Displays:**
  - Repositories (count + list)
  - Services (count + list)
  - Components (count + list)
  - Validators (count + list)
  - Database Tables (count + list)
  - Ktor Modules (count + list)

#### c) Other Logging Classes
- `StartupWarningsAggregator` - Collects warnings during startup
- `StartupValidator` - Validates startup requirements
- `InitializerRegistry` - Manages custom initializers

**Global Accessors:**
```kotlin
BootstrapProgress.startPhase(1)
BootstrapProgress.completePhase(1, "Optional message")
BootstrapProgress.displayProgressSummary()

DiscoverySummary.addRepository("UserRepository")
DiscoverySummary.addService("UserService")
DiscoverySummary.display()
```

---

## Main Modules Overview

| Module | Purpose | Key Files |
|--------|---------|-----------|
| `katalyst-core` | Base interfaces | Component, Service, Validator, Table |
| `katalyst-di` | DI setup & lifecycle | KatalystApplication, BootstrapProgress, DiscoverySummary |
| `katalyst-persistence` | Data access layer | CrudRepository, TrackerRepository, undo strategies |
| `katalyst-transactions` | Transaction management | DatabaseTransactionManager, TransactionAdapter, workflow |
| `katalyst-scanner` | Component discovery | TypeDiscovery, KotlinMethodScanner |
| `katalyst-events` | Event definitions | DomainEvent, EventHandler |
| `katalyst-events-bus` | Event bus | ApplicationEventBus, EventHandlerRegistry |
| `katalyst-scheduler` | Scheduled jobs | SchedulerService, CronExpression |
| `katalyst-migrations` | Database versioning | MigrationRunner |
| `katalyst-ktor` | HTTP integration | KtorModule, middleware |
| `katalyst-example` | Reference app | Shows repositories, services, events |

---

## File Structure Quick Map

```
katalyst/
├── katalyst-core/
│   └── component/, exception/, persistence/, transaction/, validation/
├── katalyst-di/
│   ├── config/               (DIConfiguration, ServerConfiguration)
│   ├── internal/             (AutoBindingRegistrar, registries)
│   ├── lifecycle/            (BootstrapProgressLogger, DiscoverySummaryLogger)
│   └── module/               (Koin modules)
├── katalyst-persistence/
│   ├── config/               (DatabaseConfig)
│   ├── database/             (DatabaseFactory, adapter, tables)
│   └── repositories/         (CrudRepository, TrackerRepository, undo/)
├── katalyst-transactions/
│   ├── manager/              (DatabaseTransactionManager)
│   ├── adapter/              (TransactionAdapter, registry)
│   ├── context/
│   ├── hooks/                (TransactionPhase)
│   └── workflow/             (state mgmt, undo, recovery)
├── katalyst-scanner/
│   ├── core/                 (TypeDiscovery, metadata)
│   ├── integration/          (AutoDiscoveryEngine)
│   └── scanner/              (type/method scanners)
├── katalyst-events/
│   ├── config/, exception/, validation/
│   └── base: DomainEvent, EventHandler
├── katalyst-events-bus/
│   ├── ApplicationEventBus, TransactionAwareEventBus
│   ├── EventHandlerRegistry
│   ├── adapter/              (EventsTransactionAdapter)
│   └── exception/
├── katalyst-scheduler/
│   ├── cron/                 (CronExpression, validator)
│   ├── lifecycle/            (SchedulerInitializer)
│   └── service/              (SchedulerService)
├── katalyst-ktor/
│   ├── builder/, extension/, middleware/
│   └── base: KtorModule
├── katalyst-example/
│   ├── api/                  (DTOs, handlers)
│   ├── config/               (middleware)
│   ├── domain/               (models, events, validators)
│   ├── infra/                (DB config, repositories, tables)
│   └── routes/               (HTTP endpoints)
└── [other modules]
```

---

## Common Patterns

### 1. Creating a Tracked Repository
```kotlin
class UserRepository(operationLog: OperationLog) : TrackerRepository<Long, User>(operationLog) {
    override val table = UsersTable
    override fun map(row: ResultRow) = /* ... */
    
    override suspend fun save(entity: User) = tracked(
        operation = "INSERT",
        resourceType = "User",
        resourceId = entity.id,
        action = { super.save(entity) }
    )
}
```

### 2. Using Transactions with Workflow Tracking
```kotlin
val result = transactionManager.transaction(workflowId = UUID.randomUUID().toString()) {
    val user = userRepository.save(newUser)
    eventBus.publish(UserCreatedEvent(user.id))
    user
}
```

### 3. Adding a Transaction Adapter
```kotlin
class MyTransactionAdapter : TransactionAdapter {
    override fun getPhase() = TransactionPhase.AFTER_COMMIT
    override suspend fun execute(context: TransactionEventContext) {
        // Do something after commit
    }
}

transactionManager.addAdapter(MyTransactionAdapter())
```

### 4. Bootstrap Logging
```kotlin
BootstrapProgress.startPhase(3)
// discover components...
DiscoverySummary.addRepository("UserRepository")
BootstrapProgress.completePhase(3, "Found 5 repositories")
```

---

## Key Interfaces & Classes at a Glance

### Transaction Management
- `TransactionManager` - Defines `transaction()` method
- `DatabaseTransactionManager` - Actual implementation
- `TransactionAdapter` - Hook extension point
- `TransactionPhase` - Lifecycle phase enum

### Repositories & Data Access
- `CrudRepository<Id, T>` - Generic CRUD interface
- `TrackerRepository<Id, T>` - Base class with auto-tracking
- `Identifiable<Id>` - Interface for entities with ID
- `OperationLog` - Records all operations

### Events
- `DomainEvent` - Base event interface
- `EventHandler<E : DomainEvent>` - Handler interface
- `EventBus` - Publishing interface
- `TransactionAwareEventBus` - Queues events until commit

### DI & Lifecycle
- `KatalystApplication` - Main entry point DSL
- `Component` - Discoverable component interface
- `Service` - Service base interface
- `KatalystFeature` - Optional feature interface

### Logging
- `BootstrapProgressLogger` - Real-time phase tracking
- `DiscoverySummaryLogger` - Component discovery summary
- `StartupWarningsAggregator` - Warning collection
- `BootstrapProgress` (singleton) - Global accessor
- `DiscoverySummary` (singleton) - Global accessor

---

## File Locations Quick Reference

| What | Location |
|------|----------|
| TrackerRepository | `katalyst-persistence/repositories/TrackerRepository.kt` |
| CrudRepository | `katalyst-persistence/repositories/CrudRepository.kt` |
| DatabaseTransactionManager | `katalyst-transactions/manager/DatabaseTransactionManager.kt` |
| TransactionAdapter | `katalyst-transactions/adapter/TransactionAdapter.kt` |
| BootstrapProgressLogger | `katalyst-di/lifecycle/BootstrapProgressLogger.kt` |
| DiscoverySummaryLogger | `katalyst-di/lifecycle/DiscoverySummaryLogger.kt` |
| AutoBindingRegistrar | `katalyst-di/internal/AutoBindingRegistrar.kt` |
| KatalystApplication | `katalyst-di/KatalystApplication.kt` |
| ApplicationEventBus | `katalyst-events-bus/ApplicationEventBus.kt` |
| SchedulerService | `katalyst-scheduler/service/SchedulerService.kt` |

