# Katalyst Backend - Comprehensive Codebase Map

## Executive Summary

Katalyst is a Kotlin backend framework built on Ktor and Koin DI that provides:
- Dependency injection with automatic component discovery
- Persistence layer with transaction management
- Event-driven architecture with event bus
- Scheduled job execution
- Database migrations
- Comprehensive logging and bootstrap tracking

**Repository Root:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst`

---

## Core Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     HTTP Layer (Ktor)                       │
│  (katalyst-ktor, katalyst-ktor-engine, katalyst-ktor-engine-netty) │
├─────────────────────────────────────────────────────────────┤
│              Application Layer (Services, Validators)        │
│                    (katalyst-example)                        │
├─────────────────────────────────────────────────────────────┤
│         Domain Layer (Entities, Events, Domain Models)       │
│      (katalyst-events, katalyst-events-bus, katalyst-example) │
├─────────────────────────────────────────────────────────────┤
│        Persistence & Transaction Management                 │
│  (katalyst-persistence, katalyst-transactions, katalyst-core) │
├─────────────────────────────────────────────────────────────┤
│         DI & Lifecycle Management                           │
│  (katalyst-di, katalyst-scanner, katalyst-scheduler, katalyst-migrations) │
├─────────────────────────────────────────────────────────────┤
│              Infrastructure & Support                        │
│     (katalyst-messaging, katalyst-websockets)               │
└─────────────────────────────────────────────────────────────┘
```

---

## Module Directory Structure & File Map

### 1. katalyst-core (Foundational Interfaces)

**Purpose:** Defines base interfaces and types used across all modules

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-core/src/main/kotlin/com/ead/katalyst/core`

**Key Files:**
```
katalyst-core/src/main/kotlin/com/ead/katalyst/core/
├── component/
│   ├── Component.kt                    # Interface for discoverable framework components
│   └── Service.kt                      # Base service interface
├── exception/
│   └── DependencyInjectionException.kt # DI-specific exceptions
├── persistence/
│   └── Table.kt                        # Table interface for database definitions
├── transaction/
│   └── DatabaseTransactionManager.kt   # Type alias (backwards compatibility)
│                                        # Real impl: katalyst-transactions/manager/DatabaseTransactionManager.kt
└── validation/
    ├── ValidationResult.kt
    └── Validator.kt                    # Base validator interface
```

**Key Components:**
- `Component` - Interface for any discoverable component
- `Service` - Base service interface for business logic
- `Validator` - Interface for validation logic
- `DatabaseTransactionManager` - Type alias pointing to actual implementation

---

### 2. katalyst-persistence (Data Access Layer)

**Purpose:** Repository pattern, undo/rollback strategies, operation logging

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst`

**Key Files:**
```
katalyst-persistence/src/main/kotlin/com/ead/katalyst/
├── config/
│   └── DatabaseConfig.kt               # Database connection configuration
├── database/
│   ├── DatabaseFactory.kt              # Creates Database instance from config
│   ├── DatabaseModule.kt               # Koin module for database setup
│   ├── adapter/
│   │   └── PersistenceTransactionAdapter.kt  # Transaction adapter for DB layer
│   └── table/
│       ├── OperationLogTable.kt        # Tracks all database operations
│       └── WorkflowStateTable.kt       # Stores workflow state/progress
└── repositories/
    ├── Identifiable.kt                 # Interface for entities with ID
    ├── CrudRepository.kt               # Generic CRUD interface implementation
    │                                    # (insert, update, delete, find, findAll, count, etc.)
    ├── TrackerRepository.kt            # Base class that auto-tracks operations
    │                                    # in workflow context
    ├── implementation/
    │   ├── OperationLogRepository.kt   # Repository for operation logs
    │   └── WorkflowStateRepository.kt  # Repository for workflow states
    ├── model/
    │   ├── PageInfo.kt                 # Pagination metadata
    │   ├── QueryFilter.kt              # Query filtering/sorting
    │   └── SortOrder.kt                # Sort order enumeration
    └── undo/
        ├── UndoStrategy.kt             # Interface for rollback strategies
        ├── InsertUndoStrategy.kt       # Undo for INSERT operations
        ├── UpdateUndoStrategy.kt       # Undo for UPDATE operations
        ├── DeleteUndoStrategy.kt       # Undo for DELETE operations
        ├── APICallUndoStrategy.kt      # Undo for external API calls
        ├── SimpleUndoEngine.kt         # Basic undo implementation
        ├── EnhancedUndoEngine.kt       # Advanced undo with strategies
        ├── UndoStrategyRegistry.kt     # Registry managing undo strategies
        └── RetryPolicy.kt              # Retry configuration for undo ops
```

