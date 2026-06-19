# Migrations

The `katalyst-migrations` module discovers `KatalystMigration` implementations under your
scanned packages and runs them with a `MigrationRunner`. It supports startup execution and
read-only operational checks (status, validate, dry-run). For a walkthrough, see
[Run database migrations](../how-to/run-migrations.md).

## KatalystMigration

```kotlin
interface KatalystMigration {
    val id: String                          // unique identifier (required)
    val description: String get() = id       // human description
    val checksum: String get() = id          // stored and validated on later runs
    val version: Long                        // ordering; defaults to numeric prefix of id
    val tags: Set<String> get() = emptySet() // for include/exclude filtering
    fun up()                                 // perform the change (required)
    fun down() {}                            // present, but rollback is not orchestrated
}
```

```kotlin
class CreateUsersTable : KatalystMigration {
    override val id = "2024060101_create_users"
    override val description = "Create users table"
    override val checksum = "create-users-v1"
    override val tags = setOf("prod")
    override fun up() { SchemaUtils.create(UsersTable) }
}
```

- `id` must be unique across all migrations.
- `version` controls primary ordering and defaults to the numeric prefix of `id`.
- `checksum` is stored in the history table; a changed checksum for an applied migration is a
  validation error.
- `down()` exists on the interface, but the current runtime runner does not orchestrate
  rollbacks.

## Enabling at startup

```kotlin
features {
    enableMigrations {
        runAtStartup = true
        schemaTable = "katalyst_schema_migrations"
        includeTags = setOf("prod")
        targetVersion = "2024060105"
    }
}
```

The lambda configures a `MigrationOptions`.

## MigrationOptions

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `schemaTable` | `String` | `"katalyst_schema_migrations"` | History table name. |
| `runAtStartup` | `Boolean` | `true` | Run migrations during startup. Set `false` to defer to a CLI/CI job. |
| `includeTags` | `Set<String>` | empty | Allow-list; when non-empty, only migrations with a matching tag run. |
| `excludeTags` | `Set<String>` | empty | Deny-list; migrations with a matching tag are skipped. |
| `dryRun` | `Boolean` | `false` | Log what would run without touching the database or history. |
| `stopOnFailure` | `Boolean` | `true` | Stop on the first blocking failure; otherwise continue and log. |
| `baselineVersion` | `String?` | `null` | Mark every migration with id ≤ baseline as applied without running `up()`. |
| `targetVersion` | `String?` | `null` | Inclusive: include migrations with id ≤ target. |
| `scriptDirectory` | `Path` | `db/migrations` | Where generated migration scripts are written. |

## MigrationRunner

Construct from the active `DatabaseFactory` and a `MigrationOptions`. All inspection methods
are read-only and never create the history table or execute migrations.

```kotlin
val runner = MigrationRunner(databaseFactory, MigrationOptions(runAtStartup = false))
```

### status(migrations) → MigrationStatusReport

Reports migration state without changing anything. If the history table does not exist yet,
every eligible source migration is reported as pending.

```kotlin
val report = runner.status(discoveredMigrations)
report.pending        // not yet applied
report.applied        // already applied
report.baselined      // marked applied via baseline
report.filtered       // excluded by tags/target
report.unknownApplied // in history but not in source
```

### validateMigrations(migrations) → MigrationValidationResult

```kotlin
val validation = runner.validateMigrations(discoveredMigrations)
if (!validation.valid) validation.errors.forEach(::println)
validation.throwIfInvalid()   // fail a CLI/CI task immediately
```

Validation checks: duplicate ids, duplicate version/id pairs, and checksum drift for applied
migrations.

### dryRun(migrations) → MigrationDryRunReport

```kotlin
val dryRun = runner.dryRun(discoveredMigrations)
dryRun.pending.forEach { println("Would run ${it.id}: ${it.description}") }
```

Validates first, then lists the migrations that would execute given the current tags, target,
and database history — without touching the database.

## Gradle/CLI boundary

The module exposes the runtime operations a future Gradle or CLI integration needs, but the
build-logic plugin does not yet provide `katalystMigrationStatus`, `katalystValidateMigrations`,
or `katalystMigrate` tasks, because it does not currently own application discovery or database
configuration loading.

## See also

- [Run database migrations](../how-to/run-migrations.md)
- [Persistence](persistence.md) — the schema migrations evolve.

