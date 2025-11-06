# Katalyst Library - Comprehensive Project Overview

## Executive Summary

**Katalyst** is a sophisticated Kotlin backend library built on top of Ktor and Koin that provides automatic dependency injection, exception handling, routing, and database transaction management. It follows a Spring Boot-like architecture pattern while leveraging Kotlin's coroutine system for non-blocking operations.

The library enables rapid development of Ktor-based REST APIs by automatically discovering and registering components (Services, Repositories, Validators, Event Handlers) without requiring manual Koin module configuration.

---

## 1. Directory Structure & Organization

```
katalyst/
├── dependency-injection/          # Core DI orchestration and auto-discovery
│   ├── src/main/kotlin/
│   │   └── com/ead/katalyst/di/
│   │       ├── DIConfiguration.kt         # DI setup functions
│   │       ├── KatalystApplication.kt     # Ktor application bootstrap
│   │       ├── CoreDIModule.kt            # Core module registration
│   │       ├── SchedulerDIModule.kt       # Scheduler registration
│   │       ├── ScannerDIModule.kt         # Scanner registration
│   │       ├── ServerConfiguration.kt     # Server config
│   │       └── internal/
│   │           ├── AutoBindingRegistrar.kt # Component discovery engine
│   │           └── KtorModuleRegistry.kt   # Ktor module tracking
│   └── src/test/kotlin/                   # DI tests
│
├── katalyst-core/                 # Core abstractions and interfaces
│   └── src/main/kotlin/
│       └── com/ead/katalyst/
│           ├── common/
│           │   └── Component.kt           # Component marker interface
│           ├── services/
│           │   └── Service.kt             # Service base interface
│           ├── validators/
│           │   └── Validator.kt           # Validator interface
│           ├── events/
│           │   └── Event.kt               # Event & EventHandler
│           ├── database/
│           │   └── DatabaseTransactionManager.kt
│           └── error/
│               └── DependencyInjectionException.kt
│
├── katalyst-persistence/          # Database and Repository layer
│   └── src/main/kotlin/
│       └── com/ead/katalyst/database/
│           ├── DatabaseConfig.kt          # Configuration class
│           ├── DatabaseFactory.kt         # HikariCP factory
│           ├── DatabaseModule.kt          # Koin module
│           └── Repository.kt              # Repository interface
│
├── katalyst-ktor-support/         # Ktor integration and routing
│   └── src/main/kotlin/
│       └── com/ead/katalyst/routes/
│           ├── KtorModule.kt              # KtorModule interface
│           ├── RoutingBuilder.kt          # Routing DSL
│           ├── ExceptionHandlerBuilder.kt # Exception handling DSL
│           ├── KoinRouteExtensions.kt     # DI extensions for routes
│           └── Middleware.kt              # Middleware utilities
│
├── katalyst-scheduler/            # Task scheduling support
│   └── src/main/kotlin/
│       └── com/ead/katalyst/
│           ├── scheduler/
│           │   └── SchedulerModules.kt    # Koin module
│           └── services/service/
│               ├── SchedulerService.kt    # Scheduler API
│               └── SchedulerAccessors.kt  # Scheduler helpers
│
├── scanner/                       # Type discovery via reflection
│   └── src/main/kotlin/
│       └── com/ead/katalyst/scanner/
│           ├── core/
│           │   ├── TypeDiscovery.kt       # Discovery interface
│           │   ├── DiscoveryRegistry.kt   # Registry
│           │   ├── DiscoveryPredicate.kt  # Filtering
│           │   ├── DiscoveryConfig.kt     # Config
│           │   └── DiscoveryMetadata.kt   # Metadata
│           ├── predicates/
│           │   └── BuiltInPredicates.kt   # Built-in filters
│           ├── util/
│           │   ├── MethodMetadata.kt      # Method info
│           │   └── GenericTypeExtractor.kt
│           └── integration/
│               ├── ScannerModule.kt       # Koin module
│               ├── KoinDiscoveryRegistry.kt
│               ├── AutoDiscoveryEngine.kt # Discovery engine
│               └── MethodDiscoveryDSL.kt  # DSL helpers
│
├── src/                           # Example application (Katalyst in action)
│   ├── main/kotlin/com/ead/katalyst/example/
│   │   ├── Application.kt                # Entry point
│   │   ├── domain/
│   │   │   ├── User.kt                   # Domain model
│   │   │   ├── UserService.kt            # Service implementation
│   │   │   ├── UserValidator.kt          # Validator
│   │   │   └── UserExampleValidationException.kt
│   │   ├── routes/
│   │   │   ├── UserRoutes.kt             # Route handlers
│   │   │   ├── HttpConfig.kt             # Ktor config
│   │   │   └── ExceptionHandler.kt       # Exception mapping
│   │   ├── api/
│   │   │   ├── UserDtos.kt               # DTOs
│   │   │   └── CreateUserRequest.kt
│   │   ├── infra/
│   │   │   ├── database/
│   │   │   │   ├── tables/
│   │   │   │   │   └── UsersTable.kt     # Exposed table
│   │   │   │   ├── entities/
│   │   │   │   │   └── UserEntity.kt     # Entity
│   │   │   │   ├── repositories/
│   │   │   │   │   └── UserRepository.kt # Repository
│   │   │   │   ├── mappers/
│   │   │   │   │   └── UserMappers.kt
│   │   │   │   └── config/
│   │   │   │       └── DatabaseConfigFactory.kt
│   │   │   └── ...
│   │   └── ...
│   └── test/                       # Tests
│
├── build.gradle.kts               # Root build configuration
├── settings.gradle.kts            # Gradle settings (module inclusion)
└── gradle/
    └── libs.versions.toml         # Dependency versions
```

