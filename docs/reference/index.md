# Reference

Reference is the map: factual, complete, and consistent. Use it to look things up — the
application DSL, the discovery interfaces, configuration keys, and every subsystem. For
learning or task-oriented help, see the [tutorial](../getting-started.md) and
[how-to guides](../how-to/index.md).

## Foundations

- [Module map](modules.md) — every published artifact, its purpose, and its Maven
  coordinates.
- [Application DSL](application-dsl.md) — `katalystApplication { … }` and every block it
  accepts.
- [Dependency injection & auto-wiring](di-auto-wiring.md) — the discovery interfaces and
  the parameter-injection rules.

## Configuration

- [Configuration](configuration.md) — `ConfigProvider`, the loader patterns, `ConfigLoaders`
  helpers, and every recognized key.

## Data

- [Persistence](persistence.md) — `Table`, `mapping`, `CrudRepository`, `SqlExecutor`,
  `DatabaseConfig`.
- [Transactions](transactions.md) — `transactionManager`, `TransactionConfig`, retry, and
  isolation.
- [Migrations](migrations.md) — `KatalystMigration`, `MigrationRunner`, `MigrationOptions`.

## Application features

- [Scheduler](scheduler.md) — `requireScheduler`, the jobs DSL, `ScheduleConfig`,
  `CronExpression`.
- [Events](events.md) — `DomainEvent`, `EventHandler`, `EventBus`, side effects,
  deduplication.
- [Ktor integration](ktor.md) — `katalystRouting`, `katalystMiddleware`,
  `katalystWebSockets`, `katalystExceptionHandler`, `ktInject`.

## Testing

- [Testing](testing.md) — `katalystTestEnvironment`, `katalystTestApplication`,
  `inMemoryDatabaseConfig`, `FakeConfigProvider`.

