# Module map

Katalyst is published as a set of focused modules under the group
`io.github.darkryh.katalyst`. Depend only on what you use. All modules share the same
version (current line: `1.0.0-alpha01`). Applications should consume the BOM and feature
starters; the lower-level modules below remain useful for advanced integrations.

```kotlin
val katalyst = "1.0.0-alpha01"
implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
implementation("io.github.darkryh.katalyst:katalyst-starter-web")
```

## Starters

| Starter | Includes |
|---------|----------|
| `katalyst-starter-core` | Bootstrap, DI, scanning, YAML configuration, and events |
| `katalyst-starter-web` | Core starter, Ktor integration, and the Netty engine |
| `katalyst-starter-persistence` | Core starter, transactions, Exposed, HikariCP, and JDBC drivers |
| `katalyst-starter-migrations` | Persistence starter and migration support |
| `katalyst-starter-scheduler` | Core starter and scheduling support |
| `katalyst-starter-websockets` | Web starter and WebSocket support |
| `katalyst-starter-test` | Core and Ktor testing support |

The BOM controls Katalyst artifact versions. Starters control capabilities. Adding the BOM by
itself adds no runtime classes.

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
val katalyst = "1.0.0-alpha01"
implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
implementation("io.github.darkryh.katalyst:katalyst-starter-web")
implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")
implementation("io.github.darkryh.katalyst:katalyst-starter-migrations")
implementation("io.github.darkryh.katalyst:katalyst-starter-scheduler")
implementation("io.github.darkryh.katalyst:katalyst-starter-websockets")
testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
```

The starters provide their corresponding Ktor, Exposed, HikariCP, and JDBC dependencies.
Applications only declare those libraries directly when overriding or extending the curated
stack.
