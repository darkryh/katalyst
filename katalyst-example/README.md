# Katalyst Example – Testing Playbook

The example application now includes executable tests that demonstrate how to
exercise the Katalyst toolchain (automatic DI, migrations, scheduler, events)
without booting the full Ktor server.

## Test matrix

| Test | Purpose | Tooling showcased |
| --- | --- | --- |
| `AuthenticationServiceIntegrationTest` | Boots the full DI container against an in-memory H2 database and drives the `AuthenticationService` through register/login flows. | `initializeKoinStandalone`, auto-discovered repositories/tables, scheduler/events features. |
| `AuthenticationServicePostgresTest` | Repeats the workflow against a PostgreSQL Testcontainer to mimic production. Automatically skipped if Docker isn’t available. | Katalyst features + Testcontainers. |
| `AuthAccountStatusMigrationTest` | Runs `MigrationRunner` directly to backfill auth-account statuses and asserts `katalyst_schema_migrations` logged the execution. | MigrationRunner + in-memory H2. |

## Bootstrapping DI inside tests

`src/test/kotlin/com/ead/katalyst/example/testsupport/KatalystTestSupport.kt` exposes helpers:

```kotlin
val koin = startKatalystForTests(
    databaseConfig = inMemoryDatabaseConfig(),
    features = defaultTestFeatures()
)
// ... use koin.get<AuthenticationService>() ...
stopKatalystForTests()
```

`defaultTestFeatures()` wires the same optional features used by `Application.kt`
(`ConfigProviderFeature`, `eventSystemFeature`, `SchedulerFeature`,
`WebSocketFeature`, and `MigrationFeature`), so services that call
`requireScheduler()` or load `ConfigProvider` behave exactly as they do in
production.

## Running the suite

```bash
./gradlew :katalyst-example:test
```

> The Postgres integration test automatically skips itself when Docker/Testcontainers
> aren’t available, so the suite stays green in restricted CI environments.
