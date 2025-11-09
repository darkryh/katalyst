# Custom Initializers Guide

This guide explains how to create and register custom initialization logic that runs during application startup.

## Overview

Custom initializers allow you to hook into the application lifecycle and run code at specific initialization phases. They are executed in a predictable order and support fail-fast error handling.

## ApplicationInitializer Interface

All custom initializers must implement the `ApplicationInitializer` interface:

```kotlin
interface ApplicationInitializer {
    /**
     * Unique identifier for this initializer (for logging/debugging)
     */
    val initializerId: String

    /**
     * Execution order. Lower values execute first.
     * Built-in initializers use negative orders:
     * - StartupValidator: -100
     * - SchedulerInitializer: -50
     * - EngineInitializer: -40
     *
     * Custom initializers typically use 0 or positive values
     */
    val order: Int

    /**
     * Called when the application is ready to initialize.
     * At this point:
     * - All services are instantiated
     * - Database schema is created
     * - Transaction adapters are registered
     * - Scheduler is operational (if enabled)
     */
    suspend fun onApplicationReady(koin: Koin)
}
```

## Creating a Custom Initializer

### Basic Example

```kotlin
import com.ead.katalyst.di.lifecycle.ApplicationInitializer
import org.koin.core.Koin
import org.slf4j.LoggerFactory

class DataInitializer : ApplicationInitializer {
    private val logger = LoggerFactory.getLogger(DataInitializer::class.java)

    override val initializerId: String = "DataInitializer"

    // Custom initializers typically use 0 or higher
    // This runs after built-in initializers
    override val order: Int = 10

    override suspend fun onApplicationReady(koin: Koin) {
        logger.info("Loading seed data...")

        // Access services from Koin
        val userService = koin.get<UserService>()

        // Perform initialization
        userService.seedAdminUser()

        logger.info("Seed data loaded successfully")
    }
}
```

### Advanced Example with Error Handling

```kotlin
class CacheWarmerInitializer : ApplicationInitializer {
    private val logger = LoggerFactory.getLogger(CacheWarmerInitializer::class.java)

    override val initializerId: String = "CacheWarmerInitializer"
    override val order: Int = 20

    override suspend fun onApplicationReady(koin: Koin) {
        try {
            logger.info("Warming up application caches...")

            val cacheService = koin.get<CacheService>()
            val userRepository = koin.get<UserRepository>()

            // Warm up critical caches
            val totalUsers = userRepository.count()
            logger.info("Loaded cache with $totalUsers users")

            cacheService.prefetchFrequentQueries()

            logger.info("Cache warming completed")
        } catch (e: Exception) {
            logger.warn("Cache warming failed, continuing with cold start", e)
            // Don't throw - cache warming is optional
        }
    }
}
```

## Registering Your Initializer

### Option 1: In a Koin Module (Recommended)

Create a module in your application:

```kotlin
// MyApplicationModule.kt
import org.koin.dsl.module
import com.ead.katalyst.di.lifecycle.ApplicationInitializer

val myApplicationModule = module {
    // Register your initializers
    single<ApplicationInitializer> { DataInitializer() }
    single<ApplicationInitializer> { CacheWarmerInitializer() }
}
```

Then include the module in your DIConfiguration:

```kotlin
// In your application setup code
startKoin {
    modules(
        // ... other modules ...
        myApplicationModule
    )
}
```

### Option 2: In a Feature Module

If you're creating a feature module (like scheduler or events), you can register initializers in your module:

```kotlin
// MyFeatureModule.kt
val myFeatureModule = module {
    // Register feature-specific initializer
    single<ApplicationInitializer> { MyFeatureInitializer() }

    // Register other feature components
    single { MyFeatureService() }
}
```

## Execution Order

Initializers execute in ascending order by their `order` field:

```
order=-100  StartupValidator (validates DB connectivity)
order=-50   SchedulerInitializer (discovers scheduled jobs)
order=-40   EngineInitializer (validates Ktor configuration)
order=0     CustomInitializer (user code runs here)
order=10    DataInitializer (seed data, cache warming, etc.)
order=20    CacheWarmerInitializer
```

**Recommendation**: Use `order=0` to `order=100` for custom initializers.

## Error Handling

### Fail-Fast Behavior

If any initializer throws an exception, the application **will not start**:

```kotlin
override suspend fun onApplicationReady(koin: Koin) {
    val config = koin.get<MyConfig>()

    if (!config.isValid()) {
        throw IllegalStateException("Configuration is invalid")
    }

    // If we get here, config is valid
    doInitialization()
}
```

### Wrapping Exceptions

To provide better error context, wrap exceptions:

```kotlin
override suspend fun onApplicationReady(koin: Koin) {
    try {
        val service = koin.get<MyService>()
        service.doSomething()
    } catch (e: SQLException) {
        throw InitializerFailedException(
            initializerName = initializerId,
            message = "Database error during initialization: ${e.message}",
            cause = e
        )
    }
}
```

