# Run database migrations

Migrations evolve your schema in a controlled, repeatable way. Katalyst discovers
`KatalystMigration` implementations under your scanned packages and runs them with a
`MigrationRunner`. This guide covers writing migrations, running them at startup, and
inspecting them operationally.

## Enable migrations

Add the feature toggle and pair it with an explicit schema policy. For services that own
their schema lifecycle, validate on startup and let migrations apply changes:

```kotlin
fun main(args: Array<String>) = katalystApplication(args) {
    engine(NettyServer)
    beanEngine(KoinBeanEngine)
    enableYamlConfiguration()
    database { fromConfiguration() }
    scanPackages("com.example")
    schema { validateOnStartup() }
    features {
        enableMigrations {
            runAtStartup = true
            schemaTable = "katalyst_schema_migrations"
        }
    }
}
```

Use `schema { none() }` when an external migration job applies changes before the service
boots.

## Write a migration

Implement `KatalystMigration`. The `up()` method performs the change; `id` must be unique.

```kotlin
import io.github.darkryh.katalyst.migrations.KatalystMigration
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

class CreateUsersTable : KatalystMigration {
    override val id = "2024060101_create_users"
    override val description = "Create users table"
    override val checksum = "create-users-v1"
    override val tags = setOf("prod")

    override fun up() {
        SchemaUtils.create(UsersTable)
    }
}
```

For data fixes, run SQL inside an Exposed transaction against the injected `DatabaseFactory`:

```kotlin
class NormalizeStatuses(private val databaseFactory: DatabaseFactory) : KatalystMigration {
    override val id = "2024060102_normalize_status"

    override fun up() {
        transaction(databaseFactory.database) {
            exec("UPDATE auth_accounts SET status = 'active' WHERE status IS NULL")
        }
    }
}
```

Rules to know:

- `id` is unique and, by default, its numeric prefix supplies the ordering `version`.
- `checksum` is stored in the history table and validated on later runs; a changed checksum
  for an already-applied migration is a validation error.
- `tags` participate in `includeTags` / `excludeTags` filtering.
- `down()` exists on the interface but rollback orchestration is not part of the current
  runtime runner.

See the [migrations reference](../reference/migrations.md) for the full contract.

## Filter and target migrations

Configure the run through `enableMigrations { … }`:

```kotlin
enableMigrations {
    runAtStartup = true
    includeTags = setOf("prod")        // only run migrations tagged "prod"
    targetVersion = "2024060105"       // inclusive: run ids <= this value
    baselineVersion = "2024060100"     // mark everything <= as applied, without running up()
}
```

All options are listed in the [`MigrationOptions` reference](../reference/migrations.md#migrationoptions).

## Inspect migrations without applying them

Build a `MigrationRunner` from the active `DatabaseFactory` to drive read-only checks from a
CLI, CI job, or admin endpoint.

```kotlin
val runner = MigrationRunner(databaseFactory, MigrationOptions(runAtStartup = false))
```

### Status

```kotlin
val report = runner.status(discoveredMigrations)
println("Pending: ${report.pending.map { it.id }}")
println("Applied: ${report.applied.map { it.id }}")
```

`status()` never creates the history table or executes anything. If the history table does
not exist yet, every eligible migration is reported as pending.

### Validate

```kotlin
val validation = runner.validateMigrations(discoveredMigrations)
if (!validation.valid) validation.errors.forEach(::println)
// or fail hard in CI:
validation.throwIfInvalid()
```

Validation catches duplicate ids, duplicate version/id pairs, and checksum drift.

### Dry run

```kotlin
val dryRun = runner.dryRun(discoveredMigrations)
dryRun.pending.forEach { println("Would run ${it.id}: ${it.description}") }
```

`dryRun()` validates first, then lists what would execute given the current tags, target,
and history — without touching the database.

## Related

- [Migrations reference](../reference/migrations.md) — interfaces, options, and report types.
- [Define tables and repositories](define-tables-and-repositories.md) — the schema you migrate.