**Key Classes & Concepts:**

1. **TrackerRepository** (`TrackerRepository.kt`)
   - Location: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/TrackerRepository.kt`
   - Base class for repositories that auto-track operations
   - Automatically logs operations to workflow context (if active)
   - Provides `tracked()` method for wrapping operations
   - Non-blocking operation logging (async, fire-and-forget)
   - Auto-extracts resource type name from repository class

2. **CrudRepository** (`CrudRepository.kt`)
   - Generic interface with default CRUD implementations
   - Supports insert, update, delete, find by ID, find all with pagination
   - Auto-maps entity properties to table columns (snake_case ↔ camelCase conversion)
   - Uses reflection to bind properties to columns

3. **Undo Strategies**
   - `InsertUndoStrategy` - Deletes inserted rows
   - `UpdateUndoStrategy` - Restores previous values
   - `DeleteUndoStrategy` - Re-inserts deleted rows
   - `APICallUndoStrategy` - Handles external API call rollbacks
   - `UndoStrategyRegistry` - Registry pattern for strategy selection

---

### 3. katalyst-transactions (Transaction Management)

**Purpose:** Transactional operations with hooks, workflow tracking, adapters

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions`

**Key Files:**
```
katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/
├── manager/
│   ├── DatabaseTransactionManager.kt   # Main transaction executor
│   │                                    # (wraps Exposed's newSuspendedTransaction)
│   └── TransactionManager.kt           # Interface for transaction management
├── adapter/
│   ├── TransactionAdapter.kt           # Interface for cross-cutting concerns
│   └── TransactionAdapterRegistry.kt   # Registry for adapters
├── context/
│   └── TransactionContext.kt           # Wrapper for transaction event context
├── hooks/
│   └── TransactionPhase.kt             # Phases: BEFORE_BEGIN, AFTER_BEGIN, etc.
└── workflow/
    ├── TransactionOperation.kt         # Represents a single operation
    ├── OperationLog.kt                 # Logs all operations in transaction
    ├── CurrentWorkflowContext.kt       # Thread-local workflow ID storage
    ├── UndoEngine.kt                   # Interface for undo operations
    ├── WorkflowStateManager.kt         # Manages workflow state
    ├── WorkflowStateMachine.kt         # State machine for workflow execution
    ├── WorkflowComposer.kt             # Builds workflows from operations
    ├── RecoveryJobScheduler.kt         # Schedules recovery of failed workflows
    ├── WorkflowRecoveryJob.kt          # Job that recovers failed workflows
    └── RecoveryHealthMonitor.kt        # Monitors recovery job health
```

**Key Classes & Concepts:**

