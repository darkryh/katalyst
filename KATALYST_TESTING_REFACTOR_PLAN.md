# Katalyst Testing Refactor Plan

## Executive Summary

This document outlines a comprehensive testing refactor plan for the Katalyst project. Based on detailed analysis of 23 production modules (excluding examples), the current test coverage is **8% by file count** (18/229 source files have tests). This plan aims to achieve **90%+ comprehensive test coverage** across all critical modules without modifying production code.

**Project Scope:**
- **Total Modules to Test:** 22 modules (excluding `katalyst-testing-core` and `katalyst-testing-ktor`)
- **Estimated New Tests:** 1,100-1,350 tests
- **Estimated Effort:** 8-12 weeks (1-2 developer months)
- **Priority:** HIGH - Critical infrastructure with insufficient test coverage

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Current State Assessment](#current-state-assessment)
3. [Testing Strategy](#testing-strategy)
4. [Phased Implementation Plan](#phased-implementation-plan)
5. [Module-by-Module Testing Plans](#module-by-module-testing-plans)
6. [Testing Standards and Best Practices](#testing-standards-and-best-practices)
7. [Risk Management](#risk-management)
8. [Success Criteria and Metrics](#success-criteria-and-metrics)
9. [Timeline and Milestones](#timeline-and-milestones)
10. [Appendices](#appendices)

---

## Project Overview

### Goals

1. **Achieve Comprehensive Coverage:** Ensure all critical paths, edge cases, and error scenarios are tested
2. **No Production Code Changes:** Only create/enhance test files
3. **Establish Testing Standards:** Create reusable patterns for future development
4. **Improve Reliability:** Catch bugs early and prevent regressions
5. **Enable Safe Refactoring:** Build confidence for future code improvements

### Scope

**In Scope:**
- Unit tests for all public APIs
- Integration tests for module interactions
- Edge case and error scenario testing
- Concurrent/async operation testing
- Resource management testing
- Configuration validation testing

**Out of Scope:**
- Production code modifications
- Performance/load testing (noted as future work)
- End-to-end testing (exists in example module)
- Example module testing

---

## Current State Assessment

### Test Coverage by Module

| Module | Files | Tests | Coverage | Status | Priority |
|--------|-------|-------|----------|--------|----------|
| **katalyst-persistence** | 24 | 0 | 0% | ❌ CRITICAL | P0 |
| **katalyst-transactions** | 36 | 1 | 3% | ❌ CRITICAL | P0 |
| **katalyst-events-transport** | 11 | 0 | 0% | ❌ CRITICAL | P0 |
| **katalyst-events-client** | 10 | 0 | 0% | ❌ CRITICAL | P0 |
| **katalyst-config-yaml** | 6 | 0 | 0% | ⚠️ HIGH | P1 |
| **katalyst-ktor** | 5 | 0 | 0% | ⚠️ HIGH | P1 |
| **katalyst-events** | 6 | 0 | 0% | ⚠️ HIGH | P1 |
| **katalyst-config-provider** | 4 | 0 | 0% | ⚠️ HIGH | P1 |
| **katalyst-core** | 9 | 0 | 0% | ⚠️ MEDIUM | P2 |
| **katalyst-messaging** | 8 | 0 | 0% | ⚠️ MEDIUM | P2 |
| **katalyst-di** | 17 | 1 | 6% | ⚠️ LOW | P3 |
| **katalyst-migrations** | 10 | 2 | 20% | ⚠️ LOW | P3 |
| **katalyst-websockets** | 5 | 1 | 20% | ⚠️ LOW | P3 |
| **katalyst-scheduler** | 22 | 4 | 31% | ✅ GOOD | P3 |
| **katalyst-scanner** | 22 | 5 | 23% | ✅ FAIR | P3 |
| **katalyst-events-bus** | 25 | 4 | 16% | ⚠️ LOW | P3 |
| **katalyst-ktor-netty** | 3 | 0 | 0% | ⚠️ MEDIUM | P2 |
| **katalyst-ktor-cio** | 3 | 0 | 0% | ⚠️ MEDIUM | P2 |
| **katalyst-ktor-jetty** | 3 | 0 | 0% | ⚠️ MEDIUM | P2 |
| **katalyst-messaging-amqp** | 7 | 0 | 0% | 📝 DEV | P4 |

### Key Findings

**Well-Tested Modules (Enhance):**
- `katalyst-scheduler`: 85% effective coverage - needs edge cases
- `katalyst-scanner`: 80% effective coverage - needs integration tests
- `katalyst-events-bus`: 75% effective coverage - needs concurrent testing

**Critically Undertested (Urgent):**
- `katalyst-persistence`: 0% coverage - 24 files, complex undo/rollback system
- `katalyst-transactions`: 3% coverage - 36 files, transaction coordination
- `katalyst-events-transport`: 0% coverage - event serialization critical
- `katalyst-events-client`: 0% coverage - main publish API untested

**Missing Test Categories:**
- Concurrent/async operations
- Resource exhaustion scenarios
- Partial failure handling
- State machine edge transitions
- Retry policy edge cases

---

## Testing Strategy

### Testing Pyramid

```
                    E2E Tests (Existing in Example)
                   /                                \
              Integration Tests (100-150 tests)
             /                                      \
        Unit Tests (1,000-1,200 tests)
```

### Test Types

1. **Unit Tests (Primary Focus)**
   - Test individual classes/functions in isolation
   - Mock dependencies using inline objects (project pattern)
   - Fast execution (<1s per test)
   - 1,000-1,200 new tests

2. **Integration Tests**
   - Test module interactions
   - Real database (H2 in-memory)
   - Real event bus and DI container
   - 100-150 new tests

3. **Contract Tests**
   - Verify interface implementations
   - Ensure substitutability (Liskov)
   - 50-80 tests

### Testing Tools & Frameworks

**Core Framework:**
- Kotlin Test (`kotlin.test`) - Assertions
- JUnit 5 - Test runner
- Kotlinx Coroutines Test - Async testing

**Testing Utilities (Existing):**
- `katalystTestEnvironment { }` - Full DI bootstrap
- `katalystTestApplication { }` - Ktor test host
- `inMemoryDatabaseConfig()` - H2 database
- `FakeConfigProvider` - Mock configuration

**Database:**
- H2 in-memory for unit tests
- Exposed SQL DSL
- TestContainers (optional for PostgreSQL integration)

**No External Mocking Libraries:**
- Project uses inline anonymous objects
- No Mockk, MockK, or Mockito
- Follow existing pattern for consistency

---

## Phased Implementation Plan

### Phase 0: Preparation (Week 1)
**Duration:** 3-5 days
**Effort:** 0.5 developer-weeks

**Objectives:**
- Set up testing infrastructure
- Create reusable test utilities
- Establish testing conventions

**Deliverables:**
- [ ] Shared test helper functions
- [ ] Mock object builders
- [ ] Test data factories
- [ ] Testing style guide document

**Exit Criteria:**
- Testing utilities documented
- Sample tests using utilities pass
- Team alignment on conventions

---

### Phase 1: Critical Path Coverage (Weeks 2-5)
**Duration:** 4 weeks
**Effort:** 4 developer-weeks
**Priority:** P0 - CRITICAL

#### Phase 1A: Persistence Layer (Weeks 2-3)
**Module:** `katalyst-persistence`
**Estimated Tests:** 400-500 tests

**Focus Areas:**
1. **CrudRepository Operations** (80-100 tests)
   - Basic CRUD: save, findById, findAll, delete, count
   - Batch operations: saveAll, deleteAll
   - Query filtering: equals, notEquals, greaterThan, lessThan, like, in
   - Sorting: ascending, descending, multi-field
   - Pagination: offset, limit edge cases

2. **Undo/Rollback System** (150-200 tests)
   - INSERT undo strategy (delete on undo)
   - DELETE undo strategy (re-insert on undo)
   - UPDATE undo strategy (restore previous values)
   - API_CALL undo strategy (inverse operation)
   - LIFO execution order with 10+ operations
   - Partial failure handling
   - Concurrent undo operations

3. **WorkflowStateRepository** (60-80 tests)
   - State transitions: STARTED → COMMITTED/FAILED
   - State transitions: COMMITTED → UNDONE
   - State transitions: FAILED → FAILED_UNDO
   - Invalid transition detection
   - Concurrent state updates

4. **Retry Policies** (40-60 tests)
   - ExponentialBackoffRetry (with jitter)
   - LinearBackoffRetry
   - NoRetry
   - ImmediateRetry
   - Custom retry lambda
   - Max attempts enforcement

5. **Database Factory** (30-40 tests)
   - HikariCP connection pooling
   - Connection validation
   - Pool exhaustion handling
   - Configuration validation

6. **Operation Logging** (40-60 tests)
   - Insert operation logging
   - Delete operation logging
   - Update operation logging
   - Log retrieval for undo reconstruction

**Critical Test Scenarios:**
- ✓ Undo 100 operations in LIFO order
- ✓ Partial undo failure (7/10 succeed)
- ✓ Concurrent writes to same entity
- ✓ Connection pool exhaustion (100 concurrent ops)
- ✓ Invalid state machine transitions
- ✓ Exponential backoff with jitter calculation
- ✓ QueryFilter type mismatches (sort on wrong column type)

**Exit Criteria:**
- 90%+ line coverage in persistence module
- All CRUD operations tested with edge cases
- All 4 undo strategies tested with complex scenarios
- State machine tested with all valid/invalid transitions
- Concurrent operations tested with race conditions

---

#### Phase 1B: Event System Core (Week 4)
**Modules:** `katalyst-events-transport`, `katalyst-events-client`
**Estimated Tests:** 350-500 tests

##### katalyst-events-transport (150-200 tests)

**Focus Areas:**
1. **Event Serialization** (60-80 tests)
   - JSON serialization with Jackson
   - toString fallback for non-serializable objects
   - ByteArray equality/hashCode
   - Null handling
   - Circular reference detection
   - Large payload handling (>1MB)

2. **Event Headers** (40-60 tests)
   - event-type extraction
   - event-id generation
   - correlation-id propagation
   - causation-id chaining
   - source identification
   - timestamp formatting
   - Custom headers

3. **Event Routing** (50-60 tests)
   - QUEUE routing
   - TOPIC routing
   - STREAM routing
   - Prefix-based routing
   - Package-based routing
   - Custom routing strategies
   - Invalid destination handling

**Critical Test Scenarios:**
- ✓ Serialize event with 50+ properties
- ✓ ByteArray equality edge cases
- ✓ Route to 10 different destinations simultaneously
- ✓ Missing required headers
- ✓ Invalid routing configuration

##### katalyst-events-client (200-300 tests)

**Focus Areas:**
1. **Publish Flow** (80-100 tests)
   - Single event publish
   - Batch publish (10, 100, 1000 events)
   - Publish with delivery info
   - Local-only publish
   - External-only publish
   - Both local and external
   - Neither (should error)

2. **Retry Policies** (60-80 tests)
   - NoRetry (fail immediately)
   - ExponentialBackoff (2^n with jitter)
   - LinearBackoff (constant delay)
   - Custom lambda retry
   - ImmediateRetry
   - Max attempts enforcement
   - Backoff calculation accuracy

3. **Interceptor Chain** (60-80 tests)
   - beforePublish execution order
   - afterPublish LIFO execution
   - Interceptor aborts publish
   - Interceptor modifies event
   - onPublishError handling
   - Multiple interceptors (10+)
   - Interceptor exceptions

4. **Configuration Combinations** (20-30 tests)
   - localEnabled=true, externalEnabled=true
   - localEnabled=true, externalEnabled=false
   - localEnabled=false, externalEnabled=true
   - localEnabled=false, externalEnabled=false (error)
   - Missing EventBus (when local enabled)
   - Missing transport (when external enabled)

**Critical Test Scenarios:**
- ✓ Publish 1000 events in batch with 10 failures
- ✓ Exponential backoff with jitter (verify no thundering herd)
- ✓ 20 interceptors with mixed abort/continue decisions
- ✓ Publish during high contention (1000 concurrent publishes)
- ✓ Partial batch failure handling
- ✓ Delivery info timing accuracy

**Exit Criteria:**
- 90%+ line coverage in both modules
- All 7 publish flow steps tested
- All retry policies tested with edge cases
- Interceptor chain tested with 10+ interceptors
- Batch operations tested with partial failures

---

#### Phase 1C: Transaction System (Week 5)
**Module:** `katalyst-transactions`
**Estimated Tests:** 150-200 tests (expanding from 1 existing test)

**Focus Areas:**
1. **Transaction Coordinator** (40-60 tests)
   - Begin transaction
   - Commit transaction
   - Rollback transaction
   - Nested transactions
   - Concurrent transactions
   - Transaction timeout
   - Deadlock detection

2. **Transaction Adapters** (60-80 tests)
   - Database transaction adapter
   - Event transaction adapter
   - Custom adapter registration
   - Multi-adapter coordination
   - Adapter failure handling
   - Adapter rollback

3. **Adapter Registry** (30-40 tests)
   - Register adapter
   - Unregister adapter
   - Get adapter by type
   - Duplicate registration handling
   - Missing adapter handling

4. **Transaction Context** (20-30 tests)
   - Context propagation
   - Context isolation
   - Thread-local storage
   - Coroutine context propagation

**Critical Test Scenarios:**
- ✓ 5 nested transactions with rollback at level 3
- ✓ 100 concurrent transactions
- ✓ Transaction timeout with cleanup
- ✓ Partial adapter failure (2/5 commit, 3 rollback)
- ✓ Deadlock scenario with 4 transactions

**Exit Criteria:**
- 85%+ line coverage (from 3%)
- Nested transactions tested up to 10 levels
- Concurrent transactions tested with 100+ threads
- All adapter types tested with failures

---

### Phase 2: High-Priority Modules (Weeks 6-8)
**Duration:** 3 weeks
**Effort:** 3 developer-weeks
**Priority:** P1 - HIGH

#### Phase 2A: Configuration System (Week 6)
**Modules:** `katalyst-config-yaml`, `katalyst-config-provider`
**Estimated Tests:** 120-160 tests

##### katalyst-config-yaml (80-100 tests)

**Focus Areas:**
1. **YAML Parsing** (30-40 tests)
   - Simple values (string, int, boolean)
   - Nested objects
   - Lists and arrays
   - Multi-line strings
   - Anchors and aliases
   - Invalid YAML syntax

2. **Environment Variable Substitution** (20-30 tests)
   - `${ENV_VAR}` syntax
   - `${ENV_VAR:default}` with defaults
   - Missing env vars without default (error)
   - Nested substitution
   - Escape sequences

3. **Profile-Based Loading** (20-30 tests)
   - Load `application.yaml`
   - Load `application-dev.yaml`
   - Load `application-prod.yaml`
   - Profile merging (profile overrides base)
   - Missing profile file handling

4. **Dot-Notation Navigation** (10-20 tests)
   - `database.url` → nested value
   - `server.port` → integer conversion
   - Invalid path handling
   - Array index access: `items[0].name`

**Critical Test Scenarios:**
- ✓ Load YAML with 100+ nested properties
- ✓ Substitute 50 environment variables
- ✓ Load 5 profile files with overlapping keys
- ✓ Navigate to deeply nested value (10+ levels)
- ✓ Invalid YAML syntax error messages

##### katalyst-config-provider (40-60 tests)

**Focus Areas:**
1. **ServiceConfigLoader** (20-30 tests)
   - Load type-safe config
   - Validation integration
   - Missing config handling
   - Invalid config type

2. **Config Provider Interface** (20-30 tests)
   - get(key) method
   - getOrNull(key) method
   - getOrDefault(key, default) method
   - Type conversions (String, Int, Long, Boolean, List)
   - Missing key handling

**Exit Criteria:**
- 90%+ line coverage in both modules
- All YAML features tested (anchors, multiline, etc.)
- Env var substitution tested with edge cases
- Profile merging tested with conflicts

---

#### Phase 2B: HTTP & Web Stack (Week 7)
**Modules:** `katalyst-ktor`, engine implementations
**Estimated Tests:** 100-140 tests

##### katalyst-ktor (40-60 tests)

**Focus Areas:**
1. **KtorModule Interface** (15-20 tests)
   - Module registration
   - Module ordering (priority)
   - Module initialization
   - Module error handling

2. **katalystRouting DSL** (25-40 tests)
   - Route registration
   - Koin verification enabled
   - Koin verification disabled
   - Error handling
   - Route conflict detection

**Critical Test Scenarios:**
- ✓ Register 20 modules with different priorities
- ✓ Verify Koin dependencies for 50 routes
- ✓ Handle route conflicts gracefully
- ✓ Module initialization order correctness

##### Engine Implementations (60-80 tests)

**Focus Areas (each engine: Netty, CIO, Jetty):**
1. **Engine Configuration** (10-15 tests per engine)
   - Port configuration
   - Host configuration
   - Connection limits
   - Timeout configuration

2. **Engine Lifecycle** (10-15 tests per engine)
   - Start server
   - Stop server
   - Graceful shutdown
   - Restart handling

**Exit Criteria:**
- 85%+ line coverage in ktor module
- All 3 engines tested with basic scenarios
- Module ordering tested with 10+ modules

---

#### Phase 2C: Event Domain Layer (Week 8)
**Modules:** `katalyst-events`, `katalyst-core`
**Estimated Tests:** 100-160 tests

##### katalyst-events (50-80 tests)

**Focus Areas:**
1. **DomainEvent Interface** (15-20 tests)
   - eventId generation
   - getMetadata() method
   - Event equality
   - Event serialization

2. **EventMetadata** (20-30 tests)
   - Timestamp generation
   - Correlation ID
   - Causation ID
   - Event version
   - Custom metadata

3. **EventHandler** (15-30 tests)
   - Single type handlers
   - Sealed class handlers
   - Handler registration
   - Handler exceptions

**Critical Test Scenarios:**
- ✓ Create 1000 events with unique IDs
- ✓ Metadata propagation through 10-event chain
- ✓ Handler for sealed class with 20 subtypes

##### katalyst-core (50-80 tests)

**Focus Areas:**
1. **ConfigProvider Interface** (15-20 tests)
   - Interface contract testing
   - Type conversion testing
   - Missing key handling

2. **Component/Service Markers** (15-25 tests)
   - @Service annotation scanning
   - @Component annotation scanning
   - Auto-discovery verification
   - Duplicate detection

3. **Validator Interface** (10-20 tests)
   - Validation success
   - Validation failure with messages
   - Multiple validator composition

4. **Exception Hierarchy** (10-15 tests)
   - ConfigException scenarios
   - DependencyInjectionException scenarios
   - Exception message formatting

**Exit Criteria:**
- 90%+ line coverage in both modules
- All marker interfaces tested with auto-discovery
- Event handling tested with complex sealed hierarchies

---

### Phase 3: Enhancement of Existing Tests (Week 9)
**Duration:** 1 week
**Effort:** 1 developer-week
**Priority:** P3 - MEDIUM

#### Modules to Enhance

##### katalyst-scheduler (Add 50-70 tests)
**Current:** 130 tests, 85% coverage
**Target:** 200+ tests, 95% coverage

**Additional Tests:**
- Edge cases for cron expressions
- Concurrent job execution
- Job cancellation scenarios
- Timezone edge cases (DST transitions)
- Resource cleanup on shutdown

##### katalyst-scanner (Add 40-60 tests)
**Current:** 80 tests, 80% coverage
**Target:** 140+ tests, 95% coverage

**Additional Tests:**
- Integration with actual DI container
- Large classpath scanning (1000+ classes)
- Performance testing (scan time)
- Duplicate component detection
- Circular dependency detection

##### katalyst-events-bus (Add 60-80 tests)
**Current:** 100 tests, 75% coverage
**Target:** 180+ tests, 95% coverage

**Additional Tests:**
- Concurrent publish/subscribe (1000 ops/sec)
- Event deduplication edge cases
- Handler failure recovery
- Transaction phase testing
- Memory leak prevention

##### katalyst-migrations (Add 15-20 tests)
**Current:** 5 tests, 30% coverage
**Target:** 25+ tests, 90% coverage

**Additional Tests:**
- Migration versioning conflicts
- Rollback scenarios
- Partial migration failure
- Checksum validation
- Concurrent migration detection

##### katalyst-websockets (Add 10-15 tests)
**Current:** 4 tests, 40% coverage
**Target:** 19+ tests, 90% coverage

**Additional Tests:**
- Multiple WebSocket connections
- Connection lifecycle (connect, disconnect, error)
- Message broadcasting
- Authentication integration
- Error handling

##### katalyst-di (Add 20-25 tests)
**Current:** 11 tests, 35% coverage
**Target:** 36+ tests, 90% coverage

**Additional Tests:**
- Lifecycle hook testing
- Scope testing (singleton, prototype)
- Lazy initialization
- Circular dependency handling
- Module unloading

**Exit Criteria:**
- All modules reach 90%+ coverage
- Concurrent scenarios tested for async modules
- Resource cleanup verified for all modules

---

### Phase 4: Medium-Priority Modules (Week 10)
**Duration:** 1 week
**Effort:** 1 developer-week
**Priority:** P2 - MEDIUM

#### Phase 4A: Messaging System
**Module:** `katalyst-messaging`
**Estimated Tests:** 80-120 tests

**Focus Areas:**
1. **Message Producer** (30-40 tests)
   - Send message
   - Send batch messages
   - Delivery confirmation
   - Send failures

2. **Message Consumer** (30-40 tests)
   - Receive message
   - Acknowledge message
   - Reject message
   - Requeue message

3. **Message Routing** (20-40 tests)
   - Queue binding
   - Topic binding
   - Exchange routing
   - Dead letter queues

**Exit Criteria:**
- 85%+ line coverage
- Producer/consumer tested with various scenarios
- Routing tested with complex topologies

---

### Phase 5: Integration Testing (Week 11)
**Duration:** 1 week
**Effort:** 1 developer-week
**Priority:** P1 - HIGH

**Integration Test Suites:**

1. **Persistence + Events Integration** (30-40 tests)
   - CRUD operations trigger events
   - Undo workflow publishes events
   - Event-driven state transitions
   - Transaction coordination

2. **Config + DI Integration** (20-30 tests)
   - Load config and inject into services
   - Profile-based service configuration
   - Config validation at startup
   - Hot reload configuration

3. **HTTP + Events Integration** (20-30 tests)
   - HTTP request triggers event
   - Event handler sends HTTP response
   - WebSocket event streaming
   - SSE event streaming

4. **Full Stack Scenarios** (30-50 tests)
   - HTTP → Business Logic → Database → Events
   - Scheduled Job → Database → Events
   - WebSocket → Events → Broadcast
   - Config Change → Service Restart → Validation

**Exit Criteria:**
- All module interactions tested
- Cross-module failures handled gracefully
- End-to-end scenarios pass consistently

---

### Phase 6: Finalization & Documentation (Week 12)
**Duration:** 1 week
**Effort:** 1 developer-week
**Priority:** P1 - HIGH

**Activities:**
1. **Code Coverage Analysis**
   - Run Kover reports for all modules
   - Identify remaining gaps
   - Fill critical gaps

2. **Documentation**
   - Document test architecture
   - Document test utilities
   - Document testing conventions
   - Create testing guide for new contributors

3. **CI/CD Integration**
   - Ensure all tests run in CI
   - Set up coverage reporting
   - Configure failure alerts

4. **Knowledge Transfer**
   - Present testing architecture to team
   - Code review sessions
   - Document lessons learned

**Exit Criteria:**
- 90%+ overall test coverage achieved
- All critical modules have comprehensive tests
- Testing documentation complete
- CI/CD pipeline validated

---

## Module-by-Module Testing Plans

### 1. katalyst-persistence (P0 - CRITICAL)

#### Overview
- **Files:** 24
- **Current Coverage:** 0%
- **Target Coverage:** 95%
- **Estimated Tests:** 400-500
- **Effort:** 2 weeks

#### Test Structure
```
katalyst-persistence/src/test/kotlin/
├── repository/
│   ├── CrudRepositoryTest.kt (80-100 tests)
│   ├── WorkflowStateRepositoryTest.kt (60-80 tests)
│   ├── OperationLogRepositoryTest.kt (40-60 tests)
│   └── QueryFilterTest.kt (30-40 tests)
├── undo/
│   ├── EnhancedUndoEngineTest.kt (60-80 tests)
│   ├── UndoStrategyRegistryTest.kt (20-30 tests)
│   ├── strategy/
│   │   ├── InsertUndoStrategyTest.kt (15-20 tests)
│   │   ├── DeleteUndoStrategyTest.kt (15-20 tests)
│   │   ├── UpdateUndoStrategyTest.kt (20-30 tests)
│   │   └── ApiCallUndoStrategyTest.kt (20-30 tests)
│   └── RetryPolicyTest.kt (40-60 tests)
├── database/
│   ├── DatabaseFactoryTest.kt (30-40 tests)
│   └── ConnectionPoolTest.kt (20-30 tests)
└── integration/
    └── PersistenceIntegrationTest.kt (30-40 tests)
```

#### Critical Test Scenarios

**CrudRepository Tests:**
```kotlin
// Test: Basic CRUD operations
@Test fun `save should persist entity and return with ID`()
@Test fun `findById should return entity when exists`()
@Test fun `findById should return null when not exists`()
@Test fun `findAll should return all entities`()
@Test fun `findAll with empty table should return empty list`()
@Test fun `delete should remove entity`()
@Test fun `delete non-existent entity should not throw`()
@Test fun `count should return correct number of entities`()

// Test: Batch operations
@Test fun `saveAll should persist multiple entities`()
@Test fun `saveAll with 1000 entities should succeed`()
@Test fun `deleteAll should remove all entities`()
@Test fun `deleteAll with IDs should remove only specified`()

// Test: Query filtering
@Test fun `filter equals should return matching entities`()
@Test fun `filter notEquals should return non-matching entities`()
@Test fun `filter greaterThan should return entities greater than value`()
@Test fun `filter lessThan should return entities less than value`()
@Test fun `filter like should support wildcard matching`()
@Test fun `filter in should return entities in list`()
@Test fun `filter with multiple conditions should apply AND logic`()
@Test fun `filter with wrong column type should throw exception`()

// Test: Sorting
@Test fun `sort ascending should order entities correctly`()
@Test fun `sort descending should order entities correctly`()
@Test fun `sort by multiple fields should apply precedence`()
@Test fun `sort with null values should handle gracefully`()

// Test: Pagination
@Test fun `pagination with offset and limit should return correct subset`()
@Test fun `pagination with offset beyond total should return empty`()
@Test fun `pagination with limit greater than total should return all`()
@Test fun `pagination with zero limit should return empty`()
```

**Undo System Tests:**
```kotlin
// Test: INSERT undo strategy
@Test fun `undo INSERT should delete created entity`()
@Test fun `undo INSERT with already deleted entity should not throw`()
@Test fun `undo INSERT with 100 entities should delete all in LIFO`()

// Test: DELETE undo strategy
@Test fun `undo DELETE should re-insert deleted entity`()
@Test fun `undo DELETE should restore all fields correctly`()
@Test fun `undo DELETE with duplicate key should handle gracefully`()

// Test: UPDATE undo strategy
@Test fun `undo UPDATE should restore previous values`()
@Test fun `undo UPDATE should only change modified fields`()
@Test fun `undo UPDATE with concurrent modification should detect conflict`()

// Test: API_CALL undo strategy
@Test fun `undo API_CALL should invoke inverse function`()
@Test fun `undo API_CALL with failed inverse should retry`()
@Test fun `undo API_CALL with custom compensating logic should execute`()

// Test: LIFO execution
@Test fun `undo should execute operations in LIFO order`()
@Test fun `undo 100 operations should maintain correct order`()
@Test fun `undo with mixed operation types should execute correctly`()

// Test: Partial failures
@Test fun `undo with 3 failures out of 10 should continue best-effort`()
@Test fun `undo should collect all failures and return them`()
@Test fun `undo should not stop on first failure`()

// Test: Concurrent undo
@Test fun `concurrent undo operations should not interfere`()
@Test fun `undo during active transaction should wait or fail safely`()
```

**Retry Policy Tests:**
```kotlin
// Test: ExponentialBackoffRetry
@Test fun `exponential backoff should calculate delay as 2^attempt`()
@Test fun `exponential backoff with jitter should add randomness`()
@Test fun `exponential backoff should cap at max delay`()
@Test fun `exponential backoff should stop at max attempts`()

// Test: LinearBackoffRetry
@Test fun `linear backoff should use constant delay`()
@Test fun `linear backoff should stop at max attempts`()

// Test: NoRetry
@Test fun `no retry should fail immediately`()

// Test: ImmediateRetry
@Test fun `immediate retry should not delay`()

// Test: Custom retry
@Test fun `custom retry lambda should be invoked`()
@Test fun `custom retry can implement complex logic`()
```

**WorkflowState Tests:**
```kotlin
// Test: Valid state transitions
@Test fun `transition from STARTED to COMMITTED should succeed`()
@Test fun `transition from STARTED to FAILED should succeed`()
@Test fun `transition from COMMITTED to UNDONE should succeed`()
@Test fun `transition from FAILED to FAILED_UNDO should succeed`()

// Test: Invalid state transitions
@Test fun `transition from COMMITTED to STARTED should fail`()
@Test fun `transition from UNDONE to COMMITTED should fail`()
@Test fun `transition from FAILED_UNDO to UNDONE should fail`()

// Test: Concurrent state updates
@Test fun `concurrent state updates should serialize correctly`()
@Test fun `optimistic locking should prevent lost updates`()
```

#### Test Data Builders
```kotlin
// Test entity for CRUD operations
data class TestEntity(
    val id: Long? = null,
    val name: String,
    val value: Int,
    val createdAt: Instant = Instant.now()
)

// Operation log builder
fun buildOperationLog(
    id: Long = 1L,
    operationType: OperationType = OperationType.INSERT,
    tableName: String = "test_entity",
    recordId: String = "123",
    oldValues: String? = null,
    newValues: String? = null
): OperationLog = OperationLog(...)

// Workflow state builder
fun buildWorkflowState(
    id: Long = 1L,
    state: WorkflowState = WorkflowState.STARTED,
    operations: List<OperationLog> = emptyList()
): WorkflowStateEntity = WorkflowStateEntity(...)
```

---

### 2. katalyst-events-client (P0 - CRITICAL)

#### Overview
- **Files:** 10
- **Current Coverage:** 0%
- **Target Coverage:** 95%
- **Estimated Tests:** 200-300
- **Effort:** 1.5 weeks

#### Test Structure
```
katalyst-events-client/src/test/kotlin/
├── EventClientTest.kt (80-100 tests)
├── EventClientBuilderTest.kt (20-30 tests)
├── interceptor/
│   ├── InterceptorChainTest.kt (60-80 tests)
│   └── InterceptorOrderingTest.kt (20-30 tests)
├── retry/
│   ├── RetryPolicyTest.kt (40-60 tests)
│   └── BackoffCalculationTest.kt (30-40 tests)
└── integration/
    └── EventClientIntegrationTest.kt (40-60 tests)
```

#### Critical Test Scenarios

**Publish Flow Tests:**
```kotlin
// Test: Single event publish
@Test fun `publish should execute all 7 steps in order`()
@Test fun `publish with local only should skip external steps`()
@Test fun `publish with external only should skip local step`()
@Test fun `publish with both disabled should throw exception`()

// Test: Batch publish
@Test fun `publishBatch with 10 events should publish all`()
@Test fun `publishBatch with 1000 events should handle efficiently`()
@Test fun `publishBatch with partial failures should return results`()
@Test fun `publishBatch should aggregate delivery info`()

// Test: Publish with delivery info
@Test fun `publishWithDeliveryInfo should return timing metrics`()
@Test fun `publishWithDeliveryInfo should return handler count`()
@Test fun `publishWithDeliveryInfo should track serialization time`()
```

**Interceptor Tests:**
```kotlin
// Test: beforePublish interceptors
@Test fun `beforePublish should execute in registration order`()
@Test fun `beforePublish ABORT should stop publish`()
@Test fun `beforePublish CONTINUE should proceed to next`()
@Test fun `beforePublish can modify event`()
@Test fun `beforePublish exception should propagate`()

// Test: afterPublish interceptors
@Test fun `afterPublish should execute in LIFO order`()
@Test fun `afterPublish receives original event`()
@Test fun `afterPublish exception should be logged but not propagate`()

// Test: onPublishError interceptors
@Test fun `onPublishError should execute on failure`()
@Test fun `onPublishError RETRY should retry publish`()
@Test fun `onPublishError ABORT should stop retries`()
@Test fun `onPublishError first decision wins`()

// Test: Multiple interceptors
@Test fun `10 interceptors should all execute`()
@Test fun `interceptor chain with mixed decisions should respect first abort`()
```

**Retry Policy Tests:**
```kotlin
// Test: ExponentialBackoff
@Test fun `exponential backoff should calculate 2^n delay`()
@Test fun `exponential backoff with jitter should add randomness`()
@Test fun `exponential backoff should not exceed max delay`()
@Test fun `exponential backoff should stop at max attempts`()
@Test fun `exponential backoff jitter should be between 0 and factor`()

// Test: Concurrent retries
@Test fun `1000 concurrent retries should not cause thundering herd`()
@Test fun `jitter should distribute retry times`()
```

**Configuration Tests:**
```kotlin
// Test: Builder validation
@Test fun `builder with local enabled requires EventBus`()
@Test fun `builder with external enabled requires transport`()
@Test fun `builder with both disabled should throw exception`()
@Test fun `builder with validator should use it`()

// Test: Configuration combinations
@Test fun `all 4 configuration combinations should work correctly`()
```

---

### 3. katalyst-events-transport (P0 - CRITICAL)

#### Overview
- **Files:** 11
- **Current Coverage:** 0%
- **Target Coverage:** 90%
- **Estimated Tests:** 150-200
- **Effort:** 1 week

#### Test Structure
```
katalyst-events-transport/src/test/kotlin/
├── serialization/
│   ├── JsonEventSerializerTest.kt (60-80 tests)
│   └── EventMessageTest.kt (30-40 tests)
└── routing/
    ├── EventRouterTest.kt (50-60 tests)
    └── RoutingStrategyTest.kt (30-40 tests)
```

#### Critical Test Scenarios

**Serialization Tests:**
```kotlin
// Test: JSON serialization
@Test fun `serialize simple event should produce valid JSON`()
@Test fun `serialize event with 50+ properties should succeed`()
@Test fun `serialize event with nested objects should flatten correctly`()
@Test fun `serialize event with null values should handle gracefully`()
@Test fun `serialize non-serializable event should fallback to toString`()
@Test fun `serialize event with circular reference should detect and handle`()

// Test: Event headers
@Test fun `serialize should extract event-type from class name`()
@Test fun `serialize should generate unique event-id`()
@Test fun `serialize should propagate correlation-id`()
@Test fun `serialize should chain causation-id`()
@Test fun `serialize should include timestamp`()
@Test fun `serialize should include source`()

// Test: ByteArray handling
@Test fun `ByteArray equality should compare contents`()
@Test fun `ByteArray hashCode should be stable`()
@Test fun `empty ByteArray should equal empty ByteArray`()
```

**Routing Tests:**
```kotlin
// Test: Destination types
@Test fun `route to QUEUE should use queue routing`()
@Test fun `route to TOPIC should use topic routing`()
@Test fun `route to STREAM should use stream routing`()

// Test: Routing strategies
@Test fun `prefix-based routing should prepend prefix`()
@Test fun `package-based routing should use package name`()
@Test fun `custom routing should invoke lambda`()

// Test: Error handling
@Test fun `route to invalid destination should throw exception`()
@Test fun `route with missing configuration should throw exception`()
```

---

### 4. katalyst-transactions (P0 - CRITICAL)

#### Overview
- **Files:** 36
- **Current Coverage:** 3% (1 existing test)
- **Target Coverage:** 85%
- **Estimated Tests:** 150-200
- **Effort:** 1.5 weeks

#### Test Structure
```
katalyst-transactions/src/test/kotlin/
├── coordinator/
│   ├── TransactionCoordinatorTest.kt (40-60 tests)
│   └── NestedTransactionsTest.kt (30-40 tests)
├── adapter/
│   ├── DatabaseTransactionAdapterTest.kt (30-40 tests)
│   ├── EventTransactionAdapterTest.kt (20-30 tests)
│   └── AdapterRegistryTest.kt (20-30 tests)
├── context/
│   └── TransactionContextTest.kt (20-30 tests)
└── integration/
    └── TransactionIntegrationTest.kt (30-40 tests)
```

#### Critical Test Scenarios

**Transaction Lifecycle Tests:**
```kotlin
// Test: Basic lifecycle
@Test fun `begin-commit should succeed`()
@Test fun `begin-rollback should succeed`()
@Test fun `commit without begin should throw exception`()
@Test fun `rollback without begin should throw exception`()

// Test: Nested transactions
@Test fun `nested transaction level 2 should work`()
@Test fun `nested transaction level 10 should work`()
@Test fun `rollback at level 3 should rollback to level 2`()
@Test fun `commit all levels should commit entire transaction`()

// Test: Concurrent transactions
@Test fun `100 concurrent transactions should not interfere`()
@Test fun `concurrent read-write should respect isolation`()
```

**Adapter Tests:**
```kotlin
// Test: Multi-adapter coordination
@Test fun `commit with 2 adapters should commit both`()
@Test fun `rollback with 5 adapters should rollback all`()
@Test fun `partial adapter failure should rollback all`()

// Test: Adapter registry
@Test fun `register adapter should make it available`()
@Test fun `unregister adapter should remove it`()
@Test fun `duplicate adapter registration should throw exception`()
```

---

### 5. katalyst-config-yaml (P1 - HIGH)

#### Overview
- **Files:** 6
- **Current Coverage:** 0%
- **Target Coverage:** 90%
- **Estimated Tests:** 80-100
- **Effort:** 0.75 weeks

#### Test Structure
```
katalyst-config-yaml/src/test/kotlin/
├── YamlConfigProviderTest.kt (40-50 tests)
├── EnvironmentVariableSubstitutionTest.kt (20-30 tests)
└── ProfileLoadingTest.kt (20-30 tests)
```

#### Critical Test Scenarios

**YAML Parsing Tests:**
```kotlin
// Test: Basic value types
@Test fun `get string value should return correct value`()
@Test fun `get int value should convert correctly`()
@Test fun `get long value should convert correctly`()
@Test fun `get boolean value should convert correctly`()
@Test fun `get list value should return list`()

// Test: Nested objects
@Test fun `get nested value using dot notation should work`()
@Test fun `get deeply nested value 10 levels should work`()
@Test fun `get nested list item should work`()
@Test fun `get nested object property should work`()

// Test: Complex YAML features
@Test fun `parse YAML with anchors and aliases should work`()
@Test fun `parse YAML with multi-line strings should preserve formatting`()
@Test fun `parse YAML with inline collections should work`()
@Test fun `parse invalid YAML syntax should throw exception`()
@Test fun `parse YAML with 100+ properties should succeed`()
```

**Environment Variable Substitution Tests:**
```kotlin
// Test: Basic substitution
@Test fun `substitute existing env var should work`()
@Test fun `substitute with default value should use default when missing`()
@Test fun `substitute without default should throw when missing`()
@Test fun `substitute multiple env vars in one value should work`()

// Test: Edge cases
@Test fun `substitute with empty env var should use empty string`()
@Test fun `substitute with special characters should work`()
@Test fun `substitute nested env vars should work`()
@Test fun `substitute 50 env vars should work efficiently`()
@Test fun `escape sequence for literal dollar sign should work`()

// Test: Error handling
@Test fun `missing env var without default should throw ConfigException`()
@Test fun `malformed substitution syntax should throw exception`()
```

**Profile Loading Tests:**
```kotlin
// Test: Profile file loading
@Test fun `load application yaml should succeed`()
@Test fun `load application-dev yaml should override base values`()
@Test fun `load application-prod yaml should override base values`()
@Test fun `load with non-existent profile should use base only`()

// Test: Profile merging
@Test fun `profile should override base values`()
@Test fun `profile should add new values`()
@Test fun `profile should merge nested objects`()
@Test fun `load 5 profiles should apply in order`()

// Test: Priority
@Test fun `later profile should override earlier profile`()
@Test fun `env var substitution should happen after merge`()
```

**Dot Notation Navigation Tests:**
```kotlin
// Test: Path navigation
@Test fun `navigate to top level property should work`()
@Test fun `navigate to nested property should work`()
@Test fun `navigate to array index should work`()
@Test fun `navigate to property in array element should work`()

// Test: Error cases
@Test fun `navigate to non-existent path should return null`()
@Test fun `navigate to invalid array index should return null`()
@Test fun `navigate with empty path should throw exception`()
```

**Type Conversion Tests:**
```kotlin
// Test: Conversions
@Test fun `convert string to int should work`()
@Test fun `convert string to long should work`()
@Test fun `convert string to boolean should handle true false yes no`()
@Test fun `convert to list should work`()
@Test fun `convert invalid string to int should throw exception`()
```

#### Test Data Examples
```kotlin
// Sample YAML content
val sampleYaml = """
    server:
      port: 8080
      host: localhost
    database:
      url: jdbc:postgresql://\${DB_HOST:localhost}:5432/\${DB_NAME}
      username: \${DB_USER}
      password: \${DB_PASSWORD:default}
    features:
      - name: feature1
        enabled: true
      - name: feature2
        enabled: false
""".trimIndent()

// Environment variables for tests
val testEnvVars = mapOf(
    "DB_HOST" to "testhost",
    "DB_NAME" to "testdb",
    "DB_USER" to "testuser"
)
```

---

### 6. katalyst-config-provider (P1 - HIGH)

#### Overview
- **Files:** 4
- **Current Coverage:** 0%
- **Target Coverage:** 90%
- **Estimated Tests:** 40-60
- **Effort:** 0.5 weeks

#### Test Structure
```
katalyst-config-provider/src/test/kotlin/
├── ConfigProviderTest.kt (20-30 tests)
└── ServiceConfigLoaderTest.kt (20-30 tests)
```

#### Critical Test Scenarios

**ConfigProvider Interface Tests:**
```kotlin
// Test: Basic retrieval
@Test fun `get existing key should return value`()
@Test fun `get non-existent key should throw ConfigException`()
@Test fun `getOrNull existing key should return value`()
@Test fun `getOrNull non-existent key should return null`()
@Test fun `getOrDefault existing key should return value`()
@Test fun `getOrDefault non-existent key should return default`()

// Test: Type conversions
@Test fun `get as String should work`()
@Test fun `get as Int should convert correctly`()
@Test fun `get as Long should convert correctly`()
@Test fun `get as Boolean should convert correctly`()
@Test fun `get as List should split comma-separated values`()
@Test fun `get as Int with invalid value should throw exception`()

// Test: Null handling
@Test fun `get with null value should throw or return based on method`()
@Test fun `getOrNull with null value should return null`()
@Test fun `getOrDefault with null value should return default`()
```

**ServiceConfigLoader Tests:**
```kotlin
// Test: Type-safe loading
@Test fun `load valid config should return typed object`()
@Test fun `load with missing required field should throw exception`()
@Test fun `load with invalid type should throw exception`()
@Test fun `load with defaults should use default values`()

// Test: Validation integration
@Test fun `load with validator should validate config`()
@Test fun `load with invalid config should throw validation exception`()
@Test fun `load with custom validator should use it`()

// Test: Complex configs
@Test fun `load nested config object should work`()
@Test fun `load config with lists should work`()
@Test fun `load config with maps should work`()

// Test: Error messages
@Test fun `loading error should provide clear message with path`()
@Test fun `validation error should include field name`()
@Test fun `type mismatch should show expected and actual types`()
```

**Integration Tests:**
```kotlin
// Test: Full integration
@Test fun `load config from YAML provider should work`()
@Test fun `load config with env var substitution should work`()
@Test fun `load multiple service configs should work`()
@Test fun `reload config should update values`()
```

#### Test Data Examples
```kotlin
// Sample service config
data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val connectionTimeout: Long = 5000
)

data class ServerConfig(
    val port: Int,
    val host: String,
    val ssl: SslConfig?
)

data class SslConfig(
    val enabled: Boolean,
    val keyStore: String,
    val keyStorePassword: String
)
```

---

### 7. katalyst-ktor (P1 - HIGH)

#### Overview
- **Files:** 5
- **Current Coverage:** 0%
- **Target Coverage:** 85%
- **Estimated Tests:** 40-60
- **Effort:** 0.5 weeks

#### Test Structure
```
katalyst-ktor/src/test/kotlin/
├── KtorModuleTest.kt (15-20 tests)
├── KatalystRoutingTest.kt (25-40 tests)
└── integration/
    └── KtorIntegrationTest.kt (10-15 tests)
```

#### Critical Test Scenarios

**KtorModule Interface Tests:**
```kotlin
// Test: Module registration
@Test fun `register module should add to registry`()
@Test fun `register duplicate module should throw exception`()
@Test fun `unregister module should remove from registry`()
@Test fun `get module by type should return module`()

// Test: Module ordering
@Test fun `modules should initialize in priority order`()
@Test fun `modules with same priority should use registration order`()
@Test fun `module with higher priority should initialize first`()
@Test fun `register 20 modules should maintain correct order`()

// Test: Module lifecycle
@Test fun `module initialize should be called`()
@Test fun `module configure should be called with Application`()
@Test fun `module initialize error should propagate`()
@Test fun `module configure error should propagate`()
```

**katalystRouting DSL Tests:**
```kotlin
// Test: Basic routing
@Test fun `define GET route should register route`()
@Test fun `define POST route should register route`()
@Test fun `define PUT route should register route`()
@Test fun `define DELETE route should register route`()
@Test fun `define route with path parameters should work`()

// Test: Koin verification
@Test fun `koin verification enabled should check dependencies`()
@Test fun `koin verification disabled should skip checks`()
@Test fun `missing dependency should throw when verification enabled`()
@Test fun `verify 50 routes with dependencies should succeed`()

// Test: Error handling
@Test fun `route handler exception should be caught`()
@Test fun `route handler should support custom error handling`()
@Test fun `multiple routes to same path should throw conflict error`()

// Test: Route grouping
@Test fun `route group should apply prefix to all routes`()
@Test fun `nested route groups should concatenate prefixes`()
@Test fun `route group should apply middleware to all routes`()
```

**Integration Tests:**
```kotlin
// Test: Full application
@Test fun `start Ktor application should succeed`()
@Test fun `make HTTP request to route should return response`()
@Test fun `make 100 concurrent requests should succeed`()
@Test fun `shutdown application should cleanup resources`()

// Test: Module integration
@Test fun `register authentication module should secure routes`()
@Test fun `register serialization module should serialize responses`()
@Test fun `register CORS module should add CORS headers`()
```

#### Test Data Examples
```kotlin
// Sample Ktor module
class TestKtorModule : KtorModule {
    override val priority: Int = 100

    override fun Application.configure() {
        routing {
            get("/test") {
                call.respondText("Test response")
            }
        }
    }
}

// Sample route with DI
fun Route.testRoute() {
    val service: TestService by inject()

    get("/api/test") {
        val result = service.doSomething()
        call.respond(result)
    }
}
```

---

### 8. katalyst-events (P1 - HIGH)

#### Overview
- **Files:** 6
- **Current Coverage:** 0%
- **Target Coverage:** 90%
- **Estimated Tests:** 50-80
- **Effort:** 0.5 weeks

#### Test Structure
```
katalyst-events/src/test/kotlin/
├── DomainEventTest.kt (15-20 tests)
├── EventMetadataTest.kt (20-30 tests)
├── EventHandlerTest.kt (15-30 tests)
└── EventValidatorTest.kt (10-15 tests)
```

#### Critical Test Scenarios

**DomainEvent Interface Tests:**
```kotlin
// Test: Event ID generation
@Test fun `eventId should be unique for each event`()
@Test fun `create 1000 events should have unique IDs`()
@Test fun `eventId should be stable for same event instance`()

// Test: Metadata
@Test fun `getMetadata should return event metadata`()
@Test fun `metadata should include timestamp`()
@Test fun `metadata should include event type`()
@Test fun `custom metadata should be included`()

// Test: Equality
@Test fun `events with same ID should be equal`()
@Test fun `events with different IDs should not be equal`()
@Test fun `event hashCode should be stable`()
```

**EventMetadata Tests:**
```kotlin
// Test: Timestamp generation
@Test fun `timestamp should be set on creation`()
@Test fun `timestamp should be in ISO format`()
@Test fun `timestamp should include timezone`()

// Test: Correlation ID
@Test fun `correlation ID should propagate through event chain`()
@Test fun `new correlation ID should be generated if not provided`()
@Test fun `correlation ID should link related events`()

// Test: Causation ID
@Test fun `causation ID should point to parent event`()
@Test fun `causation ID chain should track event lineage`()
@Test fun `event chain of 10 should maintain causation links`()

// Test: Event version
@Test fun `event version should default to 1`()
@Test fun `event version should be customizable`()
@Test fun `event version should support schema evolution`()

// Test: Custom metadata
@Test fun `add custom metadata key-value should work`()
@Test fun `retrieve custom metadata should work`()
@Test fun `custom metadata should be serializable`()
@Test fun `add 50 custom metadata fields should work`()
```

**EventHandler Tests:**
```kotlin
// Test: Single type handler
@Test fun `handler should handle specific event type`()
@Test fun `handler should not handle different event type`()
@Test fun `handler can access event properties`()

// Test: Sealed class handler
@Test fun `handler should handle sealed class events`()
@Test fun `handler should handle all subtypes`()
@Test fun `handler should dispatch to correct subtype`()
@Test fun `sealed hierarchy with 20 subtypes should work`()

// Test: Handler registration
@Test fun `register handler should make it available`()
@Test fun `unregister handler should remove it`()
@Test fun `register duplicate handler should throw or override`()

// Test: Handler exceptions
@Test fun `handler exception should be caught`()
@Test fun `handler exception should not affect other handlers`()
@Test fun `handler can implement retry logic`()
```

**EventValidator Tests:**
```kotlin
// Test: Validation success
@Test fun `valid event should pass validation`()
@Test fun `validator should return success result`()

// Test: Validation failure
@Test fun `invalid event should fail validation`()
@Test fun `validator should return error messages`()
@Test fun `multiple validation errors should all be returned`()

// Test: Validator composition
@Test fun `compose 5 validators should apply all`()
@Test fun `validator chain should stop on first error or collect all`()

// Test: Custom validators
@Test fun `custom validator implementation should work`()
@Test fun `validator can check business rules`()
```

#### Test Data Examples
```kotlin
// Sample domain events
sealed class OrderEvent : DomainEvent {
    abstract val orderId: String

    data class OrderCreated(
        override val orderId: String,
        val customerId: String,
        val items: List<OrderItem>
    ) : OrderEvent()

    data class OrderPaid(
        override val orderId: String,
        val amount: BigDecimal
    ) : OrderEvent()

    data class OrderShipped(
        override val orderId: String,
        val trackingNumber: String
    ) : OrderEvent()
}

// Sample event handler
class OrderEventHandler : EventHandler<OrderEvent> {
    override suspend fun handle(event: OrderEvent) {
        when (event) {
            is OrderEvent.OrderCreated -> handleOrderCreated(event)
            is OrderEvent.OrderPaid -> handleOrderPaid(event)
            is OrderEvent.OrderShipped -> handleOrderShipped(event)
        }
    }
}
```

---

### 9. katalyst-core (P2 - MEDIUM)

#### Overview
- **Files:** 9
- **Current Coverage:** 0%
- **Target Coverage:** 90%
- **Estimated Tests:** 50-80
- **Effort:** 0.5 weeks

#### Test Structure
```
katalyst-core/src/test/kotlin/
├── ConfigProviderInterfaceTest.kt (15-20 tests)
├── ComponentMarkerTest.kt (15-25 tests)
├── ValidatorTest.kt (10-20 tests)
└── exception/
    └── ExceptionHierarchyTest.kt (10-15 tests)
```

#### Critical Test Scenarios

**ConfigProvider Interface Tests:**
```kotlin
// Test: Contract verification
@Test fun `ConfigProvider implementation should honor contract`()
@Test fun `get with existing key should return value`()
@Test fun `get with non-existent key should throw`()
@Test fun `getOrNull should return null for missing keys`()
@Test fun `getOrDefault should return default for missing keys`()

// Test: Type conversions
@Test fun `get should support type conversion`()
@Test fun `invalid type conversion should throw`()
@Test fun `conversion to custom types should work with deserializer`()
```

**Component/Service Marker Tests:**
```kotlin
// Test: @Service annotation
@Test fun `@Service annotated class should be discoverable`()
@Test fun `scan should find all @Service classes`()
@Test fun `@Service class should be registered in DI`()

// Test: @Component annotation
@Test fun `@Component annotated class should be discoverable`()
@Test fun `scan should find all @Component classes`()
@Test fun `@Component class should be registered in DI`()

// Test: Auto-discovery
@Test fun `scan classpath should find all markers`()
@Test fun `scan with 100 components should succeed`()
@Test fun `scan should handle duplicate names`()
@Test fun `scan should detect circular dependencies`()

// Test: Integration with DI
@Test fun `discovered components should be injectable`()
@Test fun `component lifecycle should be managed by DI`()
@Test fun `singleton components should have single instance`()
```

**Validator Interface Tests:**
```kotlin
// Test: Validation contract
@Test fun `Validator implementation should honor contract`()
@Test fun `validate valid object should return success`()
@Test fun `validate invalid object should return failure`()

// Test: Validation messages
@Test fun `validation failure should include error messages`()
@Test fun `validation failure should include field names`()
@Test fun `multiple errors should all be returned`()

// Test: Validator composition
@Test fun `combine validators with AND logic should work`()
@Test fun `combine validators with OR logic should work`()
@Test fun `nested validators should work`()
@Test fun `validator chain of 10 should work`()

// Test: Custom validators
@Test fun `custom validator should be usable`()
@Test fun `validator can implement complex business rules`()
@Test fun `validator can access external services`()
```

**Exception Hierarchy Tests:**
```kotlin
// Test: ConfigException
@Test fun `ConfigException with message should create exception`()
@Test fun `ConfigException with cause should wrap exception`()
@Test fun `ConfigException message should be descriptive`()

// Test: DependencyInjectionException
@Test fun `DependencyInjectionException for missing dependency`()
@Test fun `DependencyInjectionException for circular dependency`()
@Test fun `DependencyInjectionException message should include type info`()

// Test: Exception inheritance
@Test fun `all exceptions should extend base exception`()
@Test fun `exception hierarchy should be catchable at right level`()
```

#### Test Data Examples
```kotlin
// Sample service
@Service
class TestService {
    fun doSomething(): String = "result"
}

// Sample component
@Component
class TestComponent(
    private val service: TestService
) {
    fun process(): String = service.doSomething()
}

// Sample validator
class EmailValidator : Validator<String> {
    override fun validate(value: String): ValidationResult {
        return if (value.contains("@")) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(listOf("Invalid email format"))
        }
    }
}
```

---

### 10. katalyst-messaging (P2 - MEDIUM)

#### Overview
- **Files:** 8
- **Current Coverage:** 0%
- **Target Coverage:** 85%
- **Estimated Tests:** 80-120
- **Effort:** 1 week

#### Test Structure
```
katalyst-messaging/src/test/kotlin/
├── producer/
│   ├── MessageProducerTest.kt (30-40 tests)
│   └── BatchProducerTest.kt (20-30 tests)
├── consumer/
│   ├── MessageConsumerTest.kt (30-40 tests)
│   └── MessageAcknowledgementTest.kt (15-20 tests)
└── routing/
    └── MessageRoutingTest.kt (20-40 tests)
```

#### Critical Test Scenarios

**Message Producer Tests:**
```kotlin
// Test: Send message
@Test fun `send message should publish to queue`()
@Test fun `send message should return confirmation`()
@Test fun `send message with routing key should route correctly`()
@Test fun `send message to exchange should work`()

// Test: Batch sending
@Test fun `send batch of 10 messages should succeed`()
@Test fun `send batch of 1000 messages should succeed`()
@Test fun `send batch with partial failure should return results`()

// Test: Delivery confirmation
@Test fun `message delivered should trigger confirmation`()
@Test fun `message failed should trigger error callback`()
@Test fun `delivery timeout should be detected`()

// Test: Error handling
@Test fun `send to non-existent queue should throw exception`()
@Test fun `send with invalid message should throw exception`()
@Test fun `connection loss during send should retry or fail`()

// Test: Message properties
@Test fun `set message headers should work`()
@Test fun `set message priority should work`()
@Test fun `set message expiration should work`()
@Test fun `set message correlation ID should work`()
```

**Message Consumer Tests:**
```kotlin
// Test: Receive message
@Test fun `consume message from queue should work`()
@Test fun `consume multiple messages should work`()
@Test fun `consume with no messages should wait or timeout`()

// Test: Acknowledgement
@Test fun `acknowledge message should remove from queue`()
@Test fun `reject message should requeue or dead letter`()
@Test fun `nack message should requeue`()
@Test fun `auto-acknowledge should ack automatically`()

// Test: Message handlers
@Test fun `register handler should process messages`()
@Test fun `handler exception should nack message`()
@Test fun `handler can access message properties`()
@Test fun `multiple handlers should all receive message (topic)`()

// Test: Concurrent consumption
@Test fun `10 concurrent consumers should work`()
@Test fun `consumer should handle high message rate`()
@Test fun `consumer backpressure should work`()
```

**Message Routing Tests:**
```kotlin
// Test: Queue binding
@Test fun `bind queue to exchange should work`()
@Test fun `unbind queue should stop message flow`()
@Test fun `bind with routing key should filter messages`()

// Test: Topic routing
@Test fun `publish to topic should deliver to all subscribers`()
@Test fun `topic pattern matching should work`()
@Test fun `topic with wildcards should match correctly`()

// Test: Exchange types
@Test fun `direct exchange should route by exact match`()
@Test fun `topic exchange should route by pattern`()
@Test fun `fanout exchange should broadcast to all`()
@Test fun `headers exchange should route by headers`()

// Test: Dead letter queue
@Test fun `rejected message should go to DLQ`()
@Test fun `expired message should go to DLQ`()
@Test fun `max retries exceeded should go to DLQ`()
```

#### Test Data Examples
```kotlin
// Sample message
data class OrderMessage(
    val orderId: String,
    val customerId: String,
    val items: List<OrderItem>,
    val total: BigDecimal
)

// Sample producer
class OrderMessageProducer(
    private val producer: MessageProducer
) {
    suspend fun sendOrder(order: OrderMessage) {
        producer.send(
            queue = "orders",
            message = order,
            headers = mapOf(
                "order-id" to order.orderId,
                "customer-id" to order.customerId
            )
        )
    }
}

// Sample consumer
class OrderMessageConsumer(
    private val consumer: MessageConsumer
) {
    suspend fun start() {
        consumer.consume<OrderMessage>("orders") { message ->
            processOrder(message)
            Acknowledgement.ACK
        }
    }

    private suspend fun processOrder(order: OrderMessage) {
        // Process order
    }
}
```

---

## Testing Standards and Best Practices

### Test Naming Convention

**Pattern:** `` `method/scenario should expectedBehavior when condition` ``

**Examples:**
```kotlin
@Test fun `save should persist entity and return with ID`()
@Test fun `findById should return null when entity does not exist`()
@Test fun `undo should execute operations in LIFO order`()
@Test fun `publish should throw exception when both local and external disabled`()
```

### Test Structure (Given-When-Then)

```kotlin
@Test
fun `exponential backoff should calculate delay as 2^attempt`() {
    // Given
    val policy = ExponentialBackoffRetry(
        baseDelay = 100.milliseconds,
        maxAttempts = 5,
        maxDelay = 10.seconds
    )

    // When
    val delay1 = policy.calculateDelay(attempt = 1)
    val delay2 = policy.calculateDelay(attempt = 2)
    val delay3 = policy.calculateDelay(attempt = 3)

    // Then
    assertEquals(100.milliseconds, delay1)
    assertEquals(200.milliseconds, delay2)
    assertEquals(400.milliseconds, delay3)
}
```

### Mock Objects (Inline Anonymous Objects)

**Project Pattern - No Mockk:**
```kotlin
// Create mock using anonymous object
val mockEventBus = object : EventBus {
    val publishedEvents = mutableListOf<DomainEvent>()

    override suspend fun publish(event: DomainEvent) {
        publishedEvents.add(event)
    }

    override suspend fun <T : DomainEvent> subscribe(
        eventType: KClass<T>,
        handler: EventHandler<T>
    ) {
        // No-op for this test
    }
}

// Use in test
eventClient.publish(testEvent)
assertEquals(1, mockEventBus.publishedEvents.size)
```

### Test Data Builders

```kotlin
// Use data class copy for variations
data class TestUser(
    val id: Long = 1L,
    val name: String = "Test User",
    val email: String = "test@example.com",
    val active: Boolean = true
)

// In tests
val user = TestUser()
val inactiveUser = user.copy(active = false)
val differentUser = user.copy(id = 2L, name = "Different User")
```

### Async Testing

```kotlin
@Test
fun `publish should complete within timeout`() = runTest {
    // runTest provides TestScope with virtual time
    val client = EventClient.builder()
        .localEnabled(true)
        .eventBus(mockEventBus)
        .build()

    // Test suspending function
    client.publish(testEvent)

    // Advance virtual time if needed
    advanceTimeBy(1000.milliseconds)

    // Assert
    assertEquals(1, mockEventBus.publishedEvents.size)
}
```

### Database Testing

```kotlin
@Test
fun `save should persist entity to database`() = runTest {
    // Use in-memory H2 database
    val database = inMemoryDatabaseConfig()

    transaction(database) {
        // Create schema
        SchemaUtils.create(TestEntityTable)

        // Test
        val repository = TestEntityRepository(database)
        val entity = TestEntity(name = "Test", value = 42)
        val saved = repository.save(entity)

        // Assert
        assertNotNull(saved.id)
        assertEquals("Test", saved.name)

        // Cleanup
        SchemaUtils.drop(TestEntityTable)
    }
}
```

### Testing Utilities to Create

```kotlin
// Test coroutine dispatcher
val testDispatcher = StandardTestDispatcher()

// Test database configuration
fun testDatabase(): Database {
    return inMemoryDatabaseConfig()
}

// Test event builder
fun testEvent(
    id: String = UUID.randomUUID().toString(),
    timestamp: Instant = Instant.now()
): TestDomainEvent = TestDomainEvent(id, timestamp)

// Mock config provider
class TestConfigProvider(
    private val values: Map<String, String> = emptyMap()
) : ConfigProvider {
    override fun get(key: String): String =
        values[key] ?: throw ConfigException("Key not found: $key")
}

// Assertion helpers
fun assertThrowsWithMessage(
    expectedMessage: String,
    block: () -> Unit
) {
    val exception = assertFailsWith<Exception> { block() }
    assertTrue(exception.message?.contains(expectedMessage) == true)
}
```

### Code Coverage Standards

**Minimum Coverage Targets:**
- **Overall Project:** 90%
- **Critical Modules (P0):** 95%
- **High Priority Modules (P1):** 90%
- **Medium Priority Modules (P2):** 85%
- **Enhancement Modules (P3):** 90%

**What to Cover:**
- ✅ All public APIs
- ✅ All error paths and exceptions
- ✅ All conditional branches
- ✅ Edge cases (null, empty, boundary values)
- ✅ Concurrent scenarios (for async code)
- ✅ Resource cleanup (close, dispose, shutdown)

**What NOT to Cover:**
- ❌ Generated code (data class methods)
- ❌ Simple getters/setters
- ❌ Trivial delegation
- ❌ Framework boilerplate

---

## Risk Management

### Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Tests reveal production bugs** | HIGH | HIGH | Document bugs, create issues, prioritize fixes separately |
| **Insufficient test isolation** | MEDIUM | MEDIUM | Use in-memory databases, mock external dependencies |
| **Slow test execution** | MEDIUM | MEDIUM | Parallelize tests, use test sharding, optimize setup/teardown |
| **Flaky tests** | MEDIUM | HIGH | Avoid time-based tests, use virtual time, proper cleanup |
| **Mock complexity** | MEDIUM | MEDIUM | Keep mocks simple, use builders, document patterns |

### Project Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Scope creep** | MEDIUM | HIGH | Stick to test-only changes, defer production fixes |
| **Timeline slippage** | MEDIUM | MEDIUM | Phase-based approach, prioritize critical modules |
| **Knowledge gaps** | LOW | MEDIUM | Document patterns, code reviews, pair programming |
| **Testing framework changes** | LOW | HIGH | Stick to existing JUnit 5 + Kotlin Test |
| **Merge conflicts** | LOW | LOW | Frequent commits, communicate with team |

### Mitigation Strategies

1. **Bug Discovery Protocol:**
   - Document all bugs found during testing
   - Create GitHub issues with `bug` label
   - Do NOT fix production bugs in this project
   - Prioritize bug fixes separately

2. **Test Stability:**
   - Use deterministic test data
   - Avoid real time (`Instant.now()` → use fixed time)
   - Proper resource cleanup in `@AfterEach`
   - Avoid shared mutable state

3. **Performance:**
   - Run tests in parallel (`junit.jupiter.execution.parallel.enabled = true`)
   - Use in-memory databases (H2)
   - Mock external services
   - Profile slow tests and optimize

4. **Maintenance:**
   - Document test utilities
   - Reusable test builders
   - Consistent naming conventions
   - Clear test structure (Given-When-Then)

---

## Success Criteria and Metrics

### Quantitative Metrics

**Code Coverage:**
- [ ] Overall project coverage: 90%+
- [ ] katalyst-persistence: 95%+
- [ ] katalyst-events-client: 95%+
- [ ] katalyst-events-transport: 90%+
- [ ] katalyst-transactions: 85%+
- [ ] All P1 modules: 90%+
- [ ] All P2 modules: 85%+

**Test Count:**
- [ ] Total new tests: 1,100-1,350
- [ ] Unit tests: 1,000-1,200
- [ ] Integration tests: 100-150

**Test Execution:**
- [ ] All tests pass consistently
- [ ] Test suite execution time: <5 minutes
- [ ] Zero flaky tests
- [ ] CI/CD integration complete

### Qualitative Metrics

**Code Quality:**
- [ ] All tests follow naming conventions
- [ ] All tests use Given-When-Then structure
- [ ] Test utilities documented
- [ ] No code duplication in tests

**Documentation:**
- [ ] Testing architecture documented
- [ ] Test utilities documented
- [ ] Testing conventions guide created
- [ ] Contribution guide updated

**Team Enablement:**
- [ ] Team trained on testing patterns
- [ ] Code review process established
- [ ] CI/CD alerts configured
- [ ] Knowledge transfer complete

### Exit Criteria (Per Phase)

**Phase 0 - Preparation:**
- Testing utilities created and documented
- Team aligned on conventions
- Sample tests passing

**Phase 1 - Critical Path:**
- katalyst-persistence: 95%+ coverage
- katalyst-events-transport: 90%+ coverage
- katalyst-events-client: 95%+ coverage
- katalyst-transactions: 85%+ coverage

**Phase 2 - High Priority:**
- katalyst-config-yaml: 90%+ coverage
- katalyst-config-provider: 90%+ coverage
- katalyst-ktor: 85%+ coverage
- katalyst-events: 90%+ coverage

**Phase 3 - Enhancement:**
- katalyst-scheduler: 95%+ coverage
- katalyst-scanner: 95%+ coverage
- katalyst-events-bus: 95%+ coverage
- katalyst-migrations: 90%+ coverage
- katalyst-websockets: 90%+ coverage
- katalyst-di: 90%+ coverage

**Phase 4 - Medium Priority:**
- katalyst-messaging: 85%+ coverage
- katalyst-core: 90%+ coverage

**Phase 5 - Integration:**
- All integration test suites passing
- Cross-module interactions tested

**Phase 6 - Finalization:**
- Overall 90%+ coverage achieved
- Documentation complete
- CI/CD validated
- Knowledge transfer done

---

## Timeline and Milestones

### Gantt Chart Overview

```
Week 1:  [Phase 0: Preparation]
Week 2:  [Phase 1A: Persistence - Part 1]
Week 3:  [Phase 1A: Persistence - Part 2]
Week 4:  [Phase 1B: Events Transport/Client]
Week 5:  [Phase 1C: Transactions]
Week 6:  [Phase 2A: Configuration]
Week 7:  [Phase 2B: HTTP/Ktor]
Week 8:  [Phase 2C: Events/Core]
Week 9:  [Phase 3: Enhancement]
Week 10: [Phase 4: Messaging]
Week 11: [Phase 5: Integration]
Week 12: [Phase 6: Finalization]
```

### Milestones

| Milestone | Week | Deliverable | Success Metric |
|-----------|------|-------------|----------------|
| **M0: Kickoff** | 1 | Testing utilities & conventions | Sample tests passing |
| **M1: Critical Path - Persistence** | 3 | 400-500 persistence tests | 95%+ coverage |
| **M2: Critical Path - Events** | 4 | 350-500 event tests | 90-95% coverage |
| **M3: Critical Path - Transactions** | 5 | 150-200 transaction tests | 85%+ coverage |
| **M4: High Priority Complete** | 8 | 300-400 tests for config/HTTP/events | 90%+ coverage |
| **M5: Enhancements Complete** | 9 | 200+ enhancement tests | 95%+ coverage |
| **M6: Integration Complete** | 11 | 100-150 integration tests | All scenarios passing |
| **M7: Project Complete** | 12 | Documentation & CI/CD | 90%+ overall coverage |

### Weekly Breakdown

#### Week 1: Preparation
- **Days 1-2:** Set up test structure, create utilities
- **Days 3-4:** Write sample tests, validate approach
- **Day 5:** Team alignment, documentation

#### Week 2: Persistence - Part 1
- **Days 1-2:** CrudRepository tests (80-100 tests)
- **Days 3-4:** QueryFilter and sorting tests (60-80 tests)
- **Day 5:** Review and refinement

#### Week 3: Persistence - Part 2
- **Days 1-3:** Undo system tests (150-200 tests)
- **Days 4-5:** WorkflowState and integration tests (90-120 tests)

#### Week 4: Events Transport/Client
- **Days 1-2:** Events-transport tests (150-200 tests)
- **Days 3-5:** Events-client tests (200-300 tests)

#### Week 5: Transactions
- **Days 1-3:** Transaction coordinator and adapters (100-140 tests)
- **Days 4-5:** Integration tests (50-60 tests)

#### Week 6: Configuration
- **Days 1-3:** YAML config tests (80-100 tests)
- **Days 4-5:** Config provider tests (40-60 tests)

#### Week 7: HTTP/Ktor
- **Days 1-3:** Ktor module tests (40-60 tests)
- **Days 4-5:** Engine tests (60-80 tests)

#### Week 8: Events/Core
- **Days 1-3:** Events domain tests (50-80 tests)
- **Days 4-5:** Core module tests (50-80 tests)

#### Week 9: Enhancements
- **Days 1-5:** Enhance 6 existing modules (200+ tests)

#### Week 10: Messaging
- **Days 1-5:** Messaging module tests (80-120 tests)

#### Week 11: Integration
- **Days 1-5:** Integration test suites (100-150 tests)

#### Week 12: Finalization
- **Days 1-2:** Coverage analysis, gap filling
- **Days 3-4:** Documentation
- **Day 5:** Knowledge transfer, wrap-up

---

## Appendices

### Appendix A: Test File Locations

All test files follow the standard Maven/Gradle structure:
```
<module>/src/test/kotlin/<package>/
```

Example:
```
katalyst-persistence/
├── src/
│   ├── main/kotlin/io/github/sanyavertolet/persistence/
│   └── test/kotlin/io/github/sanyavertolet/persistence/
│       ├── repository/
│       │   ├── CrudRepositoryTest.kt
│       │   └── WorkflowStateRepositoryTest.kt
│       └── undo/
│           ├── EnhancedUndoEngineTest.kt
│           └── strategy/
│               ├── InsertUndoStrategyTest.kt
│               └── UpdateUndoStrategyTest.kt
```

### Appendix B: Testing Commands

**Run all tests:**
```bash
./gradlew test
```

**Run tests for specific module:**
```bash
./gradlew :katalyst-persistence:test
```

**Run single test class:**
```bash
./gradlew :katalyst-persistence:test --tests "CrudRepositoryTest"
```

**Run single test method:**
```bash
./gradlew :katalyst-persistence:test --tests "CrudRepositoryTest.save should persist entity"
```

**Generate coverage report:**
```bash
./gradlew koverHtmlReport
```

**View coverage report:**
```bash
open build/reports/kover/html/index.html
```

**Run tests in parallel:**
```bash
./gradlew test --parallel --max-workers=4
```

### Appendix C: Reference Documents

**Analysis Documents Created:**
1. **TEST_COVERAGE_ANALYSIS.md** - Comprehensive module-by-module analysis
2. **TEST_COVERAGE_SUMMARY.md** - Executive summary and action items
3. **TEST_COVERAGE_QUICK_REFERENCE.txt** - Quick reference guide
4. **TESTING_ANALYSIS.md** - Detailed analysis of untested modules

**Configuration Files:**
- `/home/user/katalyst/build.gradle.kts` - Root build with Kover
- `/home/user/katalyst/settings.gradle.kts` - Module definitions
- `/home/user/katalyst/gradle/libs.versions.toml` - Dependency versions

**Testing Utilities:**
- `katalyst-testing-core` - Test environment and utilities
- `katalyst-testing-ktor` - Ktor test application DSL

### Appendix D: Technology Stack

**Languages & Frameworks:**
- Kotlin 2.2.20
- Ktor 3.3.1
- Koin 3.5.6 (DI)
- Exposed 1.0.0-rc-3 (SQL)

**Testing:**
- JUnit 5.1.10.2
- Kotlin Test
- Kotlinx Coroutines Test
- Ktor Test Host

**Database:**
- H2 (in-memory for tests)
- HikariCP (connection pooling)
- PostgreSQL (optional with TestContainers)

**Build & Coverage:**
- Gradle 8.x
- Kover 0.8.1

### Appendix E: Key Contacts & Resources

**Project Repository:**
- Location: `/home/user/katalyst`
- Branch: `claude/katalyst-testing-audit-plan-018HV9RjN2pFejKPFA7YDdir`

**Documentation:**
- Katalyst README: `/home/user/katalyst/README.md`
- Architecture docs: (if available in repo)

**Testing Resources:**
- JUnit 5 User Guide: https://junit.org/junit5/docs/current/user-guide/
- Kotlin Test: https://kotlinlang.org/api/latest/kotlin.test/
- Ktor Testing: https://ktor.io/docs/testing.html

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-14 | Claude | Initial comprehensive testing refactor plan |

---

## Approval

**Prepared by:** Claude (AI Assistant)
**Date:** 2025-11-14
**Status:** Draft - Pending Review

**Review Required:**
- [ ] Technical Lead
- [ ] Project Manager
- [ ] QA Lead
- [ ] Development Team

**Next Steps:**
1. Review and approve this plan
2. Allocate resources (1-2 developers)
3. Set up tracking (Jira/GitHub Projects)
4. Begin Phase 0: Preparation

---

**END OF DOCUMENT**
