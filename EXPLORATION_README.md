# Katalyst Codebase Exploration - Documentation Index

This directory now contains comprehensive documentation of the Katalyst framework architecture.

## Quick Start

**Start here:** Read `QUICK_REFERENCE.md` first for answers to the three main questions:
1. Where is TrackerRepository and tracker-related classes?
2. Where is transaction management code?
3. Where is logging operations code?

## Documentation Files

### 1. QUICK_REFERENCE.md
- **Size:** 11 KB
- **Focus:** Direct answers to your 3 main questions
- **Content:**
  - File locations for key components
  - Usage patterns and code examples
  - Quick module overview table
  - Common design patterns
  - File lookup reference
- **Best for:** Quick lookups, getting oriented fast, finding specific files

### 2. CODEBASE_MAP.md
- **Size:** 36 KB, 829 lines
- **Focus:** Comprehensive architecture documentation
- **Content:**
  - Complete module-by-module breakdown
  - All 18 modules with detailed file structures
  - Component relationships and interactions
  - Transaction flow diagrams
  - Component discovery flow
  - Design patterns used throughout
  - Example implementations from katalyst-example
- **Best for:** Deep understanding, reference material, architecture review

## Quick Answers to Your Questions

### Question 1: TrackerRepository Location
**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/TrackerRepository.kt`

**What it is:** Base class for repositories that automatically track all database operations within a workflow context.

**Key capabilities:**
- Auto-logs operations (INSERT, UPDATE, DELETE) to workflow context
- Non-blocking async logging (fire-and-forget)
- Integrates with transaction management for operation tracking
- Extends CrudRepository for full CRUD support

**Related classes:**
- `CrudRepository` - Generic CRUD interface
- `OperationLog` - Stores tracked operations
- `CurrentWorkflowContext` - Thread-local workflow ID holder

### Question 2: Transaction Management
**Primary File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/manager/DatabaseTransactionManager.kt`

**Module:** `katalyst-transactions`

**What it does:**
- Wraps Exposed's `newSuspendedTransaction` for clean suspend function API
- Manages transaction lifecycle with 6 phases of hooks
- Tracks workflow ID in thread-local context for operation tracking
- Provides adapter system for cross-cutting concerns (persistence, events, etc.)
- Handles automatic commit/rollback with proper cleanup

**Transaction Phases:**
1. BEFORE_BEGIN - Setup before transaction starts
2. AFTER_BEGIN - After transaction starts, before user block
3. BEFORE_COMMIT - Pre-commit validation (still in transaction)
4. AFTER_COMMIT - Post-commit actions (outside transaction)
5. ON_ROLLBACK - During rollback execution
6. AFTER_ROLLBACK - After rollback completes

**Key related files:**
- `TransactionManager.kt` - Interface definition
- `TransactionAdapter.kt` - Extension point for hooks
- `TransactionPhase.kt` - Lifecycle phases enum
- `CurrentWorkflowContext.kt` - Workflow ID storage
- `PersistenceTransactionAdapter.kt` - Example DB adapter

### Question 3: Logging Operations
**Module:** `katalyst-di` (specifically `lifecycle` subpackage)

**Primary Files:**

#### a) BootstrapProgressLogger
**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/BootstrapProgressLogger.kt`

**Purpose:** Tracks and displays real-time bootstrap phase progress

**7 Phases Tracked:**
1. Koin DI Bootstrap (Component scanning)
2. Scheduler Method Discovery
3. Component Discovery (repositories, services, validators)
4. Database Schema Initialization
5. Transaction Adapter Registration
6. Application Initialization Hooks
7. Ktor Engine Startup

**Status Icons:**
- ⏳ Running
- ✓ Completed successfully
- ✗ Failed
- ⊘ Skipped
- ○ Pending

#### b) DiscoverySummaryLogger
**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/DiscoverySummaryLogger.kt`

**Purpose:** Displays consolidated summary of discovered components

**Shows Counts For:**
- Repositories
- Services
- Components
- Validators
- Database Tables
- Ktor Modules

**Related Classes:**
- `StartupWarningsAggregator` - Collects warnings during startup
- `StartupValidator` - Validates startup requirements
- `InitializerRegistry` - Manages custom initializers

## Module Overview

### Core Framework (5 modules)
1. **katalyst-core** - Base interfaces (Component, Service, Validator, Table)
2. **katalyst-di** - Koin DI setup, KatalystApplication, lifecycle logging
3. **katalyst-persistence** - Repositories, CrudRepository, TrackerRepository, undo strategies
4. **katalyst-transactions** - DatabaseTransactionManager, adapters, workflow management
5. **katalyst-scanner** - Type discovery, component scanning

### Event-Driven Architecture (4 modules)
1. **katalyst-events** - Event interfaces and validation
2. **katalyst-events-bus** - Event publishing and handler registry
3. **katalyst-events-transport** - Event message serialization
4. **katalyst-events-client** - Remote event publishing client

### Infrastructure (4 modules)
1. **katalyst-scheduler** - Scheduled job execution with cron expressions
2. **katalyst-migrations** - Database schema versioning
3. **katalyst-messaging** - Message queue abstractions
4. **katalyst-messaging-amqp** - AMQP/RabbitMQ specific implementation

### Web & Server (3 modules)
1. **katalyst-ktor** - HTTP framework integration
2. **katalyst-ktor-engine** - Embedded Ktor server
3. **katalyst-websockets** - WebSocket support

### Example Application (1 module)
1. **katalyst-example** - Reference application demonstrating all patterns

## Key Design Patterns Used

