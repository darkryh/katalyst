# Katalyst Lifecycle Troubleshooting Guide

This guide helps diagnose and resolve common issues during application initialization.

## Startup Failures

### Application Won't Start - Generic Startup Error

**Symptoms**:
- Application exits immediately on startup
- No clear error message in logs

**Steps**:
1. Enable debug logging in `logback.xml`:
```xml
<logger name="InitializerRegistry" level="DEBUG"/>
<logger name="StartupValidator" level="DEBUG"/>
<logger name="SchedulerInitializer" level="DEBUG"/>
<logger name="EngineInitializer" level="DEBUG"/>
```

2. Run the application and examine logs carefully
3. Look for stack traces in the error output

### ✗ DATABASE CONNECTION FAILED

**Symptoms**:
```
╔════════════════════════════════════════════════════╗
║ ✗ DATABASE CONNECTION FAILED                      ║
║ Failed at: StartupValidator                       ║
║ Reason: Connection refused: localhost:5432        ║
╚════════════════════════════════════════════════════╝
```

**Diagnosis**:
1. Is the database service running?
```bash
# PostgreSQL example
docker ps | grep postgres
# or check database connection
psql -h localhost -U postgres -d katalyst
```

2. Check connection string in configuration:
```yaml
# application.yaml
database:
  url: "jdbc:postgresql://localhost:5432/katalyst"
  username: "postgres"
  password: "password"
```

3. Verify network connectivity:
```bash
ping localhost
telnet localhost 5432  # Should connect
```

**Resolution**:
- Start the database service: `docker-compose up -d postgres`
- Verify connection string matches running database
- Check database user has correct permissions

### ✗ No KtorEngineConfiguration found

**Symptoms**:
```
║ ✗ INITIALIZATION FAILED                           ║
║ Failed at: EngineInitializer                       ║
║ Reason: No KtorEngineConfiguration found in Koin. ║
║         Ensure an engine implementation module     ║
║         is on the classpath                        ║
╚════════════════════════════════════════════════════╝
```

**Cause**: Ktor engine module not in dependencies

**Solution**: Add engine implementation to `build.gradle.kts`:
```kotlin
dependencies {
    // Add this:
    implementation(project(":katalyst-ktor-engine-netty"))
    // OR for other engines:
    // implementation(project(":katalyst-ktor-engine-jetty"))
}
```

### ✗ No services found during scheduler discovery

**Symptoms**:
```
INFO  SchedulerInitializer - Found 0 service(s) to scan
```

**Cause**: No services registered or scanner package misconfigured

**Resolution**:
1. Verify scanner is configured correctly in DIConfiguration
2. Ensure services are in scanned packages
3. Verify `@Suppress("unused")` is on scheduler methods:

```kotlin
// Correct:
@Suppress("unused")
fun scheduleMyJob(): SchedulerJobHandle {
    return scheduler.schedule(...)
}

// Wrong - will not be discovered:
fun scheduleMyJob(): SchedulerJobHandle {
    return scheduler.schedule(...)
}
```

### ✗ Scheduler method validation failed

**Symptoms**:
```
INFO  SchedulerInitializer - No candidates passed bytecode validation
```

**Cause**: Methods match signature but don't call scheduler methods

**Solution**: Ensure method actually calls a scheduler method:
```kotlin
// Correct - calls scheduler method:
@Suppress("unused")
fun scheduleMyJob(): SchedulerJobHandle {
    return scheduler.scheduleCron("0 0 * * *") {
        doWork()
    }
}

// Wrong - doesn't call scheduler:
@Suppress("unused")
fun scheduleMyJob(): SchedulerJobHandle {
    doWork()
    return Job().asSchedulerHandle()  // ❌ Never calls scheduler
}
```

## Runtime Issues

### Custom Initializer Not Running

**Symptoms**:
- Initializer logs don't appear
- Initialization logic doesn't execute

**Cause**: Initializer not registered in Koin

**Solution**: Check registration in your module:
```kotlin
val myModule = module {
    // Register as ApplicationInitializer:
    single<ApplicationInitializer> { MyInitializer() }
}
```

### Application Starts But Services Not Available

**Symptoms**:
```
Exception in initializer: No element found for koin instance: MyService
```

**Cause**: Service not in scanned packages or not registered in Koin

**Resolution**:
1. Verify service is in a scanned package
2. Check DIConfiguration scanner configuration
3. Ensure service implements expected interface/annotation
4. Verify service is instantiable (no abstract, has default constructor)

### Transaction Operations Fail in Initializer