### Module Dependency Graph

```
dependency-injection
    ├── depends on: katalyst-core, scanner, katalyst-persistence
    └── integrates: katalyst-ktor-support, katalyst-scheduler

katalyst-core (abstractions)
    └── standalone, no dependencies

katalyst-persistence (database)
    └── depends on: katalyst-core

katalyst-ktor-support (routing)
    └── depends on: katalyst-core

katalyst-scheduler
    └── depends on: katalyst-core

scanner (reflection)
    └── standalone, provides type discovery

example app (src/)
    └── depends on: all modules
```

---

## 2. Core Library Components

### 2.1 Dependency Injection System

The DI system is built around **automatic component discovery** using reflection and the **Koin** framework.

#### Key Files:
- `/Users/darkryh/Desktop/Libraries/Backend/katalyst/dependency-injection/src/main/kotlin/com/ead/katalyst/di/DIConfiguration.kt`
- `/Users/darkryh/Desktop/Libraries/Backend/katalyst/dependency-injection/src/main/kotlin/com/ead/katalyst/di/internal/AutoBindingRegistrar.kt`

#### How It Works:

1. **Automatic Discovery**: The framework scans specified packages at startup to find all classes implementing:
   - `Component` interface (general components)
   - `Service` interface (business logic)
   - `Repository<Id, Entity>` interface (data access)
   - `EventHandler<T>` interface (event handlers)
   - `KtorModule` interface (route module plugins)

2. **Smart Registration**: For each discovered class:
   - Analyzes its constructor parameters
   - Resolves dependencies from the Koin container
   - Instantiates the class with all dependencies
   - Registers it in Koin as both its primary type and any secondary interfaces

3. **Deferred Resolution**: If dependencies aren't yet available, the registrar defers registration and retries after other components are registered, enabling dependency chains.

#### Initialization Flow:

```kotlin
// Step 1: Configure and start
fun main(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.fromEnvironment())
    scanPackages("com.example.app")
    enableScheduler()
}

// Step 2: Internal bootstrap process:
// bootstrapKatalystDI()
//   ├─ Load core DI module (database, transaction manager)
//   ├─ Load scanner DI module (reflection utilities)
//   ├─ Load scheduler DI module (if enabled)
//   └─ Run AutoBindingRegistrar:
//       ├─ Discover Repository implementations
//       ├─ Discover Component implementations
//       ├─ Discover KtorModule implementations
//       ├─ Discover EventHandler implementations
//       ├─ Discover route extension functions
//       └─ Register all with Koin

// Step 3: Ktor application configuration
fun Application.module() {
    // DI is already initialized here
    // All services/repositories are available for injection
    configureRouting()
    exceptionHandlers()
}
```

#### Component Interface Hierarchy:

```
Component (marker interface)
├── Service (for business logic)
├── Validator (for validation logic)
└── EventHandler (for event processing)

Repository<Id, Entity> (standalone, not a Component)
    └── Provides CRUD operations

KtorModule (standalone, for route installation)
    └── Provides route installation hooks
```

### 2.2 Exception Handling

Located in: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-ktor-support/src/main/kotlin/com/ead/katalyst/routes/ExceptionHandlerBuilder.kt`

#### How It Works:

Developers define custom exceptions and map them to HTTP responses using the `katalystExceptionHandler` DSL:

```kotlin
// Define custom exceptions
class UserValidationException(message: String) : Exception(message)

