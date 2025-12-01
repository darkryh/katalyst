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
import com.ead.katalyst.core.component.Component
import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.events.bus.eventsOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@Suppress("unused")
class UserRegistrationFlowMonitor(
    private val eventBus: EventBus
) : Component /*implementing Component marks this class for auto-discovery */ {
    private val logger = LoggerFactory.getLogger(this::class.java)
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
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.cron.CronExpression
import com.ead.katalyst.scheduler.extension.requireScheduler

class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val validator: AuthValidator,
    private val passwordHasher: PasswordHasher,
    private val eventBus: EventBus,
    private val jwtSettings: JwtSettingsService
) : Service /*implementing Service marks this class for auto-discovery */ {
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
import com.ead.katalyst.ktor.builder.katalystRouting
import com.ead.katalyst.ktor.middleware.katalystMiddleware
import com.ead.katalyst.ktor.extension.ktInject
import com.ead.katalyst.websockets.builder.katalystWebSockets

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

## Configuration Auto-Wiring

Service-specific configuration objects are discovered and auto-injected just like components. Use `AutomaticServiceConfigLoader<T>` for configuration that's only needed by components (not infrastructure). This enables automatic discovery, loading, and injection during DI bootstrap Phase 5a.

**Example:** Define `NotificationApiConfigLoader` implementing `AutomaticServiceConfigLoader<NotificationApiConfig>`, then inject it:

```kotlin
class NotificationService(
    val notificationConfig: NotificationApiConfig  // ✅ Auto-injected by DI!
) : Service {
    fun sendMessage(to: String, message: String) {
        val baseUrl = notificationConfig.baseUrl
        val apiKey = notificationConfig.apiKey
        // Call external API...
    }
}
```

For the complete 3-step implementation guide, see [configuration.md](configuration.md) → **Implementing AutomaticServiceConfigLoader**.

**Note:** For infrastructure configuration needed before DI bootstrap (like database), use `ServiceConfigLoader` instead. See [configuration.md](configuration.md) → **Choosing the Right Pattern** for the decision framework.

**How it works:**

During DI Phase 5a, the framework automatically:
1. Discovers all `AutomaticServiceConfigLoader` implementations via classpath scanning
2. Loads each configuration from YAML using `ConfigProvider`
3. Validates each configuration (fail-fast if invalid)
4. Registers it in Koin as a singleton

Components then receive their configurations through constructor injection in Phase 5b.

For the complete 7-phase DI bootstrap process and detailed Phase 5a explanation, see [configuration.md](configuration.md) → **How Discovery Works (Phase 5a)**.

**Key advantages for service configuration:**
- No helper functions or bootstrap code needed
- Automatic discovery via classpath scanning
- Constructor injection (idiomatic Kotlin)
- Fail-fast validation at startup

**Not suitable for infrastructure config** (like database) because it loads in Phase 5a, which is too late. Use `ServiceConfigLoader` for infrastructure config instead.

For the complete decision framework and detailed patterns, see [configuration.md](configuration.md) → **Choosing the Right Pattern for Your Configuration**.

## Persistence

Tables (`Table<Id, Entity>`) and repositories (`CrudRepository<Id, Entity>`) are covered in detail in [documentation/persistence.md](persistence.md). Once defined under the scanned packages, they are injected just like any other dependency.

## Messaging / AMQP

The messaging modules are **in development**. When released, implement their handler interfaces and register them under the scanned packages; the scanner will wire them like other components.