1. **DatabaseTransactionManager** (Primary Transaction Executor)
   - Location: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/manager/DatabaseTransactionManager.kt`
   - Wraps Exposed's `newSuspendedTransaction` for suspended function support
   - Manages transaction lifecycle with adapter hooks
   - Supports workflow ID tracking for operation logging
   - Transaction phases:
     - `BEFORE_BEGIN` - Before transaction starts
     - `AFTER_BEGIN` - After transaction starts, before user block
     - `BEFORE_COMMIT` - Before commit (still in transaction)
     - `AFTER_COMMIT` - After successful commit
     - `ON_ROLLBACK` - When rollback occurs
     - `AFTER_ROLLBACK` - After rollback completes
   - Automatic cleanup of workflow context (prevents leaks)

2. **TransactionAdapter** (Cross-Cutting Concerns)
   - Interface for modules to hook into transaction lifecycle
   - Executed at specific phases
   - Priority-based ordering
   - Examples: persistence adapters, event adapters

3. **TransactionPhase** (Lifecycle Phases)
   - `BEFORE_BEGIN` - Setup phase
   - `AFTER_BEGIN` - Transaction started
   - `BEFORE_COMMIT` - Pre-commit validation
   - `AFTER_COMMIT` - Post-commit actions (event publishing)
   - `ON_ROLLBACK` - Error recovery
   - `AFTER_ROLLBACK` - Cleanup after rollback

4. **CurrentWorkflowContext** (Thread-Local)
   - Stores current workflow ID in thread-local storage
   - Allows repositories to auto-track operations
   - Cleared after transaction completes

---

### 4. katalyst-di (Dependency Injection & Application Startup)

**Purpose:** Koin DI setup, component discovery, lifecycle management, logging

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di`

**Key Files:**
```
katalyst-di/src/main/kotlin/com/ead/katalyst/di/
├── KatalystApplication.kt              # Application DSL entry point
├── config/
│   ├── DIConfiguration.kt              # DI configuration loading
│   ├── ServerConfiguration.kt          # Server (Ktor) configuration
│   └── ServerConfigurationBuilder.kt   # Builder for server config
├── feature/
│   └── KatalystFeature.kt              # Interface for optional features
├── internal/
│   ├── AutoBindingRegistrar.kt         # Auto-discovers and registers components
│   ├── ServiceRegistry.kt              # Registry for services
│   ├── KtorModuleRegistry.kt           # Registry for Ktor modules
│   └── RouteModuleMarker.kt            # Marker interface for route modules
├── module/
│   ├── CoreDIModule.kt                 # Koin module for core components
│   └── ScannerDIModule.kt              # Koin module for scanner
├── lifecycle/
│   ├── ApplicationInitializer.kt       # Base initializer interface
│   ├── BootstrapProgressLogger.kt      # Tracks bootstrap phases in real-time
│   │                                    # Displays structured progress with icons
│   ├── DiscoverySummaryLogger.kt       # Shows discovered components summary
│   ├── EngineInitializer.kt            # Initializes Ktor engine
│   ├── InitializerRegistry.kt          # Registry for custom initializers
│   ├── LifecycleException.kt           # Lifecycle-related exceptions
│   ├── StartupValidator.kt             # Validates startup requirements
│   ├── StartupWarningsAggregator.kt    # Aggregates warnings during startup
│   └── StartupWarnings.kt              # Global warning collector
└── migrations/
    └── KatalystMigration.kt            # Migration marker interface
```

**Key Classes & Concepts:**