// Map them to HTTP responses
fun Route.exceptionHandlers() = katalystExceptionHandler {
    exception<UserValidationException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "VALIDATION_ERROR", "message" to cause.message)
        )
    }
    
    exception<NotFoundException> { call, cause ->
        call.respond(HttpStatusCode.NotFound, mapOf("message" to cause.message))
    }
    
    exception<Throwable> { call, cause ->
        logger.error("Unexpected error", cause)
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "INTERNAL_ERROR"))
    }
}
```

#### Exception Handling Flow:

```
Route Handler throws UserValidationException
    ↓
Ktor StatusPages plugin catches exception
    ↓
ExceptionHandlerBuilder has registered handler for this type
    ↓
Handler invokes: call.respond(HttpStatusCode.BadRequest, ...)
    ↓
HTTP Response with 400 status sent to client
```

### 2.3 Routing & Route Discovery

Located in: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-ktor-support/src/main/kotlin/com/ead/katalyst/routes/`

#### Files:
- `RoutingBuilder.kt` - Main routing DSL
- `KoinRouteExtensions.kt` - Dependency injection in routes
- `KtorModule.kt` - Module plugin interface

#### How It Works:

1. **Route Functions**: Developers write Ktor extension functions that define routes
2. **Automatic Discovery**: The `AutoBindingRegistrar` discovers all static methods in `*Kt` files that:
   - Take an `Application` or `Route` as first parameter
   - Are static (in `*Kt` files)
   - Return `Unit` or `Void`
3. **Automatic Invocation**: These functions are wrapped in `RouteFunctionModule` and invoked automatically

#### Usage Pattern:

```kotlin
// Define routes (in any package that's scanned)
fun Route.userRoutes() = katalystRouting {
    route("/api/users") {
        post {
            val userService = call.inject<UserService>()
            val request = call.receive<CreateUserRequest>()
            val created = userService.createUser(request)
            call.respond(HttpStatusCode.Created, UserResponse.from(created))
        }
        
        get {
            val userService = call.inject<UserService>()
            val users = userService.listUsers().map(UserResponse::from)
            call.respond(users)
        }
    }
}

// These are automatically discovered and invoked during startup
// No manual routing setup needed!
```

#### Dependency Injection in Routes:

Two ways to get dependencies in route handlers:

```kotlin
// 1. Eager injection (use immediately)
val service: UserService = call.inject<UserService>()

// 2. Lazy injection (resolved when needed)
val serviceLazy: Lazy<UserService> = route.inject<UserService>()
val service = serviceLazy.value
```

### 2.4 Database & Transaction Management

Located in: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-core/src/main/kotlin/com/ead/katalyst/database/DatabaseTransactionManager.kt`

#### Architecture:

```
Service (suspend function)
    ↓
transactionManager.transaction {
    ├─ Establishes database connection from HikariCP pool
    ├─ Begins transaction
    ├─ Repository.save() - synch, uses tx connection
    ├─ Repository.findById() - synch, uses tx connection
    └─ Commits on success, rolls back on exception
}
```

#### Key Classes:

1. **DatabaseConfig** - Configuration class for connection parameters
   - JDBC URL, driver, username, password
   - Connection pool settings (max size, timeouts)
   - Transaction isolation level

2. **DatabaseFactory** - Creates and manages database connection
   - Sets up HikariCP connection pool
   - Connects to Exposed framework
   - Initializes tables if needed

3. **DatabaseTransactionManager** - Wraps suspend transactions
   - Uses Exposed's `newSuspendedTransaction`
   - Runs on IO dispatcher by default
   - Automatic commit/rollback

#### Usage Example:

```kotlin
class UserService(
    private val userRepository: UserRepository
) : Service {
    // transactionManager is automatically injected
    
    suspend fun createUser(request: CreateUserRequest): User {
        // All repository calls must be within a transaction
        return transactionManager.transaction {
            val existing = userRepository.findByEmail(request.email)
            if (existing != null) {
                throw ValidationException("Email already exists")
            }
            
            userRepository.save(User.from(request))
            // Transaction commits here automatically
        }
    }
}
```

### 2.5 Repository Pattern

Located in: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/Repository.kt`

#### Design:

The `Repository<Id, Entity>` interface provides generic CRUD operations on Exposed tables. Unlike Spring Data JPA, repositories are NOT components - they're purely data access objects.

#### Implementation Pattern:

