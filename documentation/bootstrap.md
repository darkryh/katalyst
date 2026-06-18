# Bootstrap & Feature Wiring

This guide shows how to start a Katalyst application the same way the sample (`samples/katalyst-example`) and the Boshi server (`projects/boshi/boshi-server`) do: annotation-free, interface/DSL driven, and using the latest Ktor 3 + Exposed/Hikari stack.

## Quickstart (katalystApplication)

```kotlin
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer
// Or:
// import io.github.darkryh.katalyst.ktor.engine.jetty.JettyServer
// import io.github.darkryh.katalyst.ktor.engine.cio.CioServer
import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations
import io.github.darkryh.katalyst.scheduler.enableScheduler
import io.github.darkryh.katalyst.websockets.enableWebSockets

fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)                 // REQUIRED: choose engine (Netty shown; CIO/Jetty also available)
    beanEngine(KoinBeanEngine)            // REQUIRED: choose bean/injection engine
    enableYamlConfiguration()             // REQUIRED for YAML-backed database/server/service config
    database {                            // REQUIRED: load database.* from the installed config source
        fromConfiguration()
        // maxPoolSize = 20               // Optional code override
    }
    scanPackages("com.example")           // REQUIRED: discover components/services/routes/etc.

    schema {
        validateOnStartup()               // Default when schema { ... } is omitted
        // createMissing()                // Local/test compatibility mode
        // none()                         // External migration job owns schema lifecycle
    }

    features {                            // OPTIONAL: opt in to non-core features
        enableServerConfiguration()       // load ktor.deployment.* from YAML
        enableMigrations()                // run Katalyst migrations at startup
        enableScheduler()                 // register Scheduler jobs discovered in scanPackages
        enableWebSockets {
            // pingPeriod = 30.seconds
            // timeout = 15.seconds
            // maxFrameSize = Long.MAX_VALUE
            // masking = false
        }
        enableEvents()                    // enable local transactional EventBus
    }
}
```

Key points:
- Add a DI adapter module to the application runtime and select it explicitly. For Koin, add `io.github.darkryh.katalyst:katalyst-koin-bean` and call `beanEngine(KoinBeanEngine)`; startup fails fast when no bean engine is selected.
- For YAML-backed apps, call `enableYamlConfiguration()` once and configure database inside the app DSL with `database { fromConfiguration() }`.
- `database { fromConfiguration(); maxPoolSize = 20 }` reads `database.*` keys from YAML and lets code override pool/transaction values explicitly. Use `database(DatabaseConfig(...))` only when you want fully programmatic database configuration.
- `scanPackages` is the discovery hook; everything else is interface-driven (no annotations).
- Feature toggles under `features { ... }` opt your app into migrations, scheduler, events, WebSockets, and server deployment loading.
- Constructor and framework-function parameters are injected directly; use Kotlin defaults or nullable types for optional values, and `@InjectNamed` only for intentional qualifier disambiguation.

## Discovery Signals (no annotations)

Place classes under the scanned packages and implement the right interfaces/return types:
- `Service` / `Component` (business logic, observers, helpers) – see `AuthenticationService`, `UserRegistrationFlowMonitor`.
- `CrudRepository<Id, Entity>` and `Table<Id, Entity>` – repositories and tables are auto-registered; Exposed/Hikari wiring happens for you.
- Scheduler registrations – declare methods on `Service` classes that return `SchedulerJobHandle` through `requireScheduler().jobs { ... }`.
- `EventHandler<T>` – handlers are bound to the in-process `EventBus`; events published inside `transactionManager.transaction { }` are delayed until commit.
- `ApplicationInitializer` – multiple implementations are allowed and executed deterministically by `order`.
- Ktor DSL entry points – `katalystRouting`, `katalystMiddleware`, `katalystWebSockets` functions are installed automatically; use `ktInject<T>()` inside them for constructor-free wiring.

## Application Initializers

You can split bootstrap work across multiple `ApplicationInitializer` implementations.