### Optional Initialization

For non-critical operations, log and continue:

```kotlin
override suspend fun onApplicationReady(koin: Koin) {
    try {
        performOptionalSetup()
    } catch (e: Exception) {
        logger.warn("Optional setup failed, continuing anyway", e)
        // Don't throw - this is non-critical
    }
}
```

## Access to Application Context

During `onApplicationReady()`, you have full access to:

### 1. Services from Koin DI

```kotlin
val userService = koin.get<UserService>()
val transactionManager = koin.get<TransactionManager>()
val schedulerService = koin.get<SchedulerService>()  // if scheduler is enabled
```

### 2. Database (already initialized)

```kotlin
val database = koin.get<Database>()
transaction(database) {
    // Execute database operations
}
```

### 3. Configuration Objects

```kotlin
val appConfig = koin.get<ApplicationConfiguration>()
val databaseConfig = koin.get<DatabaseConfiguration>()
```

### 4. Event Bus (if Events module enabled)

```kotlin
val eventBus = koin.getOrNull<EventBus>()
eventBus?.publish(MyInitializationEvent())
```

## Common Patterns

### Pattern 1: Seed Data Initialization

```kotlin
class SeedDataInitializer : ApplicationInitializer {
    override val initializerId = "SeedDataInitializer"
    override val order = 10

    override suspend fun onApplicationReady(koin: Koin) {
        val userRepository = koin.get<UserRepository>()

        // Only seed if database is empty
        if (userRepository.count() == 0) {
            userRepository.save(User(id = 1, name = "Admin"))
            logger.info("Seed data initialized")
        }
    }
}
```

### Pattern 2: Connection Pool Warmup

```kotlin
class ConnectionPoolWarmup : ApplicationInitializer {
    override val initializerId = "ConnectionPoolWarmup"
    override val order = 5  // Run early

    override suspend fun onApplicationReady(koin: Koin) {
        val database = koin.get<Database>()

        // Connect and verify database accessibility
        transaction(database) {
            exec("SELECT 1")
        }

        logger.info("Connection pool verified")
    }
}
```

### Pattern 3: Feature Enablement Check

```kotlin
class FeatureCheckInitializer : ApplicationInitializer {
    override val initializerId = "FeatureCheckInitializer"
    override val order = 1

    override suspend fun onApplicationReady(koin: Koin) {
        val featureService = koin.get<FeatureService>()
        val enabledFeatures = featureService.listEnabled()

        logger.info("Enabled features: {}", enabledFeatures.joinToString(", ") { it.name })

        // Validate required features are available
        if (!enabledFeatures.any { it.name == "authentication" }) {
            throw IllegalStateException("Authentication feature is required but not enabled")
        }
    }
}
```

## Testing

Use the provided test fixtures to test your initializers:

```kotlin
import com.ead.katalyst.di.lifecycle.test.TestApplicationInitializer
import kotlin.test.Test
import kotlin.test.assertTrue

class MyInitializerTest {
    @Test
    fun `my initializer executes successfully`() {
        var initialized = false

        val initializer = MyInitializer()

        // Verify it's properly implemented
        assertTrue(initializer is ApplicationInitializer)

        // In real tests, you would integrate with Koin and verify behavior
        // See LIFECYCLE.md for integration testing patterns
    }
}
```

## Troubleshooting

### Issue: Initializer not running

**Cause**: Initializer not registered in Koin

**Solution**: Ensure your initializer is registered as:
```kotlin
single<ApplicationInitializer> { MyInitializer() }
```

### Issue: Services not available

**Cause**: Service not registered in DI container

**Solution**: Ensure service is registered in a Koin module before initializer runs

### Issue: Initialization hangs

**Cause**: Initializer is waiting for something that never happens

**Solution**: Add timeout logic or break circular dependencies

```kotlin
withTimeoutOrNull(Duration.ofSeconds(10)) {
    waitForService()
}
```

### Issue: Database not ready

**Cause**: Trying to access database in initializer with order < -100

**Solution**: Use order >= 0, database is guaranteed ready at that point

## Best Practices

1. **Keep Initializers Lightweight**: Don't perform heavy operations - these block startup
2. **Use Appropriate Order**: Use -100 to -50 for critical setup, 0+ for application logic
3. **Provide Clear Logging**: Always log what you're doing for debugging
4. **Fail Fast**: Throw exceptions for critical failures
5. **Document Dependencies**: Clearly document which services your initializer needs
6. **Handle Missing Services**: Use `getOrNull()` for optional services
7. **Test Thoroughly**: Write tests for your initializer logic
8. **Make it Idempotent**: Running twice should be safe (for restarts)

## Examples

See `katalyst-example` for complete examples of:
- Custom initializers
- Scheduler method discovery
- Database schema initialization
- Transaction tracking
