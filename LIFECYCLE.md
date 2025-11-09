# Katalyst Application Lifecycle

This document describes the complete application lifecycle in Katalyst, from startup to readiness. Understanding this lifecycle is essential for:
- Adding custom initialization logic
- Diagnosing startup failures
- Understanding when components are available
- Implementing initializer hooks

## Overview

The Katalyst lifecycle has **distinct phases**, each with specific responsibilities and availability guarantees.

```
┌─────────────────────────────────────────────────────────────┐
│ Application Startup Sequence                                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 1. Parse Command-Line Arguments                             │
│    - Load configuration files (application.yaml, etc.)      │
│    - Initialize Kotlin coroutine context                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Koin DI Bootstrap                                        │
│    - Load core modules (DatabaseModule, ScannerModule)      │
│    - Load feature modules (SchedulerModule, EventsModule)   │
│    - Register engine implementation modules                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Component Discovery & Registration                       │
│    - Scan packages for services, repositories, etc.         │
│    - Instantiate discovered components                      │
│    - Register components in Koin DI container               │
│    Status: ✅ All services available                        │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Database Schema Initialization                           │
│    - Discover all Table implementations                     │
│    - Create database schema (tables, indexes)               │
│    - Run migrations if applicable                           │
│    Status: ✅ Database ready for operations                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Transaction Adapter Registration                         │
│    - Register Persistence adapter (always available)        │
│    - Register Events adapter (if EventBus available)        │
│    Status: ✅ Transactions are tracked                      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. Application Initialization Lifecycle                     │
│                                                             │
│    Execute ApplicationInitializer implementations in order: │
│    • order=-100: StartupValidator (DB readiness check)      │
│    • order=-50:  SchedulerInitializer (discover tasks)      │
│    • order=-40:  EngineInitializer (validate engine)        │
│    • order=0+:   Custom initializers (user-defined)         │
│                                                             │
│    Status: ✅ Application fully initialized                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. Ktor Application Ready                                   │
│    - Server listening on configured host:port               │
│    - Ready to accept HTTP requests                          │
│    - All features operational                               │
│    Status: ✅ Application running                           │
└─────────────────────────────────────────────────────────────┘
```

## Detailed Phases

### Phase 1: Arguments & Configuration Parsing
**Status**: Initial startup
**What's available**: None yet
**What can fail**:
- Invalid YAML configuration
- Missing required properties
- Invalid data types in config

### Phase 2: Koin DI Bootstrap
**Status**: Building dependency injection container
**What's available**: Module instances (before component discovery)
**What can fail**:
- Circular dependencies
- Missing required beans
- Invalid module configuration

**Throwable**: `LifecycleException` (subtype varies)

### Phase 3: Component Discovery & Registration
**Status**: Scanning and instantiating components
**What's available**: Instantiated services, repositories, validators
**Guarantees**:
- All `Service` implementations discovered
- All `Repository` implementations registered
- All `EventHandler` implementations loaded
- All `KtorModule` implementations available

**What can fail**:
- Service constructor exceptions
- Classpath scanning errors
- Invalid component annotations

**Throwable**: `ComponentDiscoveryException`

### Phase 4: Database Schema Initialization
**Status**: Creating database structure
**What's available**: All discovered services + database connection
**Guarantees**:
- All tables created
- All indexes created
- Foreign key constraints in place
- Initial data loaded (if applicable)

**What can fail**:
- SQL syntax errors in table definitions
- Database connection issues
- Permission errors
- Incompatible database version

**Throwable**: `SchemaInitializationException`

### Phase 5: Transaction Adapter Registration
**Status**: Setting up transaction tracking
**What's available**: All from previous phases + database schema
**Guarantees**:
- Persistence operations tracked
- Event transactions coordinated (if Events enabled)
- Rollback behavior configured

**What can fail**:
- Event bus not available
- Adapter instantiation errors
- Configuration issues

**Throwable**: `TransactionAdapterException`

### Phase 6: Application Initialization Hooks
**Status**: Executing custom initialization code
**What's available**: Everything - full DI container, database, all services
**Execution order** (ascending by `order` field):

```
1. StartupValidator (order=-100)
   - Validates database is accessible
   - Checks schema version compatibility

2. SchedulerInitializer (order=-50)
   - Discovers @ScheduledJob methods
   - Validates job signatures
   - Registers with scheduler

3. EngineInitializer (order=-40)
   - Validates Ktor engine configuration
   - Confirms engine module on classpath

4. Custom Initializers (order=0+)
   - User-defined initialization logic
```

**What can fail**:
- Any custom initializer exception
- Invalid scheduler configuration
- Missing engine implementation module

**Throwable**: `InitializerFailedException` (wraps underlying cause)

### Phase 7: Server Ready
**Status**: Application running
**What's available**: Everything - fully operational application
**Guarantees**:
- HTTP endpoints responding
- Scheduler executing scheduled jobs
- Database transactions managed
- Event system operational (if enabled)

