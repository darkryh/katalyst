# Module map

Katalyst is published as a set of focused modules under the group
`io.github.darkryh.katalyst`. Depend only on what you use. All modules share the same
version (current line: `1.0.0-alpha`).

```kotlin
implementation("io.github.darkryh.katalyst:<module>:1.0.0-alpha")
```

## Core runtime

| Module | Purpose | Key API |
|--------|---------|---------|
| `katalyst-core` | Discovery interfaces and shared contracts | `Component`, `Service`, `ConfigProvider`, `Table`, `Validator` |
| `katalyst-di` | Bootstrap, discovery, dependency analysis, lifecycle | `katalystApplication`, `KatalystFeature`, `ApplicationInitializer`, `@InjectNamed` |
| `katalyst-koin-bean` | Koin dependency-injection adapter | `KoinBeanEngine` |
| `katalyst-scanner` | Classpath scanning that backs discovery | (internal; pulled in transitively) |

## HTTP and engines

| Module | Purpose | Key API |
|--------|---------|---------|
| `katalyst-ktor` | Routing, middleware, WebSocket, and exception-handler DSLs | `katalystRouting`, `katalystMiddleware`, `katalystWebSockets`, `katalystExceptionHandler`, `ktInject` |
| `katalyst-ktor-engine-netty` | Netty engine | `NettyServer` |
| `katalyst-ktor-engine-jetty` | Jetty engine | `JettyServer` |
| `katalyst-ktor-engine-cio` | CIO engine | `CioServer` |
| `katalyst-websockets` | `enableWebSockets()` feature toggle | `enableWebSockets` |

## Data

| Module | Purpose | Key API |
|--------|---------|---------|
| `katalyst-persistence` | Exposed + HikariCP tables, repositories, managed SQL | `Table`, `mapping`, `CrudRepository`, `SqlExecutor`, `DatabaseFactory`, `DatabaseConfig` |
| `katalyst-transactions` | Transaction management, retry, isolation, phases | `DatabaseTransactionManager`, `TransactionConfig`, `RetryPolicy` |
| `katalyst-migrations` | Schema migration discovery and execution | `KatalystMigration`, `MigrationRunner`, `MigrationOptions`, `enableMigrations` |

## Configuration

| Module | Purpose | Key API |
|--------|---------|---------|
| `katalyst-config-provider` | Runtime config tree + DI integration, loader patterns | `ConfigProvider` helpers, `ServiceConfigLoader`, `AutomaticServiceConfigLoader`, `ConfigLoaders` |
| `katalyst-config-spi` | Format-agnostic loader SPI | `ConfigLoader`, `ConfigLoaderResolver` |
| `katalyst-config-yaml` | YAML loader with profiles and `${ENV:default}` | `enableYamlConfiguration` |

## Application features

| Module | Purpose | Key API |
|--------|---------|---------|
| `katalyst-scheduler` | Cron / fixed-delay / fixed-rate / one-time jobs | `requireScheduler`, `ScheduleConfig`, `CronExpression`, `enableScheduler` |
| `katalyst-events` | Event contracts and validation | `DomainEvent`, `EventHandler`, `EventMetadata` |
| `katalyst-events-bus` | In-process transactional event bus | `EventBus`, `EventSideEffect`, `EventDeduplicationStore` |

## Testing

| Module | Purpose | Key API |
|--------|---------|---------|
| `katalyst-testing-core` | DI bootstrap for tests | `katalystTestEnvironment`, `inMemoryDatabaseConfig`, `FakeConfigProvider` |
| `katalyst-testing-ktor` | Ktor end-to-end test host | `katalystTestApplication` |

## A typical dependency set

A full-featured service usually pulls in:

```kotlin
val katalyst = "1.0.0-alpha"
implementation("io.github.darkryh.katalyst:katalyst-core:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-di:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-koin-bean:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-scanner:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-ktor:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-ktor-engine-netty:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-persistence:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-transactions:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-migrations:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-config-provider:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-config-yaml:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-scheduler:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-events:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-events-bus:$katalyst")
implementation("io.github.darkryh.katalyst:katalyst-websockets:$katalyst")
testImplementation("io.github.darkryh.katalyst:katalyst-testing-core:$katalyst")
testImplementation("io.github.darkryh.katalyst:katalyst-testing-ktor:$katalyst")
```

You also pin your own Ktor, Exposed, HikariCP, and JDBC-driver versions. Katalyst targets
Kotlin 2.4.x, Ktor 3.5.x, Koin 4.2.x, Exposed 1.3.x, and HikariCP 5.1.x.

