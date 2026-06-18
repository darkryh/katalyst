# Migrations

Katalyst migrations are discovered through `KatalystMigration` and executed by `MigrationRunner`. The runtime API supports startup execution and read-only operational checks that can be wired into a CLI, Gradle task, CI job, or admin endpoint.

## Migration Contract

```kotlin
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

Rules:
- `id` must be unique.
- `checksum` is stored in the history table and validated on future runs.
- `version` controls primary ordering and defaults to the numeric prefix of `id`.
- `tags` participate in `includeTags` and `excludeTags` filtering.
- `down()` is preserved on the interface, but rollback orchestration is not part of the current runtime runner.

## Startup Execution

```kotlin
enableMigrations {
    runAtStartup = true
    schemaTable = "katalyst_schema_migrations"
    includeTags = setOf("prod")
    targetVersion = "2024060105"
}
```

`targetVersion` is inclusive. Migrations whose id is less than or equal to the target are eligible to run.

For production services, pair startup migrations with an explicit schema policy:

```kotlin
schema {
    validateOnStartup()
}
```

Use `schema { none() }` when an external migration job owns database changes before the service boots.

## Operational APIs

Create a runner from the active `DatabaseFactory` and the same `MigrationOptions` used at startup:

```kotlin
val runner = MigrationRunner(databaseFactory, MigrationOptions(runAtStartup = false))
```

### Status

```kotlin
val report = runner.status(discoveredMigrations)

report.pending
report.applied
report.baselined
report.filtered
report.unknownApplied
```

`status()` is read-only. It does not create the history table, mark baselines, or execute migrations. If the history table does not exist yet, all eligible source migrations are reported as pending.

### Validate

```kotlin
val validation = runner.validateMigrations(discoveredMigrations)

if (!validation.valid) {
    validation.errors.forEach(::println)
}
```

Validation checks:
- duplicate migration ids,
- duplicate version/id pairs,
- checksum drift for migrations already present in the history table.

Use `validation.throwIfInvalid()` when a CLI or CI task should fail immediately.

### Dry Run

```kotlin
val dryRun = runner.dryRun(discoveredMigrations)

dryRun.pending.forEach { migration ->
    println("Would run ${migration.id}: ${migration.description}")
}
```

`dryRun()` is also read-only. It validates the migration set first, then returns the migrations that would execute with the current tags, target, and database history.

## Current Gradle/CLI Boundary

The migration module now exposes the runtime operations needed by a future Gradle or CLI integration. The build-logic plugin does not yet provide `katalystMigrationStatus`, `katalystValidateMigrations`, or `katalystMigrate` tasks because it does not currently own application discovery or database configuration loading. Add those tasks after the application/container SPI is stabilized.
