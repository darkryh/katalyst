# Module map & imports

Group `io.github.darkryh.katalyst`. All artifacts share one version (current line
`1.0.0-alpha01`).

## How apps depend on Katalyst — BOM + starters (do this)

Applications do **not** list individual `katalyst-*` modules, and they do **not** list the
external libraries (Ktor, Exposed, HikariCP, JDBC drivers, JUnit, …) themselves. Instead you
declare the **BOM** once — it pins every Katalyst artifact to one version — and then add the
**feature starters** you need with no version. Each starter pulls its own Katalyst modules
*and* their third-party dependencies transitively. This is the single most common thing to get
right in a build file; the old per-module + explicit-external-deps style is gone.

```kotlin
val katalyst = "1.0.0-alpha01"
implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))   // version alignment only
implementation("io.github.darkryh.katalyst:katalyst-starter-web")               // Ktor + Netty + JSON
implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")       // Exposed + Hikari + H2/Postgres
implementation("io.github.darkryh.katalyst:katalyst-starter-migrations")        // + migrations
implementation("io.github.darkryh.katalyst:katalyst-starter-scheduler")         // + scheduler
implementation("io.github.darkryh.katalyst:katalyst-starter-websockets")        // + WebSockets
testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")          // test host + JUnit + Testcontainers
```

| Starter | Bundles (Katalyst modules) | Brings transitively (external) |
|---------|----------------------------|--------------------------------|
| `katalyst-starter-core` | core, di, koin-bean, scanner, config-provider, config-yaml, events, events-bus | coroutines, kotlinx-serialization, slf4j, logback |
| `katalyst-starter-web` | starter-core, ktor, ktor-engine-netty | Ktor server (core, content-negotiation, auth/JWT, call-id, rate-limit, status-pages, JSON), Netty |
| `katalyst-starter-persistence` | starter-core, persistence, transactions | Exposed (core/dao/jdbc), HikariCP, H2 + PostgreSQL drivers |
| `katalyst-starter-migrations` | starter-persistence, migrations | (via persistence) |
| `katalyst-starter-scheduler` | starter-core, scheduler | — |
| `katalyst-starter-websockets` | starter-web, websockets | Ktor WebSockets |
| `katalyst-starter-test` | testing-core, testing-ktor | kotlin-test-junit5, Ktor WS client, Testcontainers-Postgres, JUnit runtime |

The BOM adds no classes by itself — starters control capabilities. Netty ships inside
`katalyst-starter-web`; to run on Jetty or CIO add that engine module (versionless, BOM-managed)
— see the engine rows below. Depend on a bare module directly only for advanced integrations
that no starter covers.

## Modules (what each starter bundles)

