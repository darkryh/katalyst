# Katalyst – Production-Ready Ktor Starter Kit

Katalyst is a batteries-included toolkit for building opinionated Ktor services. It automates dependency injection, config loading, database bootstrap, routing discovery, schedulers, events, and websockets so you can focus on business logic instead of plumbing.

## Key Capabilities

- **Automatic DI & Component Scan** – Implement `Service`/`Component`/`CrudRepository`, list your packages, and `initializeKoinStandalone` wires everything (repositories, tables, events, routes, middleware, websocket builders).
- **Configuration Pipeline** – `katalyst-config-provider` + YAML provider cascade through `ConfigProviderFeature`. Config loaders (e.g., `DatabaseConfigLoader`) are auto-discovered, validated, and injected anywhere.
- **Database & Migrations** – `katalyst-persistence` discovers Exposed `Table` implementations, creates schemas, and exports `DatabaseTransactionManager`. `katalyst-migrations` exposes migration runners plus transaction-aware event adapters.
- **Scheduler** – `requireScheduler()` gives you `SchedulerService`; returning `SchedulerJobHandle` from a no-arg function registers cron/fixed-rate jobs automatically. Tags/timeouts/hooks come from `ScheduleConfig`.
- **WebSockets & HTTP DSLs** – Routes/middleware/websocket blocks defined via `katalystRouting`, `katalystMiddleware`, or `katalystWebSockets` are auto-installed with DI-backed request injection.
- **Event System** – `katalyst-events` (bus/transport/client) discovers `EventHandler`s and `DomainEvent` observers, ensuring handlers run within transactions and events fan out via local flows.
- **Testing Tooling** – `:katalyst-testing-core` gives `katalystTestEnvironment` (DI+DB+feature bootstrap, module overrides, fake config providers) and `:katalyst-testing-ktor` adds `katalystTestApplication` so HTTP/WebSocket tests run against the real auto-configured pipeline.

## Popular Modules & When To Use Them

| Module | Why it matters |
| --- | --- |
| `katalyst-di` | DSL (`katalystApplication`, `initializeKoinStandalone`) for bootstrapping DI + server engines, plus auto-discovery registrars. |
| `katalyst-config-provider` / `katalyst-config-yaml` | Normalizes hierarchical configs and makes them injectable as `ConfigProvider`. |
| `katalyst-persistence` | Discovers Exposed tables, provides repository abstractions, wires `DatabaseFactory` and `DatabaseTransactionManager`. |
| `katalyst-migrations` | Migration runner + transaction adapters so events only publish after commits. |
| `katalyst-scheduler` | Coroutine-based scheduler with cron/fixed-rate/fixed-delay support; integrates through `SchedulerFeature`. |
| `katalyst-websockets` | `katalystWebSockets` DSL for auto-registered websocket endpoints. |
| `katalyst-events` (+bus/client/transport) | Local event bus with sealed hierarchy support, Flow observers, transport/client layers. |
| `katalyst-testing-core` / `katalyst-testing-ktor` | Production-parity test harness for DI + Ktor. |

## Example: Boot & Configure

```kotlin
fun main(args: Array<String>) = katalystApplication(args) {
    database(DbConfigImpl.loadDatabaseConfig())
    scanPackages("com.ead.katalyst.example")

    enableConfigProvider()
    enableEvents { withBus(true) }
    enableMigrations()
    enableScheduler()
    enableWebSockets()
}
```

Routes, middleware, and websockets are just extension functions:

```kotlin
@Suppress("unused")
fun Route.authRoutes() = katalystRouting {
    route("/api/auth") {
        post("/login") {
            val service = call.ktInject<AuthenticationService>()
            val request = call.receive<LoginRequest>()
            call.respond(service.login(request))
        }
    }
}

@Suppress("unused")
fun Application.requestLoggingMiddleware() = katalystMiddleware {
    install(CallLogging)
}

@Suppress("unused")
fun Route.notificationWebSocketRoutes() = katalystWebSockets {
    webSocket("/ws/users") {
        send(Frame.Text("""{"type":"welcome"}"""))
        for (frame in incoming) {
            if (frame is Frame.Text && frame.readText() == "ping") {
                send(Frame.Text("""{"type":"pong"}"""))
            }
        }
    }
}
```

## Example: Scheduler & Events

```kotlin
class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val eventBus: EventBus,
    ...
) : Service {
    private val scheduler = requireScheduler()

    suspend fun register(request: RegisterRequest): AuthResponse = transactionManager.transaction {
        val account = repository.save(/* ... */).toDomain()
        eventBus.publish(UserRegisteredEvent(account.id, account.email, request.displayName))
        issueToken(account)
    }

    @Suppress("unused")
    fun scheduleAuthDigest() = scheduler.scheduleCron(
        config = ScheduleConfig("authentication.broadcast", tags = setOf("demo")),
        task = { broadcastAuth() },
        cronExpression = CronExpression("0 0/1 * * * ?")
    )
}
```

The scheduler automatically discovers the `scheduleAuthDigest` method because it returns `SchedulerJobHandle` and takes no parameters. Event handlers such as `UserRegistrationHandler` (implements `EventHandler<UserRegisteredEvent>`) and Flow observers like `UserRegistrationFlowMonitor` are auto-registered.

## Testing the Full Stack

Use the shared testing modules to run production-parity tests without manual DI wiring:

```kotlin
@Test
fun `register login over HTTP`() = katalystTestApplication(
    configureEnvironment = {
        database(inMemoryDatabaseConfig())
        scan("com.ead.katalyst.example")
    }
) { env ->
    val registerResponse = client.post("/api/auth/register") { ... }
    val profileResponse = client.get("/api/users/me") {
        header(HttpHeaders.Authorization, "Bearer ${registerResponse.token}")
    }
    val profiles = env.get<UserProfileService>().listProfiles()
    assertTrue(profiles.any { it.accountId == registerResponse.accountId })
}
```

## Running & Coverage

```bash
# Full build + tests
./gradlew build

# Example module tests only
./gradlew :katalyst-example:test

# Coverage (Kover-enabled)
./gradlew :katalyst-example:koverHtmlReport
# -> katalyst-example/build/reports/kover/html/index.html
```

## What’s Next?

The messaging/AMQP modules are still in development; when you start using them, follow the same testing approach—spin up the relevant Testcontainers, override bindings via `katalystTestEnvironment { overrideModules(...) }`, and add end-to-end flows. The current suite already validates DI, migrations, scheduler, events, websockets, and HTTP, making Katalyst a dependable foundation for new Ktor services.