1. **KatalystApplication** (Main Entry Point)
   - Location: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/KatalystApplication.kt`
   - DSL builder pattern for application configuration
   - Fluent API: `katalystApplication { ... }`
   - Chains initialization phases automatically

2. **Logging Operations:**

   a) **BootstrapProgressLogger** (Real-time Phase Tracking)
   - Location: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/BootstrapProgressLogger.kt`
   - Displays 7 bootstrap phases with progress icons (⏳, ✓, ✗, ⊘, ○)
   - Measures duration for each phase
   - Tracks overall bootstrap time
   - Visual output with ASCII box borders
   - Phases tracked:
     1. Koin DI Bootstrap (Component scanning)
     2. Scheduler Method Discovery
     3. Component Discovery (repositories, services, validators)
     4. Database Schema Initialization
     5. Transaction Adapter Registration
     6. Application Initialization Hooks
     7. Ktor Engine Startup

   b) **DiscoverySummaryLogger** (Component Summary)
   - Location: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/DiscoverySummaryLogger.kt`
   - Consolidates discovered components into organized tables
   - Displays counts for:
     - Repositories
     - Services
     - Components
     - Validators
     - Database tables
     - Ktor modules
   - Reports empty sections as warnings
   - ASCII box formatting for visual clarity

3. **AutoBindingRegistrar** (Component Discovery)
   - Location: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/internal/AutoBindingRegistrar.kt`
   - Automatically discovers and registers:
     - Repositories (extend CrudRepository)
     - Services (marked with @Service)
     - Components (implement Component)
     - Validators (marked with @Validator)
   - Integrates with Koin DI
   - Supports custom scan packages

---

### 5. katalyst-scanner (Type Discovery & Component Scanning)

**Purpose:** Scan classpath for components, extract metadata, discover types

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-scanner/src/main/kotlin/com/ead/katalyst/scanner`

**Key Files:**
```
katalyst-scanner/src/main/kotlin/com/ead/katalyst/scanner/
├── core/
│   ├── DiscoveryConfig.kt              # Configuration for discovery
│   ├── DiscoveryMetadata.kt            # Metadata about discovered types
│   ├── DiscoveryPredicate.kt           # Predicates for filtering
│   ├── DiscoveryRegistry.kt            # Registry for discoveries
│   └── TypeDiscovery.kt                # Core discovery engine
├── extensions/
│   ├── DiscoveryDSL.kt                 # DSL for type discovery
│   └── MethodDiscoveryDSL.kt           # DSL for method discovery
├── integration/
│   ├── AutoDiscoveryEngine.kt          # Automatic discovery coordination
│   ├── KoinDiscoveryRegistry.kt        # Koin integration
│   └── ScannerModule.kt                # Koin module for scanner
├── predicates/
│   └── BuiltInPredicates.kt            # Standard filtering predicates
├── scanner/
│   ├── InMemoryDiscoveryRegistry.kt    # In-memory registry impl
│   ├── KotlinMethodScanner.kt          # Scans Kotlin methods
│   └── ReflectionsTypeScanner.kt       # Scans types with Reflections
└── util/
    ├── GenericTypeExtractor.kt         # Extracts generic type parameters
    └── MethodMetadata.kt               # Method introspection info
```

**Key Components:**
- `TypeDiscovery` - Discovers types on classpath
- `KotlinMethodScanner` - Extracts method metadata
- `GenericTypeExtractor` - Handles generic type resolution
- Supports custom discovery predicates

---

### 6. katalyst-events (Event Definitions)

**Purpose:** Domain event interfaces and validation

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-events/src/main/kotlin/com/ead/katalyst/events`

**Key Files:**
```
katalyst-events/src/main/kotlin/com/ead/katalyst/events/
├── DomainEvent.kt                      # Base interface for domain events
├── EventHandler.kt                     # Interface for event handlers
├── EventMetadata.kt                    # Metadata about events
├── config/
│   └── EventConfiguration.kt           # Event system configuration
├── exception/
│   └── EventException.kt               # Event-specific exceptions
└── validation/
    └── EventValidator.kt               # Validates events before publishing
```

---

### 7. katalyst-events-bus (Event Publishing & Handling)

**Purpose:** In-memory event bus with handler registry and transaction awareness

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus`

**Key Files:**
```
katalyst-events-bus/src/main/kotlin/com/ead/katalyst/events/bus/
├── EventBus.kt                         # Interface for event publishing
├── ApplicationEventBus.kt              # Default event bus implementation
├── TransactionAwareEventBus.kt         # Publishes events after commit
├── EventHandlerRegistry.kt             # Registry of event handlers
├── EventBusInterceptor.kt              # Interceptor pattern for publishing
├── EventTopology.kt                    # Models event handler graph
├── EventBusModule.kt                   # Koin module for event bus
├── adapter/
│   └── EventsTransactionAdapter.kt     # Transaction adapter for event publishing
└── exception/
    ├── EventPublishingException.kt
    └── HandlerException.kt