```kotlin
// 1. Define table (Exposed)
object UsersTable : LongIdTable("users") {
    val name = varchar("name", 100)
    val email = varchar("email", 150)
}

// 2. Define entity (domain model)
data class UserEntity(
    override val id: Long? = null,
    val name: String,
    val email: String
) : Identifiable<Long>

// 3. Define repository (data access)
class UserRepository : Repository<Long, UserEntity> {
    override val table = UsersTable
    
    override fun mapper(row: ResultRow): UserEntity =
        UserEntity(
            id = row[table.id].value,
            name = row[table.name],
            email = row[table.email]
        )
    
    // Optional custom methods
    fun findByEmail(email: String): UserEntity? =
        table.selectAll()
            .where { table.email eq email }
            .limit(1)
            .firstOrNull()
            ?.let { mapper(it) }
}
```

#### Provided Methods:

- `save(entity)` - Insert or update
- `findById(id)` - Get by primary key
- `findAll()` - Get all (ordered by ID desc)
- `findAll(filter)` - Paginated query
- `delete(id)` - Delete by ID
- `count()` - Total count

### 2.6 Scanner/Type Discovery

Located in: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/scanner/src/main/kotlin/com/ead/katalyst/scanner/`

#### Architecture:

The scanner module uses the Reflections library to find classes at runtime that match specific criteria.

#### Key Components:

1. **TypeDiscovery<T>** - Interface for discovering types
2. **DiscoveryPredicate<T>** - Filtering logic
3. **DiscoveryConfig** - Configuration (packages to scan, filters)
4. **ReflectionsTypeScanner** - Implementation using Reflections library

#### How Used:

```kotlin
// Find all concrete Repository implementations in specific packages
val predicate: DiscoveryPredicate<Repository<*, *>> = { candidate ->
    !Modifier.isAbstract(candidate.modifiers) && !candidate.isInterface
}

val config = DiscoveryConfig(
    scanPackages = listOf("com.example.app"),
    predicate = predicate
)

val scanner = ReflectionsTypeScanner(Repository::class.java, config)
val repositories = scanner.discover()  // Set<Class<out Repository>>
```

---

## 3. Key Classes & Their Responsibilities

### Core Abstraction Classes

#### 1. Component Interface
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-core/src/main/kotlin/com/ead/katalyst/common/Component.kt`

**Purpose**: Marker interface for framework-managed components.

**Usage**: Implement this to make a class eligible for automatic discovery and DI:
```kotlin
// Any business logic component
class UserValidator : Validator<User> {
    // Automatically discovered and registered
}

class NotificationService : Component {
    // Automatically discovered and registered
}
```

#### 2. Service Interface
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-core/src/main/kotlin/com/ead/katalyst/services/Service.kt`

**Purpose**: Base interface for service classes with automatic transaction manager injection.

**Properties**:
- Extends `Component` and `KoinComponent`
- Provides `transactionManager` property that's automatically injected
- Inherits from `KoinComponent` for manual Koin access

**Usage**:
```kotlin
class UserService(
    private val repository: UserRepository
) : Service {
    suspend fun createUser(dto: CreateUserDTO): User {
        return transactionManager.transaction {
            // All repository calls here use same transaction
            repository.save(User.from(dto))
        }
    }
}
```

#### 3. Validator Interface
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-core/src/main/kotlin/com/ead/katalyst/validators/Validator.kt`

**Purpose**: Strongly-typed validation abstraction for domain entities.

**Methods**:
- `suspend fun validate(entity: T): ValidationResult`
- `suspend fun getValidationErrors(entity: T): List<String>`

**Usage**:
```kotlin
class UserValidator : Validator<User> {
    override suspend fun validate(entity: User): ValidationResult {
        val errors = mutableListOf<String>()
        if (entity.name.isEmpty()) {
            errors.add("Name cannot be empty")
        }
        if (!isValidEmail(entity.email)) {
            errors.add("Invalid email format")
        }
        return if (errors.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(*errors.toTypedArray())
        }
    }
}
```

#### 4. EventHandler & DomainEvent
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-core/src/main/kotlin/com/ead/katalyst/events/Event.kt`

**Purpose**: Event-driven architecture support.

**Classes**:
- `DomainEvent` - Base interface for events
- `EventHandler<T>` - Functional interface for handling events
- `EventDispatcher` - Lightweight pub/sub system

**Usage**:
```kotlin
// Define an event
data class UserCreatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
    val userId: Long,
    val email: String
) : DomainEvent

// Define a handler
class EmailNotificationHandler : EventHandler<UserCreatedEvent> {
    override suspend fun handle(event: UserCreatedEvent) {
        sendWelcomeEmail(event.email)
    }
}