```kotlin
class CacheWarmupInitializer : ApplicationInitializer {
    override val initializerId: String = "cacheWarmup"
    override val order: Int = 50
    override suspend fun onApplicationReady() { /* ... */ }
}

class MetricsInitializer : ApplicationInitializer {
    override val initializerId: String = "metrics"
    override val order: Int = 60
    override suspend fun onApplicationReady() { /* ... */ }
}
```

Behavior:
- Multiple initializers can coexist (multibinding).
- Execution order is deterministic (`order` ascending, then class-name tie-break).
- Strict collision behavior remains for non-allowlisted secondary interfaces.

## Scheduler Jobs

Declare scheduler jobs on service classes:

```kotlin
class CleanupJobs(
    private val cleanupService: CleanupService,
) : Service {
    private val scheduler = requireScheduler()

    fun cleanupJobs() = scheduler.jobs {
        cron("cleanup-expired", "0 0 * * * ?") {
            cleanupService.cleanupExpired()
        }

        fixedDelay("sync-users", 30.seconds) {
            cleanupService.syncUsers()
        }
    }
}
```

## Persistence & Transactions

Katalyst wraps Exposed 1.3.0 + HikariCP:
- Tables extend `LongIdTable` (or friends) and implement `Table<Id, Entity>` with a `mapping { ... }` block for row construction and insert/update bindings.
- Repositories implement `CrudRepository<Id, Entity>`; custom queries use the Exposed DSL (`selectAll`, `eq`, `and`, etc.).
- Use `transactionManager.transaction { ... }` in Services to ensure DB writes and EventBus publications are atomic; migrations may use raw `transaction(database)` where appropriate.
- Schema validation is the default. Use `schema { validateOnStartup() }` when you want the policy to be explicit, and run migrations intentionally. `createMissing()` remains available for local/test compatibility.
Migration status, validation, and dry-run operations are covered in [migrations.md](migrations.md).
See `samples/katalyst-example/src/main/kotlin/io/github/darkryh/katalyst/example/infra/database` and `projects/boshi/boshi-server/boshi-storage` for concrete patterns.

## Configuration & YAML

- Database infrastructure config is declared in the application DSL. Install the source once with `enableYamlConfiguration()`, then call `database { fromConfiguration() }` to read `database.*` keys before DI starts.
- Service config should use `AutomaticServiceConfigLoader` so objects are loaded, validated, and injected during DI (e.g., a `NotificationApiConfigLoader`; the Boshi SMTP module shows the same pattern for email).
- YAML provider (`katalyst-config-yaml`) supports profiles (`application-dev.yaml`, `application-prod.yaml`) and `${ENV:default}` interpolation.
Deep dive: [configuration.md](configuration.md).

## Ktor Usage Cheatsheet

- Middleware: `fun Application.rateLimit() = katalystMiddleware { val cfg = ktInject<RateLimitConfig>(); install(RateLimit) { ... } }`
- Routes: `fun Route.authRoutes() = katalystRouting { post("/login") { val svc = call.ktInject<AuthService>(); call.respond(svc.login(call.receive())) } }`
- WebSockets: `fun Route.notifications() = katalystWebSockets { webSocket("/ws") { /* ... */ } }`

WebSocket routing, options, and plugin installation live in `katalyst-ktor`. The `features { enableWebSockets() }` toggle currently remains in `katalyst-websockets` because Katalyst feature registration is still owned by `katalyst-di`.

Imports are Ktor 3.x aligned (server-side `Application`, `Route`, `ContentNegotiation`, etc.) and Exposed imports should use the `org.jetbrains.exposed.v1.*` package set shown in `exposed-database-setup.md`.

## Where to Look

- **Sample app:** `samples/katalyst-example` – full stack (events, scheduler, websockets, migrations) with Netty engine and profile-aware YAML.
- **Production-style app:** `projects/boshi/boshi-server` – multi-module setup using the same bootstrap DSL, AutomaticServiceConfigLoader, scheduler jobs, rate limiting middleware, and Exposed repositories.

Use these as references when wiring new services to stay aligned with the supported imports and DSL style.
