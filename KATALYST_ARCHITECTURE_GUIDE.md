# Katalyst Architecture & Patterns Guide

## Complete Request Lifecycle

```
HTTP Request Arrives
    |
    v
Ktor Server (Netty by default)
    |
    v
Routing System
    |
    v
Route Handler (e.g., POST /api/users)
    |
    +---> call.inject<UserService>() [from Koin DI]
    |         |
    |         v
    |     UserService instance
    |         |
    |         v
    |     transactionManager.transaction { ... }
    |         |
    |         +---> userRepository.findByEmail(email) [sync]
    |         +---> userValidator.validate(request) [sync]
    |         +---> userRepository.save(entity) [sync]
    |         |
    |         +---> On success: COMMIT
    |         +---> On exception: ROLLBACK
    |
    v
Return value / throw exception
    |
    v (if exception)
Ktor StatusPages Plugin catches exception
    |
    v
Look up exception handler (from katalystExceptionHandler block)
    |
    v
Handler calls: call.respond(HttpStatusCode.XXX, responseBody)
    |
    v
ContentNegotiation serializes to JSON
    |
    v
HTTP Response sent to client
```

---

## Dependency Injection & Component Discovery Flow

### Startup Initialization

```
main(args)
    |
    v
katalystApplication(args) { ... }
    |
    +---> KatalystApplicationBuilder.build()
    |     |
    |     +---> database(config)
    |     +---> scanPackages("com.example")
    |     +---> enableScheduler()
    |
    v
bootstrapKatalystDI()
    |
    +---> Load CoreDIModule
    |     |
    |     +---> HikariDataSource (connection pooling)
    |     +---> Exposed Database connection
    |     +---> DatabaseTransactionManager
    |
    +---> Load ScannerDIModule
    |     |
    |     +---> Reflections library setup
    |
    +---> Load SchedulerDIModule (if enabled)
    |     |
    |     +---> SchedulerService
    |
    v
AutoBindingRegistrar.registerAll()
    |
    +---> discoverConcreteTypes(Repository::class)
    |     |
    |     v
    |     ReflectionsTypeScanner finds all Repository implementations
    |     in scanned packages
    |     |
    |     +---> UserRepository (implements Repository<Long, UserEntity>)
    |     +---> ProductRepository (implements Repository<Long, ProductEntity>)
    |     +---> etc.
    |
    +---> For each Repository:
    |     |
    |     +---> Analyze constructor
    |     +---> Resolve dependencies (none typically for repos)
    |     +---> Instantiate
    |     +---> Register in Koin (as Repository, also as any secondary types)
    |
    +---> discoverConcreteTypes(Component::class)
    |     |
    |     v
    |     Reflections finds all Component/Service/Validator implementations
    |     |
    |     +---> UserService (depends on UserRepository, UserValidator)
    |     +---> UserValidator (depends on nothing)
    |     +---> NotificationService (depends on MailProvider)
    |     +---> etc.
    |
    +---> For each Component:
    |     |
    |     +---> Analyze constructor parameters
    |     +---> For each parameter:
    |     |     |
    |     |     +---> Try to resolve from Koin
    |     |     +---> If found: use instance
    |     |     +---> If not found: defer or error
    |     |
    |     +---> Instantiate with all resolved dependencies
    |     +---> Inject well-known properties:
    |     |     |
    |     |     +---> DatabaseTransactionManager (for Services)
    |     |     +---> SchedulerService (if enabled)
    |     |
    |     +---> Register in Koin
    |     |     |
    |     |     +---> Primary type: UserService
    |     |     +---> Secondary types: Service, Component, KoinComponent
    |
    +---> discoverConcreteTypes(KtorModule::class)
    |     |
    |     +---> CustomAuthModule
    |     +---> etc.
    |     |
    |     v
    |     Wrap each in KtorModuleRegistry for later installation
    |
    +---> discoverConcreteTypes(EventHandler::class)
    |     |
    |     +---> UserCreatedEventHandler
    |     +---> UserDeletedEventHandler
    |     +---> etc.
    |     |
    |     v
    |     Register in Koin
    |
    +---> discoverRouteFunctions()
    |     |
    |     v
    |     Find all static methods in *Kt files that:
    |     - Take Application or Route as first parameter
    |     - Return Unit/Void
    |     |
    |     +---> fun Route.userRoutes() = katalystRouting { ... }
    |     +---> fun Route.productRoutes() = katalystRouting { ... }
    |     +---> fun Application.exceptionHandlers() = katalystExceptionHandler { ... }
    |     +---> etc.
    |     |
    |     v
    |     Wrap each in RouteFunctionModule
    |
    v
Create Embedded Ktor Server (Netty)
    |
    v
Server starts and fires ApplicationStarting event
    |
    v
Application.module() is called
    |
    +---> install(ContentNegotiation) { json() }
    +---> etc.
    |
    v
Install all KtorModules in order
    |
    +---> For each KtorModuleRegistry entry:
    |     |
    |     v
    |     If RouteFunctionModule:
    |     |
    |     +---> Invoke the route extension function
    |     |     e.g., userRoutes() with Route as receiver
    |     |
    |     +---> Function executes: katalystRouting { ... }
    |     |     |
    |     |     v
    |     |     Routes are registered in Ktor routing tree
    |     |
    |     Else if other KtorModule:
    |     |
    |     +---> Call module.install(application)
    |     |
    |     v
    |     Exception handlers, auth, etc. are installed
    |
    v
Server listening on port 8080
    |
    v
Ready to accept requests
```

