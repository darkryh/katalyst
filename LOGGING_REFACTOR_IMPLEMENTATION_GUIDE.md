# Logging Refactor Implementation Guide - Phase 4

## Overview

This guide details how to integrate the three new logger components (StartupWarningsAggregator, DiscoverySummaryLogger, BootstrapProgressLogger) into the existing Katalyst DI and initialization code.

## Implementation Map

### 1. AutoBindingRegistrar.kt Integration

**File**: `katalyst-di/src/main/kotlin/com/ead/katalyst/di/internal/AutoBindingRegistrar.kt`

**Changes Required**:

At the END of the `registerAll()` method (around line 106), add:

```kotlin
fun registerAll() {
    registerComponents(Repository::class.java, "repositories")
    registerComponents(Component::class.java, "components")
    registerComponents(Service::class.java, "services")
    registerTables()
    registerComponents(KtorModule::class.java, "ktor modules")
    registerComponents(EventHandler::class.java as Class<EventHandler<*>>, "event handlers")
    registerComponents(KatalystMigration::class.java, "migrations")
    registerRouteFunctions()

    // ✅ NEW: Display discovery summary
    displayComponentDiscoverySummary()
}

private fun displayComponentDiscoverySummary() {
    // Import needed:
    // import com.ead.katalyst.di.lifecycle.DiscoverySummary
    // import com.ead.katalyst.di.lifecycle.DiscoverySummaryLogger

    try {
        // Attempt to gather all discovered components from Koin
        val repositories = runCatching { koin.getAll<Repository>() }.getOrNull() ?: emptyList()
        val services = runCatching { koin.getAll<Service>() }.getOrNull() ?: emptyList()
        val components = runCatching { koin.getAll<Component>() }.getOrNull() ?: emptyList()
        val tables = runCatching { koin.getAll<Table>() }.getOrNull() ?: emptyList()
        val ktorModules = runCatching { koin.getAll<KtorModule>() }.getOrNull() ?: emptyList()
        val eventHandlers = runCatching { koin.getAll<EventHandler<*>>() }.getOrNull() ?: emptyList()

        // Add to summary
        repositories.forEach { repo ->
            DiscoverySummary.addRepository(
                name = repo::class.simpleName ?: "Repository",
                annotation = "@Repository"
            )
        }

        services.forEach { svc ->
            DiscoverySummary.addService(
                name = svc::class.simpleName ?: "Service",
                annotation = "@Service"
            )
        }

        components.forEach { comp ->
            DiscoverySummary.addComponent(
                name = comp::class.simpleName ?: "Component",
                type = comp::class.simpleName?.replace("Component", "") ?: "Custom",
                annotation = "@Component"
            )
        }

        tables.forEach { table ->
            DiscoverySummary.addDatabaseTable((table as? org.jetbrains.exposed.sql.Table)?.tableName ?: "unknown")
        }

        ktorModules.forEach { module ->
            DiscoverySummary.addKtorModule(
                name = module::class.simpleName ?: "KtorModule",
                annotation = "@KtorModule"
            )
        }

        // Display the summary
        DiscoverySummary.display()

    } catch (e: Exception) {
        logger.warn("Error displaying discovery summary", e)
    }
}
```

### 2. DIConfiguration.kt Integration

**File**: `katalyst-di/src/main/kotlin/com/ead/katalyst/di/config/DIConfiguration.kt`

**Changes Required**:

Around line 149-151, modify the AutoBindingRegistrar call and add progress tracking:

```kotlin
// Import needed:
// import com.ead.katalyst.di.lifecycle.BootstrapProgress

// Before line 150
BootstrapProgress.startPhase(3)  // PHASE 3: COMPONENT DISCOVERY

try {
    logger.info("Starting AutoBindingRegistrar to discover components...")
    AutoBindingRegistrar(koin, scanPackages).registerAll()
    logger.info("AutoBindingRegistrar completed")

    BootstrapProgress.completePhase(3, "Discovered repositories, services, and components")

} catch (e: Exception) {
    BootstrapProgress.failPhase(3, e)
    throw e
}
```

Also around line 160-191 (database schema initialization), wrap with phase tracking:

```kotlin
// Before database discovery
BootstrapProgress.startPhase(4)  // PHASE 4: DATABASE SCHEMA

try {
    logger.debug("Attempting to retrieve discovered Table instances from Koin...")
    val discoveredTables = koin.getAll<Table>()
    logger.info("Discovered {} table(s) for initialization", discoveredTables.size)
    // ... rest of table discovery code ...

    BootstrapProgress.completePhase(4, "Database schema initialized with ${exposedTables.size} tables")
} catch (e: Exception) {
    BootstrapProgress.failPhase(4, e)
    throw e
}
```

And around line 193-220 (transaction adapters):

```kotlin
// Before transaction adapter registration
BootstrapProgress.startPhase(5)  // PHASE 5: TRANSACTION ADAPTER REGISTRATION

try {
    logger.info("Registering transaction adapters...")
    // ... existing adapter registration code ...
    logger.info("Transaction adapter registration completed")

    BootstrapProgress.completePhase(5, "Registered persistence and event adapters")
} catch (e: Exception) {
    BootstrapProgress.failPhase(5, e)
    throw e
}
```

### 3. InitializerRegistry.kt Integration

**File**: `katalyst-di/src/main/kotlin/com/ead/katalyst/di/lifecycle/InitializerRegistry.kt`

**Changes Required**:

Around line 39 (start of invokeAll), add:

```kotlin
// Import needed:
// import com.ead.katalyst.di.lifecycle.BootstrapProgress

suspend fun invokeAll() {
    try {
        BootstrapProgress.startPhase(6)  // PHASE 6: APPLICATION INITIALIZATION HOOKS

        // ... existing code ...

        initializers.forEach { initializer ->
            val startTime = System.currentTimeMillis()
            logger.info("⏱  Starting: {}", initializer.initializerId)

            runCatching {
                initializer.onApplicationReady(koin)
            }.onFailure { e ->
                BootstrapProgress.failPhase(6, e)
                // ... existing error handling ...
            }

            val duration = System.currentTimeMillis() - startTime
            logger.info("✓  Completed: {} ({} ms)", initializer.initializerId, duration)
        }

        BootstrapProgress.completePhase(6, "All initialization hooks executed")

        // ... rest of method ...
    } catch (e: Exception) {
        logger.error("Fatal error during initialization", e)
        BootstrapProgress.failPhase(6, e)
        throw e
    }
}
```

### 4. StartupWarnings Integration Points

**File**: `katalyst-di/src/main/kotlin/com/ead/katalyst/di/config/DIConfiguration.kt`

Add warnings for optional features during bootstrap:

```kotlin
// Import needed:
// import com.ead.katalyst.di.lifecycle.StartupWarnings
// import com.ead.katalyst.di.lifecycle.StartupWarningsAggregator

// After component discovery, add warnings for missing optional features:

try {
    val schedulerService = koin.getOrNull<com.ead.katalyst.scheduler.SchedulerService>()
    if (schedulerService == null) {
        StartupWarnings.add(
            category = "Optional Features",
            message = "Scheduler service not available",
            severity = StartupWarningsAggregator.WarningSeverity.INFO,
            hint = "Add katalyst-scheduler dependency to enable scheduled tasks"
        )
    }
} catch (e: Exception) {
    // Scheduler not available - expected
}

try {
    val eventBus = koin.getOrNull<com.ead.katalyst.events.bus.ApplicationEventBus>()
    if (eventBus == null) {
        StartupWarnings.add(
            category = "Optional Features",
            message = "Event bus not available",
            severity = StartupWarningsAggregator.WarningSeverity.INFO,
            hint = "Add katalyst-events dependency to enable event-driven architecture"
        )
    }
} catch (e: Exception) {
    // Events not available - expected
}
```

### 5. KatalystApplication.kt Updates

**File**: `katalyst-di/src/main/kotlin/com/ead/katalyst/di/KatalystApplication.kt`

Already integrated in Phase 1! But add phase 7 tracking:

```kotlin
embeddedServer.monitor.subscribe(ApplicationStarting) { application ->
    BootstrapProgress.startPhase(7)  // PHASE 7: KTOR ENGINE STARTUP

    runCatching {
        val wrappedApplication = application.wrap(serverConfig.applicationWrapper)
        builder.configureApplication(wrappedApplication)
    }.onFailure { error ->
        logger.error("Failed to configure Ktor application", error)
        BootstrapProgress.failPhase(7, error)
        throw error
    }
}

embeddedServer.monitor.subscribe(ApplicationStarted) {
    val elapsedSeconds = (System.nanoTime() - bootStart) / 1_000_000_000.0
    logger.info("Katalyst started in {} s (actual)", String.format("%.3f", elapsedSeconds))

    BootstrapProgress.completePhase(7, "Ktor server listening")
    BootstrapProgress.displayProgressSummary()
}
```

## Integration Sequence

1. **Phase 3**: AutoBindingRegistrar discovers components → DiscoverySummaryLogger displays table
2. **Phase 4**: Database initialization with progress tracking
3. **Phase 5**: Transaction adapter registration with progress tracking
4. **Phase 6**: InitializerRegistry executes hooks with progress tracking
5. **Phase 7**: Ktor engine starts with progress tracking
6. **Warnings Display**: Before completion banner (already integrated in Phase 1)
7. **Completion Banner**: After all phases complete (already integrated in Phase 1)

## Testing Phase 4 Integration

After implementing all changes:

```bash
# Clean build
./gradlew clean build -x test

# Test with example application
java -jar ./katalyst-example/build/libs/katalyst-example-all.jar

# Expected output:
# ✓ PHASE 3: COMPONENT DISCOVERY table with discovered items
# ✓ PHASE 4: DATABASE SCHEMA INITIALIZATION
# ✓ PHASE 5: TRANSACTION ADAPTER REGISTRATION
# ✓ PHASE 6: INITIALIZATION HOOKS
# ✓ PHASE 7: KTOR ENGINE STARTUP
# ✓ Warnings table (if any)
# ✓ APPLICATION STARTUP COMPLETE banner
# ✓ "Responding at http://0.0.0.0:8080"
```

## Critical Integration Notes

1. **Import Statements**: Each file needs proper imports for the new logger classes
2. **Global Singletons**: DiscoverySummary, BootstrapProgress, StartupWarnings are global objects
3. **Error Handling**: All logger calls should be wrapped in try-catch to prevent breaking startup
4. **Order Matters**: Phase tracking must follow the actual execution order
5. **Phase Numbers**: Use standard 1-7 phase numbering as defined in BootstrapProgressLogger

## Phase 4 Completion Criteria

- [ ] AutoBindingRegistrar displays component discovery summary
- [ ] DIConfiguration uses BootstrapProgress for phases 3-5
- [ ] InitializerRegistry uses BootstrapProgress for phase 6
- [ ] KatalystApplication uses BootstrapProgress for phase 7
- [ ] StartupWarnings integrated for optional features
- [ ] All compilation successful (build -x test)
- [ ] Application starts without errors
- [ ] Visual output shows structured tables for components
- [ ] Progress tracking shows all 7 phases
- [ ] Warnings display (if applicable) before completion banner