```

**Key Concepts:**
- Events are queued during transaction
- Published after transaction commits
- Prevents lost events on rollback
- Handler registry with auto-discovery

---

### 8. katalyst-events-transport (Event Message Serialization)

**Purpose:** Message format for events crossing network boundaries

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-events-transport/src/main/kotlin/com/ead/katalyst/events/transport`

**Key Files:**
```
katalyst-events-transport/src/main/kotlin/com/ead/katalyst/events/transport/
├── EventMessage.kt                     # Serializable event message format
└── EventMessageBuilder.kt              # Builder for constructing messages
```

---

### 9. katalyst-events-client (Event Publishing Client)

**Purpose:** Client for publishing events to remote systems

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-events-client/src/main/kotlin/com/ead/katalyst/client`

**Key Files:**
```
katalyst-events-client/src/main/kotlin/com/ead/katalyst/client/
├── EventClient.kt                      # Client interface
├── DefaultEventClient.kt               # Default HTTP-based implementation
├── PublishResult.kt                    # Result of publishing
├── RetryPolicy.kt                      # Retry configuration
├── EventClientInterceptor.kt           # Interceptor for publishing
├── config/
│   └── EventClientConfiguration.kt     # Client configuration
├── feature/
│   └── EventSystemFeature.kt           # Feature for event client
└── exception/
    └── ClientException.kt              # Client-specific exceptions
```

---

### 10. katalyst-scheduler (Scheduled Job Execution)

**Purpose:** Schedule periodic jobs with cron expressions

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-scheduler/src/main/kotlin/com/ead/katalyst/scheduler`

**Key Files:**
```
katalyst-scheduler/src/main/kotlin/com/ead/katalyst/scheduler/
├── SchedulerFeature.kt                 # Feature interface
├── SchedulerModules.kt                 # Koin module
├── config/
│   └── ScheduleConfig.kt               # Configuration
├── cron/
│   ├── CronExpression.kt               # Cron expression parser
│   └── CronValidator.kt                # Validates cron syntax
├── exception/
│   └── SchedulerException.kt
├── extension/
│   ├── DIExtensions.kt                 # DI helper extensions
│   └── SchedulerAccessors.kt           # Accessor functions
├── job/
│   └── SchedulerJobHandle.kt           # Handle to manage jobs
├── lifecycle/
│   └── SchedulerInitializer.kt         # Initializes scheduler
└── service/
    └── SchedulerService.kt             # Core scheduling service
```

---

### 11. katalyst-migrations (Database Schema Management)

**Purpose:** Version database schema and apply migrations

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-migrations/src/main/kotlin/com/ead/katalyst/migrations`

**Key Files:**
```
katalyst-migrations/src/main/kotlin/com/ead/katalyst/migrations/
├── feature/
│   └── MigrationFeature.kt             # Feature interface
├── runner/
│   └── MigrationRunner.kt              # Executes migrations
├── options/
│   └── MigrationOptions.kt             # Migration configuration
├── extensions/
│   └── MigrationBuilderExtensions.kt   # DSL for migrations
└── internal/
    └── MigrationHistoryTable.kt        # Tracks applied migrations
```

---

### 12. katalyst-messaging (Message Queue Integration)

**Purpose:** Producer/consumer abstractions for message queues

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-messaging/src/main/kotlin/com/ead/katalyst/messaging`