1. **Repository Pattern** - CrudRepository with TrackerRepository extension
2. **Adapter Pattern** - TransactionAdapter for lifecycle hooks
3. **Observer Pattern** - EventBus and TransactionAwareEventBus
4. **Builder Pattern** - KatalystApplication DSL
5. **Registry Pattern** - Component, adapter, and handler registries
6. **Strategy Pattern** - Undo strategies (Insert, Update, Delete, APICall)
7. **Template Method** - TransactionManager lifecycle hooks

## How to Navigate

### For quick answers:
1. Open `QUICK_REFERENCE.md`
2. Find your question in "Three Key Answers" section
3. Click on the file location to jump to the source

### For understanding architecture:
1. Start with `CODEBASE_MAP.md`
2. Read the "Core Architecture Layers" section
3. Navigate to specific modules of interest
4. Review "Key Component Relationships" for transaction and discovery flows

### For learning patterns:
1. Look at "Common Patterns" in `QUICK_REFERENCE.md`
2. Review the implementation in `katalyst-example`
3. Examine the corresponding source files

## Transaction Flow Example

```
Service
  └─ transactionManager.transaction { workflowId }
      ├─ Execute BEFORE_BEGIN adapters
      ├─ Begin Exposed transaction
      ├─ Execute AFTER_BEGIN adapters
      ├─ Execute user block
      │   └─ Repository.save()
      │       └─ TrackerRepository auto-logs operation
      ├─ Execute BEFORE_COMMIT adapters
      ├─ Commit database transaction
      ├─ Execute AFTER_COMMIT adapters
      │   └─ EventBus publishes queued events
      └─ Clear workflow context
```

## Component Discovery Flow

```
KatalystApplication { database(...), scanPackages(...) }
  ├─ Phase 1: Koin DI Bootstrap
  ├─ Phase 2: Scheduler Method Discovery
  ├─ Phase 3: Component Discovery
  │   ├─ AutoBindingRegistrar finds repositories
  │   ├─ AutoBindingRegistrar finds services
  │   └─ DiscoverySummary.display() shows results
  ├─ Phase 4: Database Schema Initialization
  ├─ Phase 5: Transaction Adapter Registration
  ├─ Phase 6: Application Initialization Hooks
  └─ Phase 7: Ktor Engine Startup
```

## File Structure Reference

### katalyst-persistence (Repositories)
```
katalyst-persistence/src/main/kotlin/com/ead/katalyst/
├── repositories/
│   ├── CrudRepository.kt          # Generic CRUD interface
│   ├── TrackerRepository.kt       # Auto-tracking base class
│   ├── Identifiable.kt            # Entity ID interface
│   └── undo/                      # Rollback strategies
│       ├── UndoStrategy.kt
│       ├── InsertUndoStrategy.kt
│       ├── UpdateUndoStrategy.kt
│       ├── DeleteUndoStrategy.kt
│       └── EnhancedUndoEngine.kt
└── database/
    ├── DatabaseFactory.kt
    ├── DatabaseModule.kt
    └── adapter/
        └── PersistenceTransactionAdapter.kt
```

### katalyst-transactions (Transaction Management)
```
katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/
├── manager/
│   ├── DatabaseTransactionManager.kt  # Main executor
│   └── TransactionManager.kt          # Interface
├── adapter/
│   ├── TransactionAdapter.kt
│   └── TransactionAdapterRegistry.kt
├── hooks/
│   └── TransactionPhase.kt
└── workflow/
    ├── CurrentWorkflowContext.kt
    ├── OperationLog.kt
    ├── WorkflowStateManager.kt
    └── WorkflowStateMachine.kt
```

### katalyst-di (DI & Logging)
```
katalyst-di/src/main/kotlin/com/ead/katalyst/di/
├── KatalystApplication.kt
├── config/
│   ├── DIConfiguration.kt
│   └── ServerConfiguration.kt
├── internal/
│   ├── AutoBindingRegistrar.kt
│   └── registries/
├── lifecycle/
│   ├── BootstrapProgressLogger.kt      # Real-time phase tracking
│   ├── DiscoverySummaryLogger.kt       # Component discovery summary
│   ├── StartupWarningsAggregator.kt
│   └── InitializerRegistry.kt
└── module/
    └── CoreDIModule.kt
```

## Statistics

- **Total Modules:** 18
- **Total Kotlin Files Analyzed:** 180+
- **Lines of Code:** 10,000+
- **Documentation Lines:** 1,500+
- **Design Patterns:** 7 major patterns

## Getting Started

1. **For Quick Answers:** Read `QUICK_REFERENCE.md` (5-10 minutes)
2. **For Overview:** Skim `CODEBASE_MAP.md` (15-20 minutes)
3. **For Details:** Deep dive into specific modules using file paths
4. **For Examples:** Review `katalyst-example` source code

## Key Files to Start With

| What You Want to Learn | Start Here |
|------------------------|-----------|
| How repositories track operations | TrackerRepository.kt + CrudRepository.kt |
| How transactions work | DatabaseTransactionManager.kt + TransactionPhase.kt |
| Bootstrap logging | BootstrapProgressLogger.kt + DiscoverySummaryLogger.kt |
| Full example | katalyst-example directory |
| Component discovery | AutoBindingRegistrar.kt + KatalystApplication.kt |
| Event publishing | ApplicationEventBus.kt + TransactionAwareEventBus.kt |

## Questions Answered

This documentation answers:
1. **Structure:** Where are TrackerRepository and tracker-related classes located?
2. **Transaction Mgmt:** Where is the transaction management code (DatabaseTransactionManager, etc)?
3. **Logging:** Where is logging operations code?
4. **Modules:** What are the main modules and their purposes?
5. **Components:** What are the key files in each module?

All with complete file paths and code examples.

---

**Last Updated:** November 9, 2025
**Total Time to Explore:** Comprehensive analysis of entire Katalyst framework
**Status:** Ready for reference and development