---

## Auto-Discovery Component Resolution Order

The framework must resolve components in dependency order:

### Example Dependency Chain

```
UserService
    depends on:
    - UserRepository
    - UserValidator

UserValidator
    depends on:
    - nothing

UserRepository
    depends on:
    - nothing

DatabaseTransactionManager
    depends on:
    - DatabaseFactory (created by core module)
```

### Resolution Algorithm

```
1. First pass: Find all concrete implementations
   |
   v
2. For each implementation:
   |
   +---> Try to instantiate
   |     |
   |     +---> Resolve all constructor dependencies
   |     |     |
   |     |     +---> If all found in Koin: SUCCESS
   |     |     |
   |     |     +---> If some missing: DEFER
   |     |
   |     +---> If success: Register in Koin
   |     |
   |     +---> If defer: Add to pending list
   |
   v
3. Check for progress
   |
   +---> If progress made: Retry pending classes
   +---> If no progress: Log errors for unresolvable classes
   |
   v
4. All resolvable components now registered
```

### Example with Multiple Rounds

**Round 1:**
- Try UserRepository → Success (no deps) → Register
- Try UserValidator → Success (no deps) → Register
- Try UserService → Deferred (needs both repos/validators, might try before they're registered)

**Round 2:**
- Try UserService → Success (both dependencies now in Koin) → Register

**Result:**
All components registered in correct dependency order.

---

## Service-to-Repository Transaction Flow

### Sequence Diagram

```
Client Request (HTTP)
    |
    v
Route Handler
    |
    +--> val service = call.inject<UserService>()
    |
    v
Route handler calls service method: service.createUser(dto)
    |
    v
UserService.createUser()
    |
    +--> transactionManager.transaction {
    |    |
    |    +---> Open database connection from HikariCP pool
    |    +---> Begin transaction
    |    |
    |    +---> [Inside transaction block]
    |    |
    |    +---> val existing = userRepository.findByEmail(dto.email)
    |    |     |
    |    |     +---> SELECT * FROM users WHERE email = ?
    |    |     +---> Uses connection from transaction context
    |    |     +---> Returns UserEntity or null
    |    |
    |    +---> if (existing != null) throw DuplicateEmailException()
    |    |     |
    |    |     v
    |    |     Transaction ROLLS BACK
    |    |     Connection returned to pool
    |    |
    |    +---> val newUser = userRepository.save(UserEntity(...))
    |    |     |
    |    |     +---> INSERT INTO users (name, email) VALUES (?, ?)
    |    |     +---> Uses transaction connection
    |    |     +---> SELECT * FROM users WHERE id = ? [reload]
    |    |     +---> Returns inserted UserEntity
    |    |
    |    +---> return newUser
    |    |
    |    +---> [End of block]
    |    |
    |    +---> Transaction COMMITS
    |    +---> Connection returned to pool
    |
    v
Route handler receives User object
    |
    v
call.respond(HttpStatusCode.Created, userResponse)
    |
    v
HTTP Response sent to client with 201 Created
```

### Error Scenario

```
Inside transaction block:
    |
    +---> userRepository.findByEmail(email)
    +---> if (existing != null) throw ValidationException("Email exists")
    |
    v
ValidationException thrown
    |
    v
Exposed transaction catches exception
    |
    v
Database transaction ROLLS BACK
    |
    v
Exception propagates to route handler
    |
    v
Ktor StatusPages catches exception
    |
    v
Exception handler for ValidationException executes
    |
    v
call.respond(HttpStatusCode.BadRequest, errorResponse)
    |
    v
HTTP Response sent to client with 400 Bad Request
```

---

## Route Discovery & Invocation

### How Routes Are Found

```
During AutoBindingRegistrar initialization:

1. Get all scanned packages
   |
   v
2. Use Reflections to find all class names in packages
   |
   v
3. Filter to only *Kt files (Kotlin top-level functions)
   |
   v
4. Load each Kt class and get all static methods
   |
   v
5. Filter methods by:
   - Modifier.isStatic() ✓
   - methodParameterTypes[0] is Application or Route ✓
   - return type is Unit or Void ✓
   |
   v
6. For each matching method:
   |
   +---> Create RouteFunctionModule wrapper
   +---> Register in KtorModuleRegistry
   |
   v
7. Later, when KtorModules installed:
   |
   +---> For each RouteFunctionModule:
   |     |
   |     +---> Invoke the method
   |     |     |
   |     |     +---> If Application parameter:
   |     |     |     method.invoke(null, application)
   |     |     |
   |     |     +---> If Route parameter:
   |     |           application.routing {
   |     |               method.invoke(null, this)  // 'this' is Route
   |     |           }
   |     |
   |     v
   |     Function executes, registers routes
```

### Example Route Function Discovery

```kotlin
// File: com/example/routes/UserRoutesKt.java (compiled Kotlin)
// Original Kotlin source:
fun Route.userRoutes() = katalystRouting { ... }

// Reflections finds this as:
- Class name: "com.example.routes.UserRoutesKt"
- Method name: "userRoutes"
- Parameters: [Route]
- Static: Yes
- Return type: Unit

// RouteFunctionModule wraps it:
val module = RouteFunctionModule(method)
module.install(application) {
    // Invokes the method with Route as parameter
    application.routing {
        method.invoke(null, this)  // 'this' is the Route receiver
    }
}

// Inside userRoutes(), katalystRouting { } registers all the routes
```

---

## Exception Handling Pipeline

### Setup Phase

```
During route discovery:
    |
    v
fun Route.exceptionHandlers() = katalystExceptionHandler { ... }
is found and invoked
    |
    v
ExceptionHandlerBuilder creates handlers
    |
    +---> exception<ValidationException> { ... }
    +---> exception<NotFoundException> { ... }
    +---> exception<Throwable> { ... }
    |
    v
For each exception handler:
    |
    +---> Create registration lambda
    +---> Add to ExceptionHandlerBuilder.registrations list
    |
    v
builder.build()
    |
    v
Call install(StatusPages) { ... }
    |
    v
For each registration in list:
    |
    +---> Register with Ktor's StatusPages plugin
    |
    v
StatusPages plugin now has all exception handlers
```

### Runtime Phase

```
Route handler executes
    |
    v
Throws exception: throw NotFoundException("User not found")
    |
    v
Exception propagates up the call stack
    |
    v
Ktor catches exception (outside route handler)
    |
    v
StatusPages plugin checks its registry
    |
    +--> exception<NotFoundException> { call, cause ->
    |    |
    |    v
    |    Match found! Execute handler
    |    |
    |    v
    |    call.respond(HttpStatusCode.NotFound, ...)
    |
    +---> Not found: Check parent types
    |    |
    |    +--> exception<Throwable> { call, cause ->
    |        |
    |        v
    |        Execute catch-all handler
    |
    v
Handler completes
    |
    v
HTTP response sent to client
```

---

## Component Hierarchy & Type Bindings

### Marker Interfaces

```
Component (top-level marker)
    |
    +---> Service (for business logic)
    |     |
    |     +---> Extends: Component, KoinComponent
    |     +---> Properties: transactionManager (injected)
    |     +---> Usage: Implement for business logic with DB access
    |
    +---> Validator<T> (for validation)
    |     |
    |     +---> Generic type T
    |     +---> Methods: validate(), getValidationErrors()
    |     +---> Usage: Implement for domain entity validation
    |
    +---> [Other Components]
         (Any class implementing Component)

Repository<Id, Entity> (NOT a Component)
    |
    +---> Generic types: Id (primary key), Entity
    +---> Methods: save(), findById(), findAll(), delete(), count()
    +---> Automatically discovered and registered
    +---> Usage: Implement for data access

KtorModule (NOT a Component)
    |
    +---> Method: install(application)
    +---> Property: order (execution order)
    +---> Automatically discovered and installed in order
    +---> Usage: Implement for custom Ktor features

EventHandler<T> (NOT required to extend Component)
    |
    +---> Generic type T extends DomainEvent
    +---> Method: suspend fun handle(event: T)
    +---> Automatically discovered and registered
    +---> Usage: Implement to handle domain events
```

### Type Binding in Koin

When a class is registered:

```kotlin
class UserService(
    private val repo: UserRepository
) : Service

// Koin binds it as:
- PRIMARY: UserService
- SECONDARY: Service
- SECONDARY: Component
- SECONDARY: KoinComponent

// So it can be injected as:
- call.inject<UserService>() ✓
- call.inject<Service>() ✓ (if UserService is only Service in Koin)
- call.inject<Component>() ✗ (ambiguous - multiple components)
- call.inject<KoinComponent>() ✗ (ambiguous - all services are this)
```

---

## Configuration & Environment

### Database Configuration

```
DatabaseConfig
    |
    +---> url: String (JDBC URL)
    |     Examples:
    |     - "jdbc:postgresql://localhost:5432/mydb"
    |     - "jdbc:h2:mem:test"
    |     - "jdbc:mysql://localhost:3306/mydb"
    |
    +---> driver: String (JDBC driver class)
    |     Examples:
    |     - "org.postgresql.Driver"
    |     - "org.h2.Driver"
    |     - "com.mysql.cj.jdbc.Driver"
    |
    +---> username: String
    +---> password: String
    |
    +---> Connection Pool Settings
    |     |
    |     +---> maxPoolSize: Int (default 10)
    |     +---> minIdleConnections: Int (default 2)
    |     +---> connectionTimeout: Long (default 30000ms)
    |     +---> idleTimeout: Long (default 600000ms = 10 min)
    |     +---> maxLifetime: Long (default 1800000ms = 30 min)
    |
    +---> Transaction Settings
          |
          +---> autoCommit: Boolean (default false)
          +---> transactionIsolation: String (default REPEATABLE_READ)
```

### Server Configuration

```
ServerConfiguration
    |
    +---> engineType: String
    |     |
    |     +---> "netty" (default)
    |     +---> "jetty"
    |     +---> "cio"
    |
    +---> applicationWrapper: ((Application) -> Application)?
    |     Custom preprocessing of Application
    |
    +---> serverWrapper: ((ApplicationEngine) -> Unit)?
          Custom preprocessing of ApplicationEngine
```

---

## Performance Considerations

### Connection Pooling

```
Client Request #1
    |
    v
Needs DB connection
    |
    v
HikariCP provides connection from pool
    |
    v
Execute transaction
    |
    v
Connection returned to pool

Client Request #2 (concurrent or later)
    |
    v
Needs DB connection
    |
    v
HikariCP provides connection from pool (reuses #1's connection if available)
    |
    v
Execute transaction
    |
    v
Connection returned to pool

Benefits:
- Avoid creating new connections (expensive)
- Reuse connections
- Configurable pool size
- Timeout management
```

### Transaction Isolation

Default: `TRANSACTION_REPEATABLE_READ`

This means:
- Dirty reads: Prevented
- Non-repeatable reads: Prevented
- Phantom reads: Not prevented

For more strict isolation: Set to `TRANSACTION_SERIALIZABLE`
For less strict (faster): Set to `TRANSACTION_READ_COMMITTED`

---

## Best Practices

### Service Implementation

```kotlin
// Good
class UserService(
    private val userRepository: UserRepository,
    private val userValidator: UserValidator
) : Service {
    suspend fun createUser(dto: CreateUserDTO): User {
        userValidator.validate(dto)  // Validate first
        
        return transactionManager.transaction {
            // All DB calls in one transaction
            if (userRepository.findByEmail(dto.email) != null) {
                throw DuplicateException("Email exists")
            }
            userRepository.save(User.from(dto))
        }
    }
}

// Bad: Transaction not used
class UserService : Service {
    fun createUser(dto: CreateUserDTO): User {
        userRepository.save(User.from(dto))  // Not in transaction!
    }
}

// Bad: Mixing concerns
class UserService : Service {
    suspend fun createUser(dto: CreateUserDTO): User {
        // Validation inside transaction - creates unnecessary locks
        transactionManager.transaction {
            userValidator.validate(dto)  // Should be outside
            userRepository.save(User.from(dto))
        }
    }
}
```

### Exception Handling

```kotlin
// Good: Specific exceptions
class UserService : Service {
    suspend fun getUser(id: Long): User = transactionManager.transaction {
        userRepository.findById(id) 
            ?: throw NotFoundException("User $id not found")
    }
}

// Bad: Generic exceptions
class UserService : Service {
    suspend fun getUser(id: Long): User = transactionManager.transaction {
        userRepository.findById(id) 
            ?: throw Exception("Error")  // Too generic
    }
}
```

### Route Definitions

```kotlin
// Good: Grouped by resource
fun Route.userRoutes() = katalystRouting {
    route("/api/users") {
        get { /* list */ }
        post { /* create */ }
        get("/{id}") { /* get */ }
        put("/{id}") { /* update */ }
        delete("/{id}") { /* delete */ }
    }
}

// Bad: Each HTTP verb in separate function
fun Route.listUsers() { get("/api/users") { ... } }
fun Route.createUser() { post("/api/users") { ... } }
fun Route.getUser() { get("/api/users/{id}") { ... } }
```

---

## Troubleshooting

### Component Not Discovered

**Symptom**: `Cannot resolve dependency X for Y`

**Causes**:
1. Class doesn't implement Component/Service/Repository
2. Package not in `scanPackages`
3. Constructor parameter type doesn't match registered type
4. Circular dependency

**Solution**:
```kotlin
// Ensure class implements interface
class MyService : Service {  // Good
class MyService {  // Bad - won't be discovered
}

// Check scanPackages includes package
scanPackages("com.example.app")  // Finds com.example.app.*

// Verify dependency exists
class UserService(
    private val repo: UserRepository  // Must be discoverable
) : Service
```

### Route Not Registered

**Symptom**: 404 error when accessing route

**Causes**:
1. Function not in scanned package
2. Function not named correctly
3. Package not scanned
4. Exception thrown during route registration

**Solution**:
```kotlin
// Must be in scanned package
fun Route.userRoutes() = katalystRouting {  // Good
    route("/api/users") { ... }
}

// Must be extension on Application or Route
fun Route.routes() { ... }  // Good
fun routes() { ... }  // Bad - not extension

// Package must be scanned
scanPackages("com.example.routes")
```

### Exception Handler Not Working

**Symptom**: Exception not caught, returns 500 instead of expected status

**Causes**:
1. Exception type not registered
2. Handler registered but overridden
3. Exception handler not installed before routes

**Solution**:
```kotlin
fun Route.handlers() = katalystExceptionHandler {
    exception<MyException> { call, cause ->  // Must match exact type
        call.respond(HttpStatusCode.BadRequest, ...)
    }
}

// Must register handler before using routes
routing {
    handlers()      // First
    userRoutes()    // Second
}
```