// In service:
transactionManager.transaction {
    val user = repository.save(newUser)
    eventDispatcher.dispatch(UserCreatedEvent(userId = user.id!!, email = user.email))
}
```

#### 5. KtorModule Interface
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/katalyst-ktor-support/src/main/kotlin/com/ead/katalyst/routes/KtorModule.kt`

**Purpose**: Plugin interface for custom Ktor configuration.

**Methods**:
- `fun install(application: Application)` - Called to install the module
- `val order: Int` - Execution order (default 0)

**Usage**:
```kotlin
class CustomAuthModule : KtorModule {
    override val order = 1  // Install second
    
    override fun install(application: Application) {
        application.install(Authentication) {
            jwt { /* jwt config */ }
        }
    }
}
```

### DI & Auto-Discovery Classes

#### AutoBindingRegistrar
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/dependency-injection/src/main/kotlin/com/ead/katalyst/di/internal/AutoBindingRegistrar.kt`

**Purpose**: Discovers components and registers them with Koin.

**Process**:
1. Discovers concrete implementations of:
   - `Repository`
   - `Component` and subclasses
   - `KtorModule`
   - `EventHandler`
2. For each discovered class:
   - Analyzes constructor parameters
   - Resolves dependencies from Koin
   - Instantiates with dependencies
   - Injects well-known properties (DatabaseTransactionManager, SchedulerService)
   - Registers in Koin with primary and secondary type bindings
3. Defers registration of classes with unresolved dependencies
4. Retries deferred registrations after other classes succeed

#### KatalystApplicationBuilder
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/dependency-injection/src/main/kotlin/com/ead/katalyst/di/KatalystApplication.kt`

**Purpose**: Builder for configuring and starting the application.

**Methods**:
- `database(config)` - Set database configuration
- `scanPackages(vararg packages)` - Set packages to scan
- `enableScheduler()` - Enable task scheduling
- `withServerConfig(config)` - Set server engine configuration

**Responsible For**:
- Initializing Koin DI
- Setting up Ktor application
- Installing all discovered KtorModules
- Handling application startup/shutdown

---

## 4. Exception Handling & Routing Integration

### Exception Handling Flow

```
1. User makes HTTP request
    ↓
2. Route handler executes
    ↓
3. Throws exception (e.g., ValidationException)
    ↓
4. Ktor's StatusPages plugin catches exception
    ↓
5. Looks up registered handler for this exception type
    ↓
6. Handler executes: call.respond(HttpStatusCode.BadRequest, errorResponse)
    ↓
7. HTTP response sent to client
```

### Example: Complete Exception Handling Setup

```kotlin
// 1. Define custom exceptions
class ValidationException(val errors: List<String>) : Exception()
class NotFoundException(message: String) : Exception(message)
class UnauthorizedException(message: String) : Exception(message)

// 2. Register exception handlers
fun Route.exceptionHandlers() = katalystExceptionHandler {
    exception<ValidationException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("errors" to cause.errors)
        )
    }
    
    exception<NotFoundException> { call, cause ->
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("message" to cause.message)
        )
    }
    
    exception<UnauthorizedException> { call, cause ->
        call.respond(
            HttpStatusCode.Unauthorized,
            mapOf("message" to cause.message)
        )
    }
    
    exception<Throwable> { call, cause ->
        logger.error("Unexpected exception", cause)
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "Internal server error")
        )
    }
}

// 3. Use in service (throw exceptions)
class UserService(
    private val repository: UserRepository
) : Service {
    suspend fun getUser(id: Long): User = transactionManager.transaction {
        repository.findById(id) 
            ?: throw NotFoundException("User with id=$id not found")
    }
}

// 4. Use in route (exception is caught and handled automatically)
fun Route.userRoutes() = katalystRouting {
    get("/api/users/{id}") {
        val userService = call.inject<UserService>()
        val id = call.parameters["id"]?.toLong()
            ?: throw ValidationException(listOf("Invalid user ID"))
        
        val user = userService.getUser(id)  // May throw NotFoundException
        call.respond(user)  // Exception caught by StatusPages
    }
}
```

### How Routing & Exception Handling Work Together

```kotlin
// In Application.module()
fun Application.module() {
    // Install content negotiation
    install(ContentNegotiation) { json() }
    
    // Register exception handlers FIRST
    routing {
        exceptionHandlers()  // Sets up StatusPages handlers
        userRoutes()         // Registers routes
        productRoutes()
        adminRoutes()
    }
}

// When a request comes in:
// 1. Route handler executes
// 2. If exception thrown, StatusPages plugin intercepts
// 3. Finds matching exception handler
// 4. Handler calls call.respond() with error response
// 5. ContentNegotiation serializes to JSON
// 6. JSON response sent to client
```