**Key Files:**
```
katalyst-messaging/src/main/kotlin/com/ead/katalyst/messaging/
├── Connection.kt                       # Message broker connection
├── Destination.kt                      # Queue/topic destination
├── Message.kt                          # Message structure
├── MessagingClientFactory.kt           # Factory for creating clients
├── ProducerConsumer.kt                 # Producer/consumer interfaces
├── routing/
│   └── Routing.kt                      # Message routing logic
├── serdes/
│   └── Serdes.kt                       # Serialization/deserialization
├── error/
│   └── ErrorHandling.kt                # Error handling policies
└── stub/
    └── LoggingMessagingClientFactory.kt # Logging stub implementation
```

---

### 13. katalyst-messaging-amqp (AMQP Integration)

**Purpose:** AMQP/RabbitMQ specific implementation

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-messaging-amqp`

---

### 14. katalyst-ktor (HTTP Framework Integration)

**Purpose:** Ktor framework integration and routing

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-ktor/src/main/kotlin/com/ead/katalyst/ktor`

**Key Files:**
```
katalyst-ktor/src/main/kotlin/com/ead/katalyst/ktor/
├── KtorModule.kt                       # Base Ktor module interface
├── builder/
│   ├── ExceptionHandlerBuilder.kt      # Builds exception handling
│   └── RoutingBuilder.kt               # Builds route definitions
├── extension/
│   └── KoinRouteExtensions.kt          # Koin integration for routes
└── middleware/
    └── Middleware.kt                   # Middleware interface
```

---

### 15. katalyst-ktor-engine (Ktor Server Engine)

**Purpose:** Embedded Ktor server configuration

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-ktor-engine`

---

### 16. katalyst-ktor-engine-netty (Netty Engine)

**Purpose:** Netty engine specific implementation for Ktor

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-ktor-engine-netty`

---

### 17. katalyst-websockets (WebSocket Support)

**Purpose:** WebSocket server functionality

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-websockets`

---

### 18. katalyst-example (Reference Application)

**Purpose:** Demonstration application showing framework usage

**Location:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-example/src/main/kotlin/com/ead/katalyst/example`

**Key Files:**
```
katalyst-example/src/main/kotlin/com/ead/katalyst/example/
├── Application.kt                      # Application entry point
├── api/
│   ├── dto/
│   │   ├── AuthDtos.kt                 # Authentication request/response DTOs
│   │   ├── UserDtos.kt                 # User DTOs
│   │   └── HealthCheckDtos.kt          # Health check DTOs
│   └── exception_handler/
│       └── ExceptionHandler.kt         # Exception mapping to HTTP responses
├── config/
│   ├── CorrelationIdMiddleware.kt      # Adds request correlation IDs
│   ├── HttpConfigMiddleware.kt         # HTTP configuration
│   ├── RequestLoggingMiddleware.kt     # Logs incoming requests
│   └── security/
│       ├── JwtSettings.kt              # JWT configuration
│       └── SecurityMiddleware.kt       # Authentication/authorization
├── domain/
│   ├── User.kt                         # User domain model
│   ├── UserProfile.kt                  # User profile model
│   ├── AuthAccount.kt                  # Authentication account model
│   ├── AuthValidator.kt                # Validation logic
│   ├── events/
│   │   ├── UserRegisteredEvent.kt      # User registration event
│   │   └── handlers/
│   │       └── UserRegistrationHandler.kt  # Event handler
│   └── exception/
│       ├── UserExampleValidationException.kt
│       └── TestException.kt
├── infra/
│   ├── config/
│   │   └── DatabaseConfigFactory.kt    # Database config
│   └── database/
│       ├── entities/
│       │   ├── UserProfileEntity.kt    # User profile entity
│       │   └── AuthAccountEntity.kt    # Auth account entity
│       ├── repositories/
│       │   ├── UserProfileRepository.kt    # Extends TrackerRepository
│       │   └── AuthAccountRepository.kt    # Extends TrackerRepository
│       ├── tables/
│       │   ├── UserProfilesTable.kt    # Exposed table definition
│       │   └── AuthAccountsTable.kt    # Exposed table definition
│       └── mappers/
│           └── AuthMappers.kt          # Entity to domain mappers
└── routes/
    ├── AuthRoutes.kt                   # Authentication endpoints
    ├── UserRoutes.kt                   # User management endpoints
    ├── HealthCheckRoutes.kt            # Health check endpoint
    └── NotificationWebSocketRoutes.kt  # WebSocket notification routes
```

