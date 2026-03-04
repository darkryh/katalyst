# Katalyst Example – Testing Playbook

The example application now includes executable tests that demonstrate how to
exercise the Katalyst toolchain (automatic DI, migrations, scheduler, events)
without booting the full Ktor server.

## Test matrix

| Test | Purpose | Tooling showcased |
| --- | --- | --- |
| `AuthenticationServiceIntegrationTest` | Boots the full DI container against an in-memory H2 database and drives the `AuthenticationService` through register/login flows. | `katalystTestEnvironment`, auto-discovered repositories/tables, scheduler/events features. |
| `AuthenticationServicePostgresTest` | Repeats the workflow against a PostgreSQL Testcontainer to mimic production. Automatically skipped if Docker isn’t available. | Katalyst features + Testcontainers. |
| `AuthAccountStatusMigrationTest` | Runs `MigrationRunner` directly to backfill auth-account statuses and asserts `katalyst_schema_migrations` logged the execution. | MigrationRunner + in-memory H2. |

## Bootstrapping DI inside tests

Reusable helpers now live in `:katalyst-testing-core` and `:katalyst-testing-ktor`:

```kotlin
val environment = katalystTestEnvironment {
    database(inMemoryDatabaseConfig())
    scan("io.github.darkryh.katalyst.example")
}
val service = environment.get<AuthenticationService>()
environment.close()
```

The builder wires the same optional features used by `Application.kt`
(`ConfigProviderFeature`, `eventSystemFeature`, `SchedulerFeature`,
`WebSocketFeature`, and `MigrationFeature`), so services that call
`requireScheduler()` or load `ConfigProvider` behave exactly as they do in
production. For HTTP tests, `katalystTestApplication { ... }` installs all
auto-discovered Ktor modules before executing requests so the pipeline mirrors
runtime behavior.

## New DI/Persistence Patterns

Latest Katalyst alpha adds two useful patterns you can adopt in the sample:

1. Deferred DI resolution (DSL-first style):
```kotlin
class ExampleService(
    private val mailerProvider: Provider<MailerService>,
    private val auditClient: Lazy<AuditClient>
) : Service
```

2. Managed JDBC bootstrap/custom SQL:
```kotlin
class ExampleBootstrap(private val sqlExecutor: SqlExecutor) : Service {
    suspend fun prepare() = sqlExecutor.executeBatch(
        listOf("CREATE TABLE IF NOT EXISTS sample_marker (id INT PRIMARY KEY)")
    )
}
```

## Running the suite

```bash
./gradlew :katalyst-example:test
```

> The Postgres integration test automatically skips itself when Docker/Testcontainers
> aren’t available, so the suite stays green in restricted CI environments.