---

## 5. Scanner Module Deep Dive

### Type Discovery Process

**Goal**: Find all classes in specific packages that implement a marker interface.

**Implementation**:

```kotlin
// Step 1: Create predicates (filters)
val isConcreteClass: DiscoveryPredicate<Repository<*, *>> = { candidate ->
    !Modifier.isAbstract(candidate.modifiers) && !candidate.isInterface
}

// Step 2: Configure discovery
val config = DiscoveryConfig(
    scanPackages = listOf("com.example.app"),
    predicate = isConcreteClass
)

// Step 3: Create scanner
val scanner = ReflectionsTypeScanner(Repository::class.java, config)

// Step 4: Discover
val repositories: Set<Class<out Repository<*, *>>> = scanner.discover()
```

### Used By AutoBindingRegistrar

The scanner is used to find:
1. All `Repository` implementations → Register in Koin
2. All `Component` implementations → Register in Koin
3. All `KtorModule` implementations → Register and install
4. All `EventHandler<*>` implementations → Register in Koin
5. All route extension functions (`*Kt` files) → Wrap and install

---

## 6. Configuration Files

### Root build.gradle.kts
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/build.gradle.kts`

Configures the example application with:
- Ktor dependencies
- Database libraries (Exposed, HikariCP, PostgreSQL, H2)
- Koin DI framework
- Serialization

### settings.gradle.kts
**File**: `/Users/darkryh/Desktop/Libraries/Backend/katalyst/settings.gradle.kts`

Defines all library modules:
```gradle
include("scanner")
include("katalyst-ktor-support")
include("dependency-injection")
include("katalyst-core")
include("katalyst-persistence")
include("katalyst-scheduler")
```

### Module build.gradle.kts files

Each module has its own `build.gradle.kts`:
- `dependency-injection/` - DI orchestration
- `katalyst-core/` - Core abstractions only
- `katalyst-persistence/` - Database/repository
- `katalyst-ktor-support/` - Ktor routing
- `katalyst-scheduler/` - Task scheduling
- `scanner/` - Type discovery

---

## 7. Example Application Walkthrough

The `/Users/darkryh/Desktop/Libraries/Backend/katalyst/src/` directory contains a complete example application demonstrating all Katalyst features.

### Application.kt - Entry Point

```kotlin
fun main(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.fromEnvironment())
    scanPackages("com.ead.katalyst.example")
    enableScheduler()
}

fun Application.module() {
    configureHttp()
}
```

**What happens**:
1. `katalystApplication` initializes Koin and starts the server
2. Database config is loaded from environment
3. Packages are scanned for components
4. Scheduler is enabled
5. `Application.module()` is called to set up Ktor features
6. All discovered components are available for injection

### UserService.kt - Service Implementation

```kotlin
class UserService(
    private val userRepository: UserRepository,
    private val userValidator: UserValidator
) : Service {
    
    private val scheduler = requireScheduler()
    
    init {
        scheduleRemoveInactiveUsers()
    }
    
    suspend fun createUser(request: CreateUserRequest): User {
        return transactionManager.transaction {
            userValidator.validate(request)  // Validate
            
            if (userRepository.findByEmail(request.email) != null) {
                throw UserExampleValidationException("Email already exists")
            }
            
            userRepository.save(UserEntity(...)).toUser()
        }
    }
    
    // Schedule background cleanup task
    private fun scheduleRemoveInactiveUsers() {
        scheduler.scheduleFixedDelay(
            taskName = "users.cleanup-inactive",
            task = { removeInactiveUsers() },
            initialDelay = 5.minutes,
            fixedDelay = 60.minutes
        )
    }
}
```

**Key Points**:
- Extends `Service` for auto-discovery and transaction manager injection
- Takes repositories and validators as constructor parameters
- Wraps repository calls in `transactionManager.transaction {}`
- Uses injected scheduler for background tasks
- Throws custom exceptions for error handling

### UserRoutes.kt - Route Definition

```kotlin
fun Route.userRoutes() = katalystRouting {
    route("/api/users") {
        post {
            val userService = call.inject<UserService>()
            val request = call.receive<CreateUserRequest>()
            val created = userService.createUser(request)
            call.respond(HttpStatusCode.Created, UserResponse.from(created))
        }
        
        get {
            val userService = call.inject<UserService>()
            val users = userService.listUsers()
            call.respond(users.map(UserResponse::from))
        }
        
        get("/{id}") {
            val userService = call.inject<UserService>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val user = userService.getUser(id)
            call.respond(UserResponse.from(user))
        }
    }
}
```

**Automatically Discovered & Invoked**:
- Function name `userRoutes` matches pattern
- Extends `Route`
- Located in scanned package `com.ead.katalyst.example`
- Automatically instantiated and called during startup

### ExceptionHandler.kt - Exception Mapping

```kotlin
fun Route.exceptionHandlers() = katalystExceptionHandler {
    exception<UserExampleValidationException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "VALIDATION_ERROR", "message" to cause.message)
        )
    }
}
```

**Automatically Discovered & Registered**:
- Sets up Ktor's StatusPages plugin
- Maps custom exceptions to HTTP responses

### UserRepository.kt - Data Access

```kotlin
class UserRepository : Repository<Long, UserEntity> {
    override val table = UsersTable
    
