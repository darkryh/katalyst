# KATALYST UNTESTED MODULES - COMPREHENSIVE TESTING ANALYSIS

## Priority 1: CRITICAL MODULES (0% Coverage)

---

## 1. KATALYST-PERSISTENCE (24 Files)

**Module Purpose:** Manages database persistence, CRUD operations, repository patterns, and transaction undo/rollback mechanisms.

### 1.1 PUBLIC APIS

#### CrudRepository Interface (Generic CRUD Pattern)
```kotlin
interface CrudRepository<Id : Comparable<Id>, IdentifiableEntityId : Identifiable<Id>>
```

**Public Methods:**
- `fun save(entity: IdentifiableEntityId): IdentifiableEntityId` - Insert or update
- `fun findById(id: Id): IdentifiableEntityId?` - Retrieve by ID
- `fun findAll(): List<IdentifiableEntityId>` - Get all entities
- `fun findAll(filter: QueryFilter): Pair<List<IdentifiableEntityId>, PageInfo>` - Paginated search
- `suspend fun count(): Long` - Count total entities
- `suspend fun delete(id: Id)` - Delete by ID
- `fun map(row: ResultRow): IdentifiableEntityId` - Row-to-entity mapping

**Key Classes:**
- **CrudRepository**: Generic base for all persistence operations
- **Identifiable<T>**: Marker interface requiring entities have an ID field
- **TableMapper**: Bi-directional mapping (entity ↔ database row)
- **QueryFilter**: Encapsulates sort/pagination (sortBy, sortOrder, limit, offset)
- **PageInfo**: Pagination metadata (limit, offset, total)
- **SortOrder**: Enum (ASCENDING, DESCENDING)

### 1.2 REPOSITORY IMPLEMENTATIONS

#### WorkflowStateRepository
**Purpose:** Persistent storage for distributed transaction workflow states

**Key Methods:**
- `suspend fun startWorkflow(workflowId: String, workflowName: String)` - Create new workflow
- `suspend fun commitWorkflow(workflowId: String)` - Mark as successfully completed
- `suspend fun failWorkflow(workflowId: String, failedAtOperation: Int, error: String)` - Mark as failed
- `suspend fun markAsUndone(workflowId: String)` - Mark as rolled back
- `suspend fun getWorkflowState(workflowId: String): WorkflowState?` - Retrieve current state
- `suspend fun getFailedWorkflows(): List<WorkflowState>` - Find workflows needing recovery
- `suspend fun deleteOldWorkflows(beforeTimestamp: Long): Int` - Archive/cleanup

**Workflow States:**
- `STARTED` → `COMMITTED` (success path)
- `STARTED` → `FAILED` → `UNDONE` (rollback path)
- `STARTED` → `FAILED` → `FAILED_UNDO` (recovery failure)

#### OperationLogRepository
**Purpose:** Audit log of all database operations for undo reconstruction

**Likely Methods:**
- Log INSERT/UPDATE/DELETE operations with before/after state
- Store operation metadata (timestamp, user, transaction ID)
- Retrieve operations for a workflow to reconstruct undo sequence

### 1.3 UNDO/ROLLBACK SYSTEM (Complex!)

#### UndoStrategy Interface
```kotlin
interface UndoStrategy {
    fun canHandle(operationType: String, resourceType: String): Boolean
    suspend fun undo(operation: TransactionOperation): Boolean
}
```

**Implementations:**
1. **InsertUndoStrategy** - Undo INSERT by DELETE
   - Reads `operation.resourceId` to identify record
   - Uses `operation.undoData` to validate
   - Status: Phase 2 stub (needs actual repository integration)

2. **DeleteUndoStrategy** - Undo DELETE by RE-INSERT
   - Reads `operation.undoData` containing full serialized record
   - Must deserialize and re-insert
   - Status: Phase 2 stub (needs actual repository integration)

3. **UpdateUndoStrategy** - Undo UPDATE by applying before-state
   - Likely reads previous values from `operation.undoData`
   - Updates back to original state

4. **APICallUndoStrategy** - Undo external API calls
   - Call inverse endpoint
   - Handle API failures