**Example Implementation:**
- Shows how to use TrackerRepository (UserProfileRepository, AuthAccountRepository)
- Demonstrates event publishing (UserRegisteredEvent)
- Shows middleware usage (logging, security, correlation IDs)
- Uses domain-driven design patterns

---

## Key Component Relationships

### Transaction Flow

```
Service
  └─ transactionManager.transaction { workflowId }
      ├─ [1] Execute BEFORE_BEGIN adapters
      ├─ [2] Begin Exposed transaction
      ├─ [3] Execute AFTER_BEGIN adapters
      ├─ [4] Execute user block
      │   └─ Repository.save()
      │       └─ CurrentWorkflowContext.get() returns workflowId
      │       └─ TrackerRepository logs operation async
      ├─ [5] Execute BEFORE_COMMIT adapters (still in TX)
      ├─ [6] Commit database transaction
      ├─ [7] Execute AFTER_COMMIT adapters
      │   └─ EventBus publishes queued events
      └─ [8] Clear workflow context
```

### Component Discovery Flow

```
KatalystApplication { database(...), scanPackages(...) }
  ├─ Phase 1: Koin DI Bootstrap (BootstrapProgress.startPhase(1))
  ├─ Phase 2: Scheduler Method Discovery
  ├─ Phase 3: Component Discovery (DiscoverySummaryLogger)
  │   ├─ AutoBindingRegistrar finds repositories
  │   ├─ AutoBindingRegistrar finds services
  │   ├─ AutoBindingRegistrar finds components
  │   └─ DiscoverySummary.display() shows results
  ├─ Phase 4: Database Schema Initialization
  ├─ Phase 5: Transaction Adapter Registration
  ├─ Phase 6: Application Initialization Hooks
  └─ Phase 7: Ktor Engine Startup (BootstrapProgress.completePhase(7))
```

---

## TrackerRepository Details

**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/TrackerRepository.kt`

**Purpose:** Base class for repositories that automatically track all operations for workflow purposes

**Key Features:**
1. Auto-tracks operations in the workflow context
2. Non-blocking async logging
3. Automatically logs to operation log if workflow is active
4. Tracks: operation type, resource type, resource ID

**Usage Pattern:**
```kotlin
class UserRepository(operationLog: OperationLog) : 
    TrackerRepository<Long, User>(operationLog) {
    
    override suspend fun save(entity: User): User = tracked(
        operation = "INSERT",
        resourceType = "User",
        resourceId = entity.id,
        action = { super.save(entity) }
    )
}
```

---

## DatabaseTransactionManager Details

**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/manager/DatabaseTransactionManager.kt`

**Purpose:** Wraps Exposed's transaction handling with adapter system and workflow tracking

**Key Responsibilities:**
1. Creates and manages database transactions
2. Executes adapters at lifecycle phases
3. Tracks workflow ID in thread-local context
4. Handles commit/rollback with adapter hooks
5. Auto-cleans up workflow context

**Transaction Phases (in order):**
1. `BEFORE_BEGIN` - Setup before transaction
2. `AFTER_BEGIN` - Transaction started
3. `BEFORE_COMMIT` - Pre-commit validation (still in TX)
4. `AFTER_COMMIT` - Post-commit actions (outside TX)
5. `ON_ROLLBACK` - During rollback
6. `AFTER_ROLLBACK` - After rollback complete

---

## Logging Operations

### 1. BootstrapProgressLogger