    override fun mapper(row: ResultRow): UserEntity =
        UserEntity(
            id = row[table.id].value,
            name = row[table.name],
            email = row[table.email]
        )
    
    fun findByEmail(email: String): UserEntity? =
        table.selectAll()
            .where { table.email eq email }
            .firstOrNull()
            ?.let { mapper(it) }
}
```

**Automatically Discovered & Registered**:
- Implements `Repository<Long, UserEntity>`
- Has `table` and `mapper` required by interface
- Provides custom `findByEmail` method
- Available for service injection

---

## 8. Design Patterns & Architecture

### 1. Marker Interface Pattern
Components use marker interfaces (`Component`, `Service`, `Repository`, etc.) for automatic discovery and registration. This follows the Spring Boot pattern.

### 2. Dependency Injection Pattern
Constructor injection via Koin. The framework:
- Analyzes constructors
- Resolves types from Koin
- Instantiates with dependencies
- Registers in Koin

### 3. Service Locator Pattern
Services can access Koin via `KoinComponent` interface:
```kotlin
class MyService : Service {
    fun doSomething() {
        val other = getKoin().get<OtherService>()
    }
}
```

### 4. Repository Pattern
Data access is abstracted through generic `Repository<Id, Entity>` interface with automatic CRUD implementation.

### 5. Transaction Script Pattern
Each service method wraps database operations in a transaction:
```kotlin
suspend fun createUser(dto: DTO): User {
    return transactionManager.transaction {
        // All database calls here are in same transaction
    }
}
```

### 6. Exception Mapping Pattern
Custom exceptions are caught and mapped to HTTP responses via exception handlers:
```kotlin
exception<ValidationException> { call, cause ->
    call.respond(HttpStatusCode.BadRequest, errorResponse)
}
```

### 7. Plugin Pattern
`KtorModule` interface allows plugins to extend the Ktor application:
```kotlin
class CustomAuthModule : KtorModule {
    override fun install(application: Application) { ... }
}
```

---

## 9. How the Framework Initializes

### Complete Startup Sequence

```
1. main() calls katalystApplication { ... }
   ├─ Builder captures configuration
   ├─ Initializes Koin with core modules
   │  ├─ DatabaseModule (HikariCP, connection pool)
   │  ├─ ScannerModule (Reflections library)
   │  └─ SchedulerModule (if enabled)
   │
   ├─ AutoBindingRegistrar.registerAll()
   │  ├─ Discovers Repository implementations
   │  │  └─ Instantiates with dependencies
   │  │  └─ Registers in Koin
   │  │
   │  ├─ Discovers Component implementations
   │  │  ├─ Analyzes constructors
   │  │  ├─ Resolves dependencies
   │  │  ├─ Instantiates
   │  │  ├─ Injects well-known properties
   │  │  └─ Registers in Koin
   │  │
   │  ├─ Discovers KtorModule implementations
   │  │  └─ Wraps in holder for later installation
   │  │
   │  ├─ Discovers EventHandler implementations
   │  │  └─ Registers in Koin
   │  │
   │  └─ Discovers route extension functions
   │     └─ Wraps in RouteFunctionModule
   │
   ├─ Creates embedded Ktor server
   │
   └─ Ktor server starts
      │
      ├─ ApplicationStarting event fires
      │  ├─ Application.module() is called
      │  │  └─ Calls configureHttp(), etc.
      │  │
      │  └─ KtalystApplication installs KtorModules
      │     ├─ Sorts by order
      │     ├─ Calls module.install(application) for each
      │     │  ├─ Route extension functions are invoked
      │     │  ├─ Exception handlers are registered
      │     │  └─ Any custom modules install themselves
      │     └─ Application ready to serve requests
      │
      ├─ Server listens on configured port
      │
      └─ Ready to receive requests