#### EnhancedUndoEngine
```kotlin
class EnhancedUndoEngine(
    strategyRegistry: UndoStrategyRegistry = default(),
    retryPolicy: RetryPolicy = aggressive()
) : UndoEngine {
    override suspend fun undoWorkflow(
        workflowId: String,
        operations: List<TransactionOperation>
    ): UndoEngine.UndoResult
}
```

**Execution Flow:**
1. Process operations in **reverse LIFO order** (most recent first)
2. Find matching strategy for each operation type
3. Execute with retry policy (exponential backoff)
4. Track per-operation success/failure
5. Continue with best-effort (failures don't block other operations)
6. Return `UndoResult` with summary

#### UndoStrategyRegistry
```kotlin
class UndoStrategyRegistry {
    fun register(strategy: UndoStrategy): UndoStrategyRegistry
    fun findStrategy(operationType: String, resourceType: String): UndoStrategy
    companion object {
        fun createDefault(): UndoStrategyRegistry // Pre-registers all 4 strategies
    }
}
```

**Fallback:** `NoOpUndoStrategy` - logs warning but returns true (allows workflow continuation)

#### RetryPolicy (Persistence Module)
```kotlin
class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 5000,
    val backoffMultiplier: Double = 2.0,
    val retryPredicate: (Exception) -> Boolean = { true }
) {
    suspend fun execute(operation: suspend () -> Boolean): Boolean
}
```

**Features:**
- Exponential backoff with jitter (prevents thundering herd)
- Custom retry predicates (e.g., `transientOnly()` for network errors)
- Predefined strategies: `retryAll()`, `retryTransient()`, `aggressive()`, `conservative()`

### 1.4 DATABASE LAYER

#### DatabaseFactory
```kotlin
class DatabaseFactory private constructor(
    val database: Database,  // Exposed Database instance
    private val dataSource: HikariDataSource
) : AutoCloseable {
    companion object {
        fun create(config: DatabaseConfig, tables: List<Table> = emptyList()): DatabaseFactory
    }
}
```

**Configuration:**
- HikariCP connection pooling
- Pool size, idle connections, timeouts, transaction isolation
- Auto-schema creation on startup
- Thread-safe resource management

### 1.5 TESTING REQUIREMENTS

#### CRUD Operations (Core, Critical Path)
**What to Test:**
1. **Save/Insert**
   - Entity with null ID → auto-generates ID
   - Entity with existing ID → updates
   - Reload after insert (verify ID assigned)
   - Null field handling
   - Constraint violations (unique keys, foreign keys)

2. **FindById**
   - Existing entity → returns mapped entity
   - Non-existent ID → returns null
   - ID type conversions (Long, UUID, String)

3. **FindAll**
   - Empty table → empty list
   - Multiple entities → ordered by ID DESC (default)
   - Sorting by custom column → ascending/descending
   - Pagination (limit/offset) → correct subset
   - Total count accuracy

4. **Delete**
   - Existing entity → removed from table
   - Non-existent ID → no error (idempotent)
   - Cascade behavior (if foreign keys exist)

5. **Count**
   - Returns correct number
   - After insert/delete → updates correctly

#### Undo/Rollback (Most Complex!)
**What to Test:**
1. **InsertUndoStrategy**
   - DELETE target record by ID
   - Handle missing undo data
   - Validate operation.undoData structure

2. **DeleteUndoStrategy**
   - RE-INSERT serialized record
   - Deserialize JSON undo data
   - Handle malformed data

3. **UpdateUndoStrategy**
   - Apply before-state values
   - Partial vs full field updates
   - Type conversions

4. **APICallUndoStrategy**
   - Call inverse endpoint
   - Handle HTTP errors
   - Timeout handling

5. **EnhancedUndoEngine**
   - LIFO execution (reverse order)
   - Strategy selection by operation type/resource
   - Retry logic on transient failures
   - Continue on operation failure (best-effort)
   - Correct result summary counts
   - Exception handling and error tracking

6. **RetryPolicy**
   - Exponential backoff calculation
   - Jitter prevents thundering herd
   - Max retries exhaustion
   - Custom retry predicates
   - Immediate retry vs delayed

#### WorkflowStateRepository (State Machine)
**What to Test:**
1. State transitions:
   - `startWorkflow` → STARTED state
   - STARTED → COMMITTED (success)
   - STARTED → FAILED (operation failure)
   - FAILED → UNDONE (successful rollback)
   - FAILED → FAILED_UNDO (rollback failure)

2. **getFailedWorkflows**
   - Returns only FAILED and FAILED_UNDO states
   - Ordered by creation timestamp
   - Empty list if none failed

3. **deleteOldWorkflows**
   - Only deletes COMMITTED workflows (not ongoing/failed)
   - Timestamp filtering (before timestamp)
   - Count accuracy

4. Error handling:
   - Database errors → logged, workflow continues
   - State queries → return null on error
   - Transactional consistency

#### DatabaseFactory
**What to Test:**
1. Connection pooling
   - Pool size limits
   - Idle connection timeout
   - Connection leaks
   - Concurrent access

2. Schema creation
   - Tables created if not exist
   - Idempotent (no errors if tables exist)
   - Schema consistency

3. Resource cleanup
   - `close()` closes datasource
   - Connection cleanup on shutdown
   - No resource leaks

### 1.6 EDGE CASES & CRITICAL PATHS

**High-Risk Scenarios:**
1. **Concurrent Operations**
   - Multiple clients inserting same entity
   - Race condition in LIFO undo execution
   - Isolation level handling

2. **Partial Failures**
   - Undo of 10 operations, 3 fail → correct error summary
   - Continue with remaining operations (best-effort)
   - No cascade failures

3. **Data Type Conversions**
   - Query sorting on wrong column type
   - Pagination with invalid offset/limit
   - Type mismatches in entity mapping

4. **Resource Exhaustion**
   - Connection pool depletion
   - Memory pressure during batch operations
   - Timeout handling (query, transaction, network)

5. **Transaction Consistency**
   - Workflow state vs actual data sync
   - Orphaned workflows in state table
   - Audit log vs actual operations sync

### 1.7 DEPENDENCIES TO MOCK

1. **Exposed Database & Tables**
   - Mock ResultRow for mapping tests
   - Mock UpdateBuilder for entity-to-statement tests
   - In-memory SQLite for integration tests (better than full mock)

2. **HikariDataSource**
   - Mock for connection pool tests
   - Test timeout scenarios

3. **TransactionOperation (external)**
   - Mock with various operation types
   - Mock with/without undoData

4. **Repository Implementations (for undo strategies)**
   - Mock delete() method (InsertUndoStrategy)
   - Mock insert() method (DeleteUndoStrategy)
   - Mock update() method (UpdateUndoStrategy)

5. **External APIs (APICallUndoStrategy)**
   - Mock HTTP client
   - Mock success/failure responses
   - Mock timeouts

---

## 2. KATALYST-EVENTS-TRANSPORT (11 Files)

**Module Purpose:** Serializes domain events for external messaging systems and routes them to destinations.

### 2.1 PUBLIC APIS

#### EventSerializer Interface
```kotlin
interface EventSerializer {
    suspend fun serialize(event: DomainEvent): EventMessage
    fun getContentType(): String
}
```

#### JsonEventSerializer
**Responsibilities:**
1. Serialize `DomainEvent` to JSON bytes
2. Extract event metadata (eventId, eventType, version, correlation-id, etc.)
3. Add headers with metadata
4. Handle JSON serialization failures

**Implementation Details:**
- Attempts Jackson reflection (`com.fasterxml.jackson.databind.ObjectMapper`)
- Falls back to `toString()` if Jackson unavailable
- Content-Type: `application/json; charset=utf-8`

**Produced Headers:**
- `event-type`, `event-id`, `event-version`
- `correlation-id`, `causation-id`, `source` (optional)
- `event-timestamp`, `content-type`

#### EventMessage Data Class
```kotlin
data class EventMessage(
    val contentType: String,
    val payload: ByteArray,
    val headers: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventId: String? = null,
    val eventType: String? = null
)
```

**Methods:**
- `fun getHeader(name: String, defaultValue: String? = null): String?`
- `fun hasHeader(name: String): Boolean`
- `fun toBuilder(): EventMessageBuilder` - Create mutable copy
- Custom `equals()` and `hashCode()` (byte array comparison)

#### EventRouter Interface
```kotlin
interface EventRouter {
    fun resolve(event: DomainEvent): Destination
    fun getRouting(event: DomainEvent): RoutingConfig? = null
}
```

**Purpose:**
- Determine destination for an event
- Support different destination types (QUEUE, TOPIC, STREAM)
- Optional routing config (routing key, priority, TTL)

**Implementations (implied):**
- Prefixed: All events → `events.{eventtype}`
- PackageBased: Group by package name
- Custom: Application-specific logic

#### RoutingStrategies (utility)
**Purpose:** Factory for common routing strategies

#### EventTypeResolver
**Purpose:** Resolve event types from serialized data

#### EventMessageBuilder
**Purpose:** Fluent builder for constructing EventMessages

### 2.2 TESTING REQUIREMENTS

#### JsonEventSerializer (Core)
**What to Test:**
1. **Happy Path Serialization**
   - Valid DomainEvent → JSON bytes
   - Payload is valid UTF-8 JSON
   - Content-Type header set correctly

2. **Metadata Extraction**
   - eventId from metadata or generated
   - eventType from metadata or class name
   - version from metadata
   - correlationId/causationId (optional)
   - source system info
   - timestamp in headers

3. **Header Generation**
   - All required headers present
   - Header values match metadata
   - Optional headers only if present
   - Case consistency (lowercase header names)

4. **Error Handling**
   - Invalid event (null fields) → throws EventSerializationException
   - JSON serialization failure → EventSerializationException with cause
   - Custom JSON mapper errors → propagates exception

5. **Edge Cases**
   - Very large event payloads
   - Special characters in metadata (quotes, newlines)
   - Unicode content
   - Null correlation ID → header absent
   - Multiple events same ID → different timestamps

6. **Jackson Fallback**
   - Without Jackson → uses toString()
   - With Jackson → uses proper JSON serialization
   - Graceful degradation

#### EventMessage
**What to Test:**
1. **Header Operations**
   - `getHeader(name)` → found header
   - `getHeader(name, default)` → missing header returns default
   - `hasHeader(name)` → true/false correctly

2. **Equality & Hashing**
   - Same payload bytes → equal
   - Different payload bytes → not equal
   - Timestamp NOT compared (same event at different times)
   - Headers must match for equality
   - hashCode consistent with equals()

3. **Builder Pattern**
   - `toBuilder()` preserves all values
   - Builder chain operations
   - Builder creates independent copy

4. **ByteArray Handling**
   - Large payloads (MB sizes)
   - Binary content
   - Empty payload
   - Null payload (error handling)

#### EventRouter & Routing
**What to Test:**
1. **Route Resolution**
   - Event type → correct destination name
   - Destination type (QUEUE/TOPIC/STREAM)
   - Multiple event types → different destinations

2. **Routing Config**
   - Returned for specific event types
   - Null for others (default routing)
   - Routing key generation
   - Priority assignment
   - TTL settings

3. **Custom Routers**
   - Package-based routing
   - Prefix-based routing
   - Conditional routing

### 2.3 DEPENDENCIES TO MOCK

1. **DomainEvent Interface**
   - Mock with test event classes
   - Various metadata combinations
   - Sealed hierarchies

2. **EventMetadata**
   - Test with/without optional fields
   - Various event types and versions

3. **Destination & RoutingConfig**
   - Mock or create test implementations
   - Different destination types

4. **Jackson ObjectMapper** (for serialization tests)
   - Mock success/failure scenarios
   - Verify reflection calls

5. **External Messaging Systems**
   - Producer/broker not tested here (integration level)
   - Just test serialization → message

---

## 3. KATALYST-EVENTS-CLIENT (10 Files)

**Module Purpose:** Public API for publishing domain events with validation, retry, and interceptor support.

### 3.1 PUBLIC APIS

#### EventClient Interface
```kotlin
interface EventClient {
    suspend fun publish(event: DomainEvent): PublishResult
    suspend fun publishBatch(events: List<DomainEvent>): PublishResult.Partial
    suspend fun publishWithDeliveryInfo(event: DomainEvent): DeliveryInfo
    companion object {
        fun builder(): EventClientBuilder
    }
}
```

#### DefaultEventClient Implementation
**Publish Flow:**
1. Execute `beforePublish` interceptors
2. Validate event
3. Publish to local EventBus (if enabled)
4. Serialize event (if external enabled)
5. Route to destination (if external enabled)
6. Publish to external system with retries (if external enabled)
7. Execute `afterPublish` interceptors
8. Return aggregate result

**Key Methods:**
- `suspend fun publish(event: DomainEvent): PublishResult`
- `suspend fun publishBatch(events: List<DomainEvent>): PublishResult.Partial`
- `suspend fun publishWithDeliveryInfo(event: DomainEvent): DeliveryInfo`

#### PublishResult (Sealed Class)
```kotlin
sealed class PublishResult {
    data class Success(
        val eventId: String,
        val eventType: String,
        val destination: String,
        val timestamp: Long,
        val metadata: Map<String, String>
    )
    
    data class Failure(
        val eventId: String?,
        val eventType: String,
        val reason: String,
        val cause: Throwable?,
        val retriable: Boolean,
        val metadata: Map<String, String>
    )
    
    data class Partial(
        val successful: Int,
        val failed: Int,
        val results: List<PublishResult>,
        val timestamp: Long
    ) {
        fun isAllSuccessful(): Boolean
        fun isPartiallySuccessful(): Boolean
        fun total(): Int
        fun successRate(): Double
    }
    
    fun isSuccess(): Boolean
    fun isFailure(): Boolean
    fun isPartial(): Boolean
}
```

#### DeliveryInfo
**Purpose:** Detailed timing and handler information
```kotlin
data class DeliveryInfo(
    val publishResult: PublishResult?,
    val handlerCount: Int,
    val handlerErrors: List<Throwable>,
    val totalDurationMs: Long,
    val busPublishMs: Long,
    val externalPublishMs: Long
) {
    fun handlersSuccessful(): Boolean
    fun isSuccessful(): Boolean
}
```

#### EventClientBuilder
```kotlin
interface EventClientBuilder {
    fun retryPolicy(policy: RetryPolicy): EventClientBuilder
    fun addInterceptor(interceptor: EventClientInterceptor): EventClientBuilder
    fun addInterceptors(interceptors: List<EventClientInterceptor>): EventClientBuilder
    fun publishToLocalBus(enabled: Boolean): EventClientBuilder
    fun publishToExternal(enabled: Boolean): EventClientBuilder
    fun correlationId(correlationId: String): EventClientBuilder
    fun batchConfiguration(maxBatchSize: Int, flushIntervalMs: Long): EventClientBuilder
    fun build(): EventClient
}
```

#### RetryPolicy Interface
```kotlin
interface RetryPolicy {
    fun shouldRetry(failure: PublishResult.Failure, attemptNumber: Int): RetryDecision
    fun getMaxAttempts(): Int
    
    data class RetryDecision(
        val shouldRetry: Boolean,
        val delayMs: Long = 0,
        val reason: String? = null
    )
    
    companion object {
        fun noRetry(): RetryPolicy
        fun exponentialBackoff(...): RetryPolicy // 100ms → 30s, 2x multiplier
        fun linearBackoff(...): RetryPolicy
        fun custom(...): RetryPolicy
        fun immediate(...): RetryPolicy
    }
}
```

#### Retry Policy Implementations
1. **NoRetryPolicy** - Fail immediately
2. **ExponentialBackoffRetryPolicy** - Exponential with jitter
3. **LinearBackoffRetryPolicy** - Linear progression
4. **CustomRetryPolicy** - Lambda-based logic
5. **ImmediateRetryPolicy** - No delay between retries

**Exponential Backoff Math:**
```
delay = min(initialDelay * multiplier^(attempt-1), maxDelay)
jitter = random * delay * 0.1
finalDelay = delay + jitter
```

#### EventClientInterceptor Interface
```kotlin
interface EventClientInterceptor {
    suspend fun beforePublish(
        event: DomainEvent,
        context: PublishContext
    ): InterceptResult = InterceptResult.Continue
    
    suspend fun afterPublish(
        event: DomainEvent,
        result: PublishResult,
        context: PublishContext,
        durationMs: Long = 0
    )
    
    suspend fun onPublishError(
        event: DomainEvent,
        exception: Throwable,
        context: PublishContext,
        attemptNumber: Int = 1
    ): ErrorHandling = ErrorHandling.Stop
    
    sealed class InterceptResult {
        object Continue
        data class Abort(val reason: String)
    }
    
    sealed class ErrorHandling {
        object Stop
        data class Retry(val delayMs: Long = 0)
        object Skip
    }
    
    data class PublishContext(
        val eventId: String,
        val eventType: String,
        val destination: String? = null,
        val retryPolicy: RetryPolicy? = null,
        val metadata: Map<String, String> = emptyMap()
    )
}
```

#### CompositeEventClientInterceptor
**Purpose:** Chain multiple interceptors
- beforePublish: Execute in order, abort on first Abort
- afterPublish: Execute in reverse order (LIFO)
- onPublishError: Stop on first handling decision

#### NoOpEventClientInterceptor
**Purpose:** Null object pattern for testing

### 3.2 TESTING REQUIREMENTS

#### EventClient.publish() (Most Complex!)
**What to Test:**

1. **Happy Path**
   - Valid event → PublishResult.Success
   - Destination populated
   - Metadata populated

2. **Validation Failures**
   - Invalid event → PublishResult.Failure (non-retriable)
   - Validation errors listed in reason
   - No external publish attempted

3. **Interceptor Hooks**
   - `beforePublish` called before validation
   - `beforePublish` can abort → PublishResult.Failure
   - `afterPublish` always called
   - Exception in interceptor → PublishResult.Failure

4. **Local Bus Publishing**
   - If enabled + bus exists → event published
   - If disabled → not published
   - Bus errors logged but don't fail overall

5. **External Publishing (with Retry)**
   - Serialization → EventMessage
   - Routing → Destination
   - Publish to broker
   - Retry on failure (with backoff)
   - Max retries exhaustion
   - Non-retriable failures fail immediately

6. **Failure Handling**
   - Serialization error → Failure, non-retriable
   - Routing error → Failure, non-retriable
   - Broker error → Failure, retriable
   - Validation error → Failure, non-retriable

7. **Configuration Variations**
   - publishToLocalBus=true, publishToExternal=false → only local
   - publishToLocalBus=false, publishToExternal=true → only external
   - Both enabled → both attempted
   - Both disabled → Success (no-op)
   - Null serializer/router → local only

#### publishBatch()
**What to Test:**
1. Multiple events → each published
2. One failure doesn't affect others
3. PublishResult.Partial aggregation
   - successful count
   - failed count
   - individual results list
   - successRate() calculation

#### publishWithDeliveryInfo()
**What to Test:**
1. Timing measurements
   - busPublishMs populated
   - externalPublishMs populated
   - totalDurationMs > bus + external
2. Handler count (if EventBus populated)
3. Handler errors aggregated
4. publishResult included if external

#### RetryPolicy Implementations
**What to Test:**

1. **NoRetryPolicy**
   - getMaxAttempts() → 0
   - shouldRetry() → always false

2. **ExponentialBackoffRetryPolicy**
   - Delays: 100, 200, 400, 800, 1600 (capped at maxDelay)
   - Attempt > maxAttempts → shouldRetry=false
   - Non-retriable failure → shouldRetry=false
   - Jitter: ±10% of delay (prevents thundering herd)

3. **LinearBackoffRetryPolicy**
   - Delays: initialDelay, initialDelay+step, initialDelay+2*step...
   - Capped at maxDelay
   - Attempt > maxAttempts → shouldRetry=false

4. **CustomRetryPolicy**
   - Uses provided resolver lambda
   - Respects maxAttempts

5. **ImmediateRetryPolicy**
   - delayMs = 0 (no wait)
   - Retries up to maxAttempts
   - Respects retriable flag

#### EventClientInterceptor
**What to Test:**

1. **beforePublish**
   - Called before validation
   - Can abort with reason
   - Abort triggers failure result

2. **afterPublish**
   - Called after success
   - Called after failure
   - Receives accurate context
   - Duration parameter populated

3. **onPublishError**
   - Called on exception
   - Can decide retry/stop/skip
   - Receives attempt number

4. **CompositeInterceptor**
   - beforePublish: all executed, first abort wins
   - afterPublish: reverse order (LIFO)
   - Error handling: first decision wins

### 3.3 DEPENDENCIES TO MOCK

1. **EventBus** (local event distribution)
   - Mock for testing local-only publish
   - Track publish calls
   - Inject handler errors

2. **EventValidator**
   - Mock valid/invalid cases
   - Mock validation errors

3. **EventSerializer**
   - Mock to return EventMessage
   - Mock serialization failures
   - Track serialize calls

4. **EventRouter**
   - Mock to return Destination
   - Mock routing failures
   - Track resolve calls

5. **DomainEvent**
   - Test event classes
   - Metadata variations

6. **Destination & RoutingConfig**
   - Mock implementations
   - Various types

7. **EventClientInterceptor** (for testing)
   - Mock implementations
   - Test interceptor chains

---

## Priority 2: IMPORTANT MODULES

---

## 4. KATALYST-KTOR

**Module Purpose:** HTTP server setup and route configuration.

### Key Components

#### KtorModule Interface
```kotlin
interface KtorModule {
    val order: Int get() = 0
    fun install(application: Application)
}
```

**Purpose:** Pluggable components for Ktor application pipeline

#### katalystRouting DSL
```kotlin
fun Application.katalystRouting(block: Routing.() -> Unit)
fun Route.katalystRouting(block: Route.() -> Unit)
```

**Responsibilities:**
1. Verify Koin DI container is initialized
2. Log route configuration
3. Error handling and propagation

### Testing Requirements
1. **Module Discovery** - KtorModule auto-discovery and ordering
2. **Routing DSL** - katalystRouting block executes routes
3. **Koin Integration** - getKoinInstance() called and logged
4. **Error Handling** - Exceptions propagated correctly
5. **Route Registration** - Routes properly installed in Ktor

### Dependencies to Mock
- Ktor Application/Route/Routing
- Koin DI container
- getKoinInstance() function

---

## 5. KATALYST-CONFIG-PROVIDER

**Module Purpose:** Type-safe configuration loading pattern.

### Key APIs

#### ServiceConfigLoader<T> Interface
```kotlin
interface ServiceConfigLoader<T> {
    fun loadConfig(provider: ConfigProvider): T
    fun validate(config: T) {
        // Optional: override for validation
    }
}
```

**Purpose:**
- Declarative config loading
- Type-safe construction
- Separation of concerns
- Testability

### Testing Requirements
1. **Config Loading** - Extract values correctly
2. **Type Conversion** - Numbers, strings, booleans
3. **Defaults** - Apply when keys missing
4. **Validation** - Custom rules enforced
5. **Error Handling** - ConfigException on invalid

### Dependencies to Mock
- ConfigProvider (interface)
- Custom config objects

---

## 6. KATALYST-CONFIG-YAML

**Module Purpose:** YAML-based configuration with environment variable substitution.

### Key APIs

#### YamlConfigProvider
```kotlin
class YamlConfigProvider : ConfigProvider, Component {
    fun <T> get(key: String, defaultValue: T? = null): T?
    fun getString(key: String, default: String): String
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getList(key: String, default: List<String>): List<String>
    fun hasKey(key: String): Boolean
    fun getAllKeys(): Set<String>
}
```

**Features:**
- Profile-based loading (application-{profile}.yaml)
- Environment variable substitution `${VAR:default}`
- Dot-notation path navigation
- Type conversion with sensible defaults

#### Supporting Classes
- **YamlProfileLoader** - Load application.yaml and profile-specific variants
- **YamlParser** - Parse YAML to nested maps
- **EnvironmentVariableSubstitutor** - Replace ${...} patterns

### Testing Requirements
1. **Profile Loading** - application.yaml + application-{profile}.yaml
2. **Dot Notation** - database.url → correct nested value
3. **Type Conversions** - String, Int, Long, Boolean, List
4. **Environment Variables** - ${VAR:default} substitution
5. **Defaults** - Correct fallback values
6. **Error Cases** - Invalid YAML, missing keys
7. **Key Traversal** - getAllKeys() returns all paths

### Dependencies to Mock
- File I/O (getResourceAsStream)
- SnakeYAML parsing
- Environment variables

---

## 7. KATALYST-EVENTS

**Module Purpose:** Core domain event interfaces and validation.

### Key APIs

#### DomainEvent Interface
```kotlin
interface DomainEvent {
    val eventId: String get() = UUID.randomUUID().toString()
    fun getMetadata(): EventMetadata
    fun eventType(): String = getMetadata().eventType
}
```

#### EventMetadata
```kotlin
data class EventMetadata(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val correlationId: String? = null,
    val causationId: String? = null,
    val source: String? = null,
    val occurredAt: Long? = null
) {
    fun withCorrelationId(newCorrelationId: String): EventMetadata
    fun withCausationId(commandId: String): EventMetadata
    fun withSource(newSource: String): EventMetadata
    fun withOccurredAt(whenItOccurred: Long): EventMetadata
}
```

#### EventHandler<T : DomainEvent>
```kotlin
interface EventHandler<T : DomainEvent> {
    val eventType: KClass<T>  // Can be sealed parent
    suspend fun handle(event: T)
}
```

**Features:**
- Single event type handlers
- Sealed class handlers (auto-dispatch to subtypes)
- Async execution
- Exception isolation
- Component auto-discovery

#### EventValidator & EventConfiguration
- Validate events before publish
- Custom validation rules

### Testing Requirements
1. **Event Metadata** - Correct defaults and transformations
2. **EventHandler** - Proper event type matching
3. **Sealed Hierarchy** - Handler receives correct subtypes
4. **Validation** - Rules enforced

---

## 8. KATALYST-CORE

**Module Purpose:** Core framework interfaces and utilities.

### Key APIs

#### ConfigProvider Interface
```kotlin
interface ConfigProvider : Component {
    fun <T> get(key: String, defaultValue: T? = null): T?
    fun getString(key: String, default: String = ""): String
    fun getInt(key: String, default: Int = 0): Int
    fun getLong(key: String, default: Long = 0L): Long
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun getList(key: String, default: List<String> = emptyList()): List<String>
    fun hasKey(key: String): Boolean
    fun getAllKeys(): Set<String>
}
```

#### Component & Service
- Marker interfaces for DI auto-discovery
- Component: Framework components
- Service: Business logic components

#### Validation Framework
```kotlin
interface Validator<T> {
    suspend fun validate(value: T): ValidationResult
}

sealed class ValidationResult {
    object Valid
    data class Invalid(val errors: List<String>)
}
```

#### ConfigException & DependencyInjectionException
- Standard exception types

### Testing Requirements
1. **ConfigProvider Implementations** - Type conversions, defaults
2. **Validators** - Validation logic enforcement
3. **Component Discovery** - Auto-wiring via reflection

---

## CROSS-MODULE TESTING PATTERNS

### Transaction Coordination Testing
Test workflows involving multiple modules:
1. Repository saves entity
2. Event published to EventBus
3. Event serialized for external system
4. If failure: undo operation executed
5. Workflow state updated

### Retry Coordination
Multiple retry policies in interaction:
1. Repository retry policy (persistence)
2. EventClient retry policy (external publish)
3. UndoEngine retry policy (rollback)
4. All use exponential backoff but different thresholds

### Error Propagation
Failures at each layer:
1. Validation error → non-retriable failure
2. Serialization error → non-retriable failure
3. Routing error → non-retriable failure
4. External publish error → retriable failure
5. Undo error → best-effort, continues

---

## RECOMMENDED TESTING APPROACH

### Unit Tests (By Module)
1. **katalyst-persistence**: 400-500 tests
   - CRUD operations: 40-50
   - Undo strategies: 100-150
   - EnhancedUndoEngine: 50-80
   - RetryPolicy: 40-60
   - WorkflowStateRepository: 80-100
   - DatabaseFactory: 30-40

2. **katalyst-events-transport**: 150-200 tests
   - JsonEventSerializer: 60-80
   - EventMessage: 30-40
   - EventRouter/Routing: 40-60
   - Error cases: 20-40

3. **katalyst-events-client**: 200-300 tests
   - EventClient.publish(): 80-100
   - publishBatch(): 30-40
   - RetryPolicy variations: 60-80
   - Interceptors: 40-60
   - Integration with dependencies: 30-40

4. **katalyst-ktor**: 40-60 tests
5. **katalyst-config-provider**: 40-60 tests
6. **katalyst-config-yaml**: 80-100 tests
7. **katalyst-events**: 50-80 tests
8. **katalyst-core**: 50-80 tests

### Integration Tests
1. Persistence ↔ Events (undo workflow)
2. Config ↔ Services (dependency injection)
3. HTTP ↔ Events (request → event flow)

### Total Estimate
- **1000-1200 unit tests**
- **100-150 integration tests**
- **1100-1350 total tests**

