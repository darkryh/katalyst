# Module map & imports

Group `io.github.darkryh.katalyst`. All modules share one version (line `1.0.0-alpha`).
`implementation("io.github.darkryh.katalyst:<module>:<version>")`.

## Modules

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