2. HTTP Request arrives
   ├─ Route handler is invoked
   ├─ call.inject<Service>() resolves from Koin
   ├─ Service method executes
   ├─ Repositories are called within transaction
   ├─ If exception, StatusPages catches and handler responds
   └─ Response sent to client

3. Shutdown
   ├─ ApplicationStopping event fires
   ├─ Koin DI is stopped
   └─ Server stops
```

---

## 10. Key Files Quick Reference

### Core Abstractions
- `katalyst-core/src/main/kotlin/com/ead/katalyst/common/Component.kt` - Component marker
- `katalyst-core/src/main/kotlin/com/ead/katalyst/services/Service.kt` - Service base
- `katalyst-core/src/main/kotlin/com/ead/katalyst/validators/Validator.kt` - Validator interface
- `katalyst-core/src/main/kotlin/com/ead/katalyst/events/Event.kt` - Event system
- `katalyst-core/src/main/kotlin/com/ead/katalyst/database/DatabaseTransactionManager.kt` - Transactions

### Dependency Injection
- `dependency-injection/src/main/kotlin/com/ead/katalyst/di/DIConfiguration.kt` - DI setup
- `dependency-injection/src/main/kotlin/com/ead/katalyst/di/KatalystApplication.kt` - App builder
- `dependency-injection/src/main/kotlin/com/ead/katalyst/di/internal/AutoBindingRegistrar.kt` - Auto-discovery

### Routing & Ktor
- `katalyst-ktor-support/src/main/kotlin/com/ead/katalyst/routes/RoutingBuilder.kt` - Routing DSL
- `katalyst-ktor-support/src/main/kotlin/com/ead/katalyst/routes/ExceptionHandlerBuilder.kt` - Exception mapping
- `katalyst-ktor-support/src/main/kotlin/com/ead/katalyst/routes/KoinRouteExtensions.kt` - DI in routes
- `katalyst-ktor-support/src/main/kotlin/com/ead/katalyst/routes/KtorModule.kt` - Plugin interface

### Database & Persistence
- `katalyst-persistence/src/main/kotlin/com/ead/katalyst/database/DatabaseConfig.kt` - DB config
- `katalyst-persistence/src/main/kotlin/com/ead/katalyst/database/DatabaseFactory.kt` - Connection setup
- `katalyst-persistence/src/main/kotlin/com/ead/katalyst/repositories/Repository.kt` - Generic CRUD

### Scanner
- `scanner/src/main/kotlin/com/ead/katalyst/scanner/core/TypeDiscovery.kt` - Discovery interface
- `scanner/src/main/kotlin/com/ead/katalyst/scanner/integration/AutoDiscoveryEngine.kt` - Discovery engine

### Scheduler
- `katalyst-scheduler/src/main/kotlin/com/ead/katalyst/scheduler/SchedulerModules.kt` - Scheduler setup
- `katalyst-scheduler/src/main/kotlin/com/ead/katalyst/services/service/SchedulerService.kt` - Task API

### Example Application
- `src/main/kotlin/com/ead/katalyst/example/Application.kt` - Entry point
- `src/main/kotlin/com/ead/katalyst/example/domain/UserService.kt` - Service implementation
- `src/main/kotlin/com/ead/katalyst/example/routes/UserRoutes.kt` - Route definition
- `src/main/kotlin/com/ead/katalyst/example/exceptionHandler/ExceptionHandler.kt` - Exception mapping

---

## Summary

**Katalyst** is a comprehensive Kotlin backend framework that automates:

1. **Dependency Injection** - Automatic discovery and registration of components
2. **Exception Handling** - Mapping custom exceptions to HTTP responses
3. **Routing** - Auto-discovery of route handlers
4. **Database Transactions** - Suspend-based transaction management with automatic commit/rollback
5. **Repositories** - Generic CRUD patterns for Exposed tables
6. **Event Handling** - Pub/sub system for domain events
7. **Task Scheduling** - Background job scheduling
8. **Type Discovery** - Reflection-based component finding

By leveraging Kotlin's coroutines, Ktor's routing system, Koin's DI framework, and Exposed's database abstraction, Katalyst enables developers to build REST APIs rapidly with minimal boilerplate while maintaining strong type safety and clean architecture patterns.
