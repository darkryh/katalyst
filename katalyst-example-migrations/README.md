# Katalyst Example Migrations

This module is a self-contained playground that demonstrates how Katalyst
discovers and executes migrations. It ships a tiny todo-list domain, two
migrations, a CLI runner, and a set of integration tests so you can verify the
entire flow without touching the full sample application.

## What’s inside?

| File | Purpose |
| --- | --- |
| `schema/ExampleTables.kt` | Exposed table definitions for `todo_lists` and `todo_items`. Kept in-module to avoid depending on the main example. |
| `V2024102001CreateTodoSchema.kt` | Migration that generates/executes the DDL for both tables using `SchemaDiffService`. Tagged `schema`. |
| `V2024102002SeedSampleTodoList.kt` | Seeds a demo list + two items so the result is human-readable. Tagged `demo`/`seed`, `blocking = false`. |
| `cli/TodoMigrationCli.kt` | Minimal CLI that wires the necessary Koin beans (DatabaseFactory, SchemaDiffService, MigrationRunner, migrations) and executes them. |
| `src/test/.../ExampleMigrationsTest.kt` | H2-backed integration tests that prove the schema + seed migrations are idempotent and respect tag filters. |

## How discovery works

1. Any Katalyst app that includes this module and scans
   `com.ead.katalyst.example` will auto-register the migrations thanks to
   `AutoBindingRegistrar`.
2. Calling `enableMigrations { ... }` installs `MigrationFeature`, which provides
   `SchemaDiffService`, `MigrationRunner`, and your `MigrationOptions`.
3. When Koin is ready, the feature fetches every `KatalystMigration` bean and
   hands them to `MigrationRunner`, which:
   - creates the history table (`katalyst_schema_migrations` by default)
   - sorts migrations by `version`/`id`
   - applies `includeTags`/`excludeTags`, baseline, etc.
   - runs each migration (transactional by default) through the shared
     `DatabaseFactory`
   - records the checksum + metadata so re-runs are no-ops

## Running the standalone CLI

```bash
./gradlew :katalyst-example-migrations:run
```

Environment variables (optional):

| Variable | Default | Description |
| --- | --- | --- |
| `TODO_DB_URL` | `jdbc:h2:file:./build/todo-example-db;AUTO_SERVER=TRUE` | JDBC URL used by the CLI |
| `TODO_DB_USER` | `sa` | Database username |
| `TODO_DB_PASSWORD` | *(empty)* | Database password |
| `TODO_DB_DRIVER` | `org.h2.Driver` | Fully qualified JDBC driver |

## Testing

- `./gradlew :katalyst-example-migrations:test` – runs the H2 integration suite.
- `DOCKER_HOST=... ./gradlew :katalyst-example-migrations:test` – with Docker available, the Postgres Testcontainers case also runs to validate against a production-like database.
- `./gradlew :katalyst-example:build` – proves the migrations are discovered
  when the full example scans `com.ead.katalyst.example`.

## Tweaking migration execution

To skip the demo seed in production:

```kotlin
enableMigrations {
    excludeTags = setOf("demo", "seed")
}
```

Or to only run schema/bootstrap migrations:

```kotlin
enableMigrations {
    includeTags = setOf("schema")
}
```

Because the migrations live under the same package as the example app, they will
be auto-discovered without any manual Koin wiring. The CLI mirrors that behavior
in a smaller surface so you can experiment quickly.