| Module | Responsibility | Key public API |
|--------|----------------|----------------|
| `katalyst-core` | Discovery interfaces, shared contracts | `Component`, `Service`, `ConfigProvider`, `Table`, `mapping`, `KatalystContainer`, `Validator` |
| `katalyst-di` | Bootstrap, discovery, analysis, lifecycle | `katalystApplication`, `KatalystApplicationBuilder`, `KatalystFeature`, `ApplicationInitializer`, `@InjectNamed`, `enableEvents`, `enableServerConfiguration`, `KatalystMigration` (interface) |
| `katalyst-koin-bean` | Koin DI adapter | `KoinBeanEngine` |
| `katalyst-scanner` | Classpath scanning (internal/transitive) | — |
| `katalyst-ktor` | Routing/middleware/WebSocket/exception DSLs | `katalystRouting`, `katalystMiddleware`, `katalystWebSockets`, `katalystExceptionHandler`, `ktInject` |
| `katalyst-ktor-engine-netty` | Netty engine | `NettyServer` |
| `katalyst-ktor-engine-jetty` | Jetty engine | `JettyServer` |
| `katalyst-ktor-engine-cio` | CIO engine | `CioServer` |
| `katalyst-websockets` | WebSocket feature toggle | `enableWebSockets` |
| `katalyst-persistence` | Exposed + Hikari tables/repos, managed SQL | `Table`, `mapping`, `CrudRepository`, `Identifiable`, `SqlExecutor`, `DatabaseFactory`, `DatabaseConfig` |
| `katalyst-transactions` | Transaction mgmt, retry, isolation, phases | `DatabaseTransactionManager`, `TransactionConfig`, `RetryPolicy`, `TransactionIsolationLevel` |
| `katalyst-migrations` | Migration discovery + runner | `KatalystMigration`, `MigrationRunner`, `MigrationOptions`, `enableMigrations` |
| `katalyst-config-provider` | Config tree + loaders + helpers | `ConfigProvider` helpers, `ServiceConfigLoader`, `AutomaticServiceConfigLoader`, `ConfigLoaders` |
| `katalyst-config-spi` | Format-agnostic loader SPI | `ConfigLoader`, `ConfigLoaderResolver` |
| `katalyst-config-yaml` | YAML loader, profiles, `${ENV:default}` | `enableYamlConfiguration`, `YamlConfigurationFeature` |
| `katalyst-scheduler` | Cron/fixed/one-time jobs | `requireScheduler`, `ScheduleConfig`, `CronExpression`, `enableScheduler` |
| `katalyst-events` | Event contracts + validation | `DomainEvent`, `EventHandler`, `EventMetadata` |
| `katalyst-events-bus` | In-process transactional bus | `EventBus`, `EventSideEffect`, `EventDeduplicationStore` |
| `katalyst-testing-core` | Test DI bootstrap | `katalystTestEnvironment`, `inMemoryDatabaseConfig`, `FakeConfigProvider` |
| `katalyst-testing-ktor` | Ktor end-to-end test host | `katalystTestApplication` |

## Toolchain versions (from gradle/libs.versions.toml)

| Dependency | Version |
|------------|---------|
| Kotlin | 2.4.0 |
| Ktor | 3.5.0 |
| Koin | 4.2.2 |
| Exposed | 1.3.0 (v1 JDBC API) |
| HikariCP | 5.1.0 |
| kotlinx-coroutines | 1.10.2 |
| kotlinx-serialization | 1.9.0 |

JDK 21+. `kotlin.code.style=official`.

## Import quick-reference (the ones agents get wrong)

```kotlin
// Bootstrap
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer            // .jetty.JettyServer / .cio.CioServer
import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.di.feature.enableServerConfiguration
import io.github.darkryh.katalyst.di.feature.enableEvents
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations
import io.github.darkryh.katalyst.scheduler.enableScheduler
import io.github.darkryh.katalyst.websockets.enableWebSockets

// DI / components
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.di.injection.InjectNamed

// Persistence (note: Exposed is org.jetbrains.exposed.v1.*)
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.repositories.CrudRepository
import io.github.darkryh.katalyst.repositories.Identifiable
import io.github.darkryh.katalyst.database.SqlExecutor
import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.config.DatabaseConfig
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// Config
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.config.provider.AutomaticServiceConfigLoader
import io.github.darkryh.katalyst.config.provider.requiredString   // requiredInt, intOrNull, boolean, …

// Events
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.events.bus.eventsOf

// Scheduler
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig

// Ktor
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.builder.katalystExceptionHandler
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.github.darkryh.katalyst.ktor.websocket.katalystWebSockets
import io.github.darkryh.katalyst.ktor.extension.ktInject

// Migrations
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import io.github.darkryh.katalyst.migrations.options.MigrationOptions

// Testing
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import io.github.darkryh.katalyst.testing.core.inMemoryDatabaseConfig
import io.github.darkryh.katalyst.testing.core.FakeConfigProvider
import io.github.darkryh.katalyst.testing.ktor.katalystTestApplication
```

## Working on Katalyst itself

Each module commits its public surface to `api/<module>.api` (Kotlin binary-compatibility
validator). Changing public API requires `./gradlew apiDump`. Tests in `src/test/kotlin`; run
`./gradlew :module:test`. The integration reference app is `samples/katalyst-example`
(composite-enabled via the `includeSamplesComposite` Gradle property).