**Symptoms**:
```
Exception: Transaction adapter not registered
```

**Cause**: Initializer running before Phase 5 (Transaction Adapter Registration)

**Solution**: Use `order >= 0` for initializers that use transactions:
```kotlin
class MyInitializer : ApplicationInitializer {
    override val order = 10  // Runs after Phase 5
}
```

### OutOfMemory During Initialization

**Symptoms**:
```
Exception: java.lang.OutOfMemoryError: Java heap space
```

**Cause**: Initialization loading too much data or infinite loop

**Resolution**:
1. Check for infinite loops in initializer code
2. Reduce data loading size:
```kotlin
// Instead of:
val allUsers = userRepository.findAll()  // Loads everything

// Use:
val users = userRepository.findAll().limit(100)  // Paginate
```

3. Increase heap size:
```bash
java -Xmx2g -jar application.jar
```

## Debugging

### Enable Detailed Lifecycle Logging

Add to `logback.xml`:
```xml
<logger name="InitializerRegistry" level="DEBUG"/>
<logger name="StartupValidator" level="DEBUG"/>
<logger name="SchedulerInitializer" level="DEBUG"/>
<logger name="SchedulerMethodBytecodeValidator" level="DEBUG"/>
<logger name="EngineInitializer" level="DEBUG"/>
<logger name="com.ead.katalyst.di" level="DEBUG"/>
```

### View Initialization Phases

Look for these markers in logs:
```
╔════════════════════════════════════════════════════╗
║ APPLICATION INITIALIZATION STARTING               ║
```

And each phase:
```
║ PHASE 2: Scheduler Method Discovery & Invocation   ║
║ PHASE 3: Component Discovery & Registration        ║
║ PHASE 4: Database Schema Initialization            ║
║ PHASE 5: Transaction Adapter Registration          ║
║ PHASE 6: INITIALIZATION HOOKS                      ║
```

### Check Initializer Execution Order

Look for lines like:
```
  [Order:  -100] StartupValidator
  [Order:   -50] SchedulerInitializer
  [Order:   -40] EngineInitializer
  [Order:    10] MyCustomInitializer
```

### Verify Services Are Discovered

Look for:
```
Found 5 service(s) to scan
  [Candidate] UserService.scheduleBackup()
  [Valid] UserService.scheduleBackup()
```

## Exception Reference

### LifecycleException Hierarchy

```
LifecycleException (base)
├── DatabaseValidationException
│   └── "Failed to validate database connectivity"
├── ComponentDiscoveryException
│   └── "Failed to discover components"
├── ServiceInitializationException
│   └── "Failed to initialize service"
├── SchemaInitializationException
│   └── "Failed to initialize database schema"
├── InitializerFailedException
│   └── "Custom initializer failed"
└── TransactionAdapterException
    └── "Failed to register transaction adapter"
```

### SchedulerException Hierarchy

```
SchedulerException (base)
├── SchedulerServiceNotAvailableException
│   └── "SchedulerService is not available in DI"
├── SchedulerDiscoveryException
│   └── "Scheduler method discovery failed"
├── SchedulerValidationException
│   └── "Scheduler method bytecode validation failed"
├── SchedulerInvocationException
│   └── "Scheduler method invocation failed"
└── SchedulerConfigurationException
    └── "Scheduler configuration is invalid"
```

## Configuration Checklist

Use this checklist when application won't start:

- [ ] Database service is running and accessible
- [ ] Database connection string is correct
- [ ] Database user has correct permissions
- [ ] Ktor engine module is in dependencies
- [ ] Services are in scanned packages
- [ ] Services have proper annotations (@Service, etc.)
- [ ] Initializers are registered as `single<ApplicationInitializer>`
- [ ] Initializer order values make sense (-100 to 100)
- [ ] Required services are available before initializer runs
- [ ] No circular dependencies between services
- [ ] Configuration files are valid YAML
- [ ] JVM has enough memory (-Xmx parameter)
- [ ] All required modules are on classpath

## Getting Help

When debugging initialization issues, provide:

1. **Full startup logs** with DEBUG level
2. **Exception stack trace** (all lines)
3. **Application configuration** (database, server settings)
4. **Dependencies** (relevant build.gradle.kts entries)
5. **Initializer code** (if custom)
6. **System information** (OS, Java version, database version)

Example error report:
```
Error: Application won't start
OS: macOS 12.4
Java: openjdk 17.0.1
Database: PostgreSQL 14

Full logs:
[paste all logs with DEBUG enabled]

Configuration:
[paste relevant config]

Stack trace:
[paste full exception]
```
