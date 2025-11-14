# Automatic Wiring & Component Discovery

Katalyst’s scanner wires components any time you implement the right interfaces and place code under the packages supplied to `scanPackages`. This guide explains which interfaces are available, how they signal discovery, and which modules provide the underlying functionality.

## Module Map

| Concern | Module | Key APIs |
| --- | --- | --- |
| Components & services | `katalyst-core` | `Component`, `Service`, `transactionManager`, `requireScheduler` |
| Persistence layer | `katalyst-persistence` | `CrudRepository`, `Table` |
| Scheduler | `katalyst-scheduler` | `SchedulerService`, `ScheduleConfig`, `SchedulerJobHandle` |
| Events | `katalyst-events-*` | `EventBus`, `EventHandler`, Flow observers |
| HTTP & WebSockets | `katalyst-ktor`, `katalyst-websockets` | `katalystRouting`, `katalystMiddleware`, `katalystWebSockets`, `ktInject` |
| Messaging | `katalyst-messaging`, `katalyst-messaging-amqp` | **In development** |

## Components

Implement `Component` for lightweight collaborators (observers, helpers) that still need DI. Implementing the interface is the only signal needed for auto-registration.

```kotlin
@Suppress("unused")
class UserRegistrationFlowMonitor(
    private val eventBus: EventBus
) : Component // implementing Component marks this class for auto-discovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            eventBus.eventsOf<UserRegisteredEvent>().collect { event ->
                logger.info("Flow spotted registration for ${'$'}{event.email}")
            }
        }
    }
}
```

## Services

Extend `Service` for transactional business logic. Constructor parameters are injected automatically; `transactionManager` and `requireScheduler()` are available via the interface.

```kotlin
class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val validator: AuthValidator,
    private val passwordHasher: PasswordHasher,
    private val eventBus: EventBus,
    private val jwtSettings: JwtSettingsService
) : Service // implementing Service marks this class for auto-discovery {
    private val scheduler = requireScheduler()

    suspend fun register(request: RegisterRequest): AuthResponse = transactionManager.transaction {
        validator.validate(request)
        val account = repository.save(/* … */).toDomain()
        eventBus.publish(UserRegisteredEvent(account.id, account.email, request.displayName))
        AuthResponse(account.id, account.email, jwtSettings.generateToken(account.id, account.email))
    }

    @Suppress("unused")
    fun scheduleAuthDigest() = scheduler.scheduleCron(
        config = ScheduleConfig("authentication.broadcast"),
        task = { broadcastAuth() },
        cronExpression = CronExpression("0 0/1 * * * ?")
    )
}
```

## Scheduler Jobs

Returning `SchedulerJobHandle` from a parameterless function marks it as a scheduler registration point. The scheduler module (`katalyst-scheduler`) invokes the method at startup and manages the underlying coroutine job.

## Events & Observers

- Implement `EventHandler<T>` to handle domain events. The scanner registers handlers with the local `EventBus`.
- Regular components can observe events via `eventBus.eventsOf<T>()` for Flow-based projections.
- The transaction module ensures events published inside `transactionManager.transaction { }` are deferred until commit.

## HTTP Routes, Middleware, WebSockets

Use the provided DSLs; the scanner installs them automatically once DI is ready.

```kotlin
@Suppress("unused") // automatically injected
fun Route.authRoutes() = katalystRouting {
    route("/api/auth") {
        post("/login") {
            val service = call.ktInject<AuthenticationService>()
            call.respond(service.login(call.receive()))
        }
    }
}

@Suppress("unused") // automatically injected
fun Application.securityMiddleware() = katalystMiddleware {
    val jwtSettings = GlobalContext.get().get<JwtSettingsService>()
    jwtSettings.configure(this@securityMiddleware)
}

@Suppress("unused") // automatically injected
fun Route.notificationWebSocketRoutes() = katalystWebSockets {
    webSocket("/ws/users") {
        send(Frame.Text("""{"type":"welcome"}"""))
        // …
    }
}
```

- `call.ktInject<T>()` resolves request-scoped dependencies.
- Middleware/websocket builders can also use `inject<T>()` from the DSL to resolve dependencies.

## Persistence

Tables (`Table<Id, Entity>`) and repositories (`CrudRepository<Id, Entity>`) are covered in detail in [documentation/persistence.md](persistence.md). Once defined under the scanned packages, they are injected just like any other dependency.

## Messaging / AMQP

The messaging modules are **in development**. When released, implement their handler interfaces and register them under the scanned packages; the scanner will wire them like other components.