## Failure Handling (Fail-Fast Pattern)

**Key Principle**: If ANY phase fails, the application **WILL NOT START**.

This follows the Spring Boot pattern - misconfiguration is caught immediately, not silently.

### Error Types

```
LifecycleException (base)
├── DatabaseValidationException
├── ComponentDiscoveryException
├── ServiceInitializationException
├── SchemaInitializationException
├── InitializerFailedException
└── TransactionAdapterException
```

### Example: Missing Engine Module

If `katalyst-ktor-engine-netty` is not on the classpath:

```
╔════════════════════════════════════════════════════╗
║ ✗ INITIALIZATION FAILED                           ║
║ Failed at: EngineInitializer                       ║
║ Reason: No KtorEngineConfiguration found in Koin.  ║
║         Ensure an engine implementation module     ║
║         is on the classpath                        ║
╚════════════════════════════════════════════════════╝

Caused by: NoSuchElementException: No element found...
```

**Resolution**: Add dependency to `build.gradle.kts`:
```kotlin
implementation(project(":katalyst-ktor-engine-netty"))
```

## Implementing Custom Initializers

To add custom initialization logic, implement `ApplicationInitializer`:

```kotlin
class MyCustomInitializer : ApplicationInitializer {
    override val order: Int = 10  // After built-ins

    override suspend fun onApplicationReady(koin: Koin) {
        // At this point:
        // ✅ All services instantiated
        // ✅ Database operational
        // ✅ Schema created
        // ✅ Scheduler registered (if enabled)

        val myService = koin.get<MyService>()
        myService.postInitializationSetup()
    }
}
```

Then register in your Koin module:

```kotlin
val myModule = module {
    single<ApplicationInitializer> { MyCustomInitializer() }
}
```

The initializer will be discovered automatically and executed in order.

## Logging & Debugging

### Enable Detailed Logging

Add to `logback.xml`:

```xml
<logger name="InitializerRegistry" level="DEBUG"/>
<logger name="StartupValidator" level="DEBUG"/>
<logger name="SchedulerInitializer" level="DEBUG"/>
<logger name="EngineInitializer" level="DEBUG"/>
```

### What You'll See

```
INFO  InitializerRegistry - Registered 3 initializer(s):
INFO  InitializerRegistry -   [Order: -100] StartupValidator
INFO  InitializerRegistry -   [Order:  -50] SchedulerInitializer
INFO  InitializerRegistry -   [Order:  -40] EngineInitializer

INFO  StartupValidator - Validating database connectivity...
DEBUG StartupValidator - Database check completed successfully

INFO  SchedulerInitializer - Discovering @ScheduledJob methods...
DEBUG SchedulerInitializer - Found 5 scheduler methods

INFO  EngineInitializer - Initializing Ktor engine...
INFO  EngineInitializer - Engine ready: listening on 0.0.0.0:8080

INFO  InitializerRegistry - ✓ APPLICATION INITIALIZATION COMPLETE
```

## Common Issues & Solutions

### Issue: "No KtorEngineConfiguration found"
**Cause**: Engine module not on classpath
**Solution**: Add `katalyst-ktor-engine-netty` (or other engine) to dependencies

### Issue: "Database connection failed"
**Cause**: Database not running or credentials wrong
**Solution**: Check database service, connection string, credentials

### Issue: "Unknown table" error during schema init
**Cause**: Table class not discovered
**Solution**: Ensure table class is in a scanned package and implements `Table`

### Issue: Custom initializer not running
**Cause**: Not registered as `ApplicationInitializer` in Koin
**Solution**: Add to your module: `single<ApplicationInitializer> { YourInitializer() }`

## Timing Expectations

| Phase | Typical Duration |
|-------|------------------|
| Koin DI Bootstrap | <50ms |
| Component Discovery | 100ms - 500ms (depends on package size) |
| Schema Initialization | 50ms - 1s (depends on table count) |
| Initializer Hooks | Varies (user-dependent) |
| **Total** | **300ms - 2s** |

On modern hardware with typical projects, expect **<500ms** to "Application ready".

## Architecture Notes

### Why Fail-Fast?
- Silent failures lead to production bugs
- Startup-time validation catches config errors immediately
- Better developer experience (fail early, fail loudly)
- Follows Spring Boot best practices

### Why Ordered Initializers?
- Database validation happens before using it
- Scheduler runs before handling traffic
- Custom logic runs when dependencies are ready
- Clear, predictable execution order

### Why Reflect ng-based Discovery?
- Services don't need framework annotations
- Clean separation: framework orchestrates, user code is clean
- Automatic scalability: add services without framework changes
- Type-safe: uses Java reflection, compile-time verified

## Summary

The Katalyst lifecycle ensures:
1. ✅ Deterministic startup sequence
2. ✅ Clear phase boundaries
3. ✅ Fail-fast error handling
4. ✅ Comprehensive logging
5. ✅ Easy customization via initializers
6. ✅ Production-ready error messages
