# Bootstrap & Feature Wiring

This guide shows how to start a Katalyst application the same way the sample (`samples/katalyst-example`) and the Boshi server (`projects/boshi/boshi-server`) do: annotation-free, interface/DSL driven, and using the latest Ktor 3 + Exposed/Hikari stack.

## Quickstart (katalystApplication)

```kotlin
import io.github.darkryh.katalyst.ktor.engine.netty.embeddedServer
import io.github.darkryh.katalyst.config.yaml.enableConfigProvider
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations
import io.github.darkryh.katalyst.scheduler.enableScheduler
import io.github.darkryh.katalyst.websockets.enableWebSockets

fun main(args: Array<String>) = katalystApplication(args) {
    engine(embeddedServer())              // REQUIRED: choose engine (Netty shown; CIO/Jetty also available)
    database(DbConfigImpl.loadDatabase()) // REQUIRED: Hikari + Exposed config
    scanPackages("com.example")           // REQUIRED: discover components/services/routes/etc.

    enableServerConfiguration()           // OPTIONAL: load ktor.deployment.* from YAML
    enableConfigProvider()                // OPTIONAL: inject ConfigProvider + AutomaticServiceConfigLoader
    enableMigrations()                    // OPTIONAL: run Katalyst migrations at startup
    enableScheduler()                     // OPTIONAL: register Scheduler jobs discovered in scanPackages
    enableWebSockets()                    // OPTIONAL: auto-install websocket routes
    enableEvents { withBus(true) }        // OPTIONAL: enable EventBus + client features
}
```

Key points:
- `database(...)` accepts any `DatabaseConfig` (HikariCP + Exposed 1.0.0-rc-3). Load it with `ServiceConfigLoader` before DI starts.
- `scanPackages` is the discovery hook; everything else is interface-driven (no annotations).
- Feature toggles (`enable*`) opt your app into migrations, scheduler, events, websockets, and YAML-backed ConfigProvider.

## Discovery Signals (no annotations)

Place classes under the scanned packages and implement the right interfaces/return types:
- `Service` / `Component` (business logic, observers, helpers) – see `AuthenticationService`, `UserRegistrationFlowMonitor`.
- `CrudRepository<Id, Entity>` and `Table<Id, Entity>` – repositories and tables are auto-registered; Exposed/Hikari wiring happens for you.
- Scheduler registrations – functions returning `SchedulerJobHandle` (e.g., `scheduler.scheduleCron(...)`) are picked up automatically.
- `EventHandler<T>` – handlers are bound to the in-process `EventBus`; events published inside `transactionManager.transaction { }` are delayed until commit.
- Ktor DSL entry points – `katalystRouting`, `katalystMiddleware`, `katalystWebSockets` functions are installed automatically; use `ktInject<T>()` inside them for constructor-free wiring.

## Persistence & Transactions

Katalyst wraps Exposed 1.0.0-rc-3 + HikariCP:
- Tables extend `LongIdTable` (or friends) and implement `Table<Id, Entity>` to map rows and assign entities.
- Repositories implement `CrudRepository<Id, Entity>`; custom queries use the Exposed DSL (`selectAll`, `eq`, `and`, etc.).
- Use `transactionManager.transaction { ... }` in Services to ensure DB writes and EventBus publications are atomic; migrations may use raw `transaction(database)` where appropriate.
See `samples/katalyst-example/src/main/kotlin/io/github/darkryh/katalyst/example/infra/database` and `projects/boshi/boshi-server/boshi-storage` for concrete patterns.

## Configuration & YAML

- Infrastructure config (database, ports, TLS) should be loaded **before** DI via `ServiceConfigLoader` + `ConfigBootstrapHelper` (see `DbConfigImpl` in the sample and Boshi).
- Service config should use `AutomaticServiceConfigLoader` so objects are loaded, validated, and injected during DI (e.g., a `NotificationApiConfigLoader`; the Boshi SMTP module shows the same pattern for email).
- YAML provider (`katalyst-config-yaml`) supports profiles (`application-dev.yaml`, `application-prod.yaml`) and `${ENV:default}` interpolation.
Deep dive: [configuration.md](configuration.md).

## Ktor Usage Cheatsheet

- Middleware: `fun Application.rateLimit() = katalystMiddleware { val cfg = ktInject<RateLimitConfig>(); install(RateLimit) { ... } }`
- Routes: `fun Route.authRoutes() = katalystRouting { post("/login") { val svc = call.ktInject<AuthService>(); call.respond(svc.login(call.receive())) } }`
- WebSockets: `fun Route.notifications() = katalystWebSockets { webSocket("/ws") { /* ... */ } }`

Imports are Ktor 3.x aligned (server-side `Application`, `Route`, `ContentNegotiation`, etc.) and Exposed imports should use the `org.jetbrains.exposed.v1.*` package set shown in `exposed-database-setup.md`.

## Where to Look

- **Sample app:** `samples/katalyst-example` – full stack (events, scheduler, websockets, migrations) with Netty engine and profile-aware YAML.
- **Production-style app:** `projects/boshi/boshi-server` – multi-module setup using the same bootstrap DSL, AutomaticServiceConfigLoader, scheduler jobs, rate limiting middleware, and Exposed repositories.

Use these as references when wiring new services to stay aligned with the supported imports and DSL style.
