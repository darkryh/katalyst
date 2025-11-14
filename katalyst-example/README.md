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
    scan("com.ead.katalyst.example")
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

## Running the suite

```bash
./gradlew :katalyst-example:test
```

> The Postgres integration test automatically skips itself when Docker/Testcontainers
> aren’t available, so the suite stays green in restricted CI environments.
