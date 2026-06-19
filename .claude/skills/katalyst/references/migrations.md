# Migrations reference

`katalyst-migrations` discovers `KatalystMigration` implementations under scanned packages and
runs them with a `MigrationRunner`. Enable with `features { enableMigrations { } }`.

## KatalystMigration

`io.github.darkryh.katalyst.migrations.KatalystMigration`:

```kotlin
interface KatalystMigration {
    val id: String                          // unique (required)
    val description: String get() = id
    val checksum: String get() = id          // stored; validated on later runs
    val version: Long                        // ordering; defaults to numeric prefix of id
    val tags: Set<String> get() = emptySet()
    fun up()                                 // required
    fun down() {}                            // present; rollback NOT orchestrated by the runner
}
```

```kotlin
import io.github.darkryh.katalyst.migrations.KatalystMigration
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

class CreateUsersTable : KatalystMigration {
    override val id = "2024060101_create_users"
    override val description = "Create users table"
    override val checksum = "create-users-v1"
    override val tags = setOf("prod")
    override fun up() { SchemaUtils.create(UsersTable) }
}
```

Data fix using the injected `DatabaseFactory`:

```kotlin
import io.github.darkryh.katalyst.database.DatabaseFactory
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class NormalizeStatuses(private val databaseFactory: DatabaseFactory) : KatalystMigration {
    override val id = "2024060102_normalize_status"
    override fun up() {
        transaction(databaseFactory.database) {
            exec("UPDATE auth_accounts SET status = 'active' WHERE status IS NULL")
        }
    }
}
```

Rules: `id` unique; `version` defaults to id's numeric prefix; a changed `checksum` for an
already-applied migration is a validation error; `tags` drive include/exclude filtering;
`down()` is not run by the current runtime.

## Enabling at startup

```kotlin
import io.github.darkryh.katalyst.migrations.extensions.enableMigrations

features {
    enableMigrations {
        runAtStartup = true
        schemaTable = "katalyst_schema_migrations"
        includeTags = setOf("prod")
        targetVersion = "2024060105"   // inclusive
    }
}
```

Pair with an explicit schema policy: `schema { validateOnStartup() }`, or `schema { none() }`
when an external job owns the schema.

## MigrationOptions (the enableMigrations { } receiver)

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `schemaTable` | `String` | `"katalyst_schema_migrations"` | History table name. |
| `runAtStartup` | `Boolean` | `true` | Run during startup; false defers to CLI/CI. |
| `includeTags` | `Set<String>` | empty | Allow-list; non-empty → only matching tags run. |
| `excludeTags` | `Set<String>` | empty | Deny-list; matching tags skipped. |
| `dryRun` | `Boolean` | `false` | Log would-run set without touching DB/history. |
| `stopOnFailure` | `Boolean` | `true` | Stop on first blocking failure, else continue+log. |
| `baselineVersion` | `String?` | `null` | Mark id ≤ baseline as applied without running `up()`. |
| `targetVersion` | `String?` | `null` | Inclusive: include id ≤ target. |
| `scriptDirectory` | `Path` | `db/migrations` | Where generated scripts go. |

## MigrationRunner — operational, read-only checks

`io.github.darkryh.katalyst.migrations.runner.MigrationRunner`. Build from the active
`DatabaseFactory` and a `MigrationOptions`:

```kotlin
import io.github.darkryh.katalyst.migrations.runner.MigrationRunner
import io.github.darkryh.katalyst.migrations.options.MigrationOptions

val runner = MigrationRunner(databaseFactory, MigrationOptions(runAtStartup = false))
```

| Method | Returns | Notes |
|--------|---------|-------|
| `status(migrations)` | `MigrationStatusReport` | `.pending`, `.applied`, `.baselined`, `.filtered`, `.unknownApplied`. Read-only; no history table created. |
| `validateMigrations(migrations)` | `MigrationValidationResult` | `.valid`, `.errors`, `.throwIfInvalid()`. Checks dup ids, dup version/id, checksum drift. |
| `dryRun(migrations)` | `MigrationDryRunReport` | `.pending`. Validates then lists would-run set; no DB writes. |

```kotlin
val report = runner.status(discoveredMigrations)
report.pending.forEach { println("pending: ${it.id}") }

val validation = runner.validateMigrations(discoveredMigrations)
validation.throwIfInvalid()

runner.dryRun(discoveredMigrations).pending.forEach { println("would run ${it.id}") }
```

If the history table does not exist yet, `status()` reports all eligible migrations as pending.

## Gradle/CLI note

The runtime ops exist, but the build-logic plugin does not yet ship `katalystMigrationStatus`,
`katalystValidateMigrations`, or `katalystMigrate` tasks — it does not own application discovery
or DB config loading. Drive the runner from app code or a custom task for now.