**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/BootstrapProgressLogger.kt`

**Purpose:** Real-time bootstrap phase tracking with progress display

**Features:**
- Tracks 7 bootstrap phases
- Shows status icons: ⏳ (running), ✓ (completed), ✗ (failed), ⊘ (skipped), ○ (pending)
- Measures phase duration in milliseconds
- Displays overall bootstrap time
- ASCII box formatting for visual clarity

**Usage:**
```kotlin
BootstrapProgress.startPhase(3)
// ... do work ...
BootstrapProgress.completePhase(3, "Discovered 42 components")
BootstrapProgress.displayProgressSummary()
```

### 2. DiscoverySummaryLogger

**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/DiscoverySummaryLogger.kt`

**Purpose:** Consolidates component discovery output into organized tables

**Features:**
- Displays discovered items organized by type
- Shows counts for each category
- Reports empty sections as warnings
- ASCII box formatting
- Supports custom component info (name, type, annotation, metadata)

**Usage:**
```kotlin
DiscoverySummary.addRepository("UserRepository", "@Repository")
DiscoverySummary.addService("UserService", "@Service")
DiscoverySummary.display()
```

### 3. RequestLoggingMiddleware

**File:** `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-example/src/main/kotlin/com/ead/katalyst/example/config/RequestLoggingMiddleware.kt`

**Purpose:** Logs HTTP requests in example application

---

## File Structure Summary

```
katalyst/
├── katalyst-core/              # Base interfaces (Component, Service, Validator, Table)
├── katalyst-di/                # Koin DI, KatalystApplication, lifecycle logging
├── katalyst-persistence/       # Repositories, CrudRepository, TrackerRepository, undo strategies
├── katalyst-transactions/      # DatabaseTransactionManager, transaction adapters, workflow mgmt
├── katalyst-scanner/           # Type discovery, component scanning
├── katalyst-events/            # Event interfaces and validation
├── katalyst-events-bus/        # Event publishing and handler registry
├── katalyst-events-transport/  # Event message serialization
├── katalyst-events-client/     # Remote event publishing client
├── katalyst-scheduler/         # Scheduled job execution with cron
├── katalyst-migrations/        # Database schema versioning
├── katalyst-messaging/         # Message queue abstractions
├── katalyst-messaging-amqp/    # AMQP/RabbitMQ implementation
├── katalyst-ktor/              # Ktor framework integration
├── katalyst-ktor-engine/       # Embedded Ktor server
├── katalyst-ktor-engine-netty/ # Netty engine for Ktor
├── katalyst-websockets/        # WebSocket support
└── katalyst-example/           # Reference application
```

---

## Key Design Patterns

1. **Repository Pattern** - CrudRepository provides generic CRUD with auto-mapping
2. **Adapter Pattern** - TransactionAdapter for cross-cutting concerns
3. **Observer Pattern** - Event bus for event-driven architecture
4. **Builder Pattern** - KatalystApplication DSL
5. **Registry Pattern** - Component registries (services, adapters, handlers)
6. **Strategy Pattern** - Undo strategies for rollback operations
7. **Template Method** - TransactionManager lifecycle hooks
8. **Dependency Injection** - Koin DI integration throughout

---

## Common File Paths Reference

| Component | Primary Location |
|-----------|-----------------|
| TrackerRepository | `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/TrackerRepository.kt` |
| DatabaseTransactionManager | `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-transactions/src/main/kotlin/com/ead/katalyst/transactions/manager/DatabaseTransactionManager.kt` |
| BootstrapProgressLogger | `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/BootstrapProgressLogger.kt` |
| DiscoverySummaryLogger | `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/DiscoverySummaryLogger.kt` |
| CrudRepository | `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/CrudRepository.kt` |
| KatalystApplication | `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/KatalystApplication.kt` |
| AutoBindingRegistrar | `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-di/src/main/kotlin/com/ead/katalyst/di/internal/AutoBindingRegistrar.kt` |

