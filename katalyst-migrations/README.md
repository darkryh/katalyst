# Katalyst Migrations

This optional module wires JetBrains Exposed migrations into Katalyst’s automatic DI/bootstrap pipeline. It follows the official Exposed migration guide ( https://www.jetbrains.com/help/exposed/migrations.html ) and keeps migrations sequential, transactional, and checksum-validated.

## When to use it

- You want schema changes to run automatically during `katalystApplication` startup.
- You prefer defining migrations as Kotlin classes instead of raw SQL.
- You need environment-aware seeding (dev/prod tags) managed through configuration.

## 1. Add the dependency

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation(projects.katalystMigrations)
}
```

This brings in the `enableMigrations {}` DSL and registers the migration runner feature.

## 2. Implement migrations

Create Kotlin `object`s (or classes) that implement `com.ead.katalyst.migrations.KatalystMigration`. Each migration represents a single schema change and defines an `id` (usually timestamp-based) plus the `up()` body.

```kotlin
import com.ead.katalyst.migrations.KatalystMigration
import org.jetbrains.exposed.sql.SchemaUtils

object CreateUsersTable : KatalystMigration {
    override val id = "2024100901_create_users_table"
    override val description = "Creates the users table with unique emails"
    override val tags = setOf("prod", "dev")

    override fun up() {
        SchemaUtils.create(UsersTable)
    }
}
```

Place the class anywhere under a package that your `scanPackages()` call covers (e.g., `com.ead.katalyst.example.migrations`). No annotations are required—the Katalyst scanner automatically discovers any class implementing `KatalystMigration`.

## 3. Enable migrations in your application

Add the feature to your existing `katalystApplication` block:

```kotlin
import com.ead.katalyst.migrations.extensions.enableMigrations

fun main(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.config())
    scanPackages("com.ead.katalyst.example")

    enableMigrations {
        schemaTable = "schema_migrations"   // optional (defaults to katalyst_schema_migrations)
        includeTags = setOf("prod")         // only run migrations with matching tags
        runAtStartup = true                 // change to false to run via CLI/CI
    }
}
```

At startup:
1. Migrations implementing `KatalystMigration` are auto-discovered.
2. The feature ensures the schema history table exists.
3. Migrations are sorted by `order` (defaults to timestamp prefix) and executed sequentially via Exposed transactions.
4. Results (status, checksum, timing) are stored in the history table, preventing re-runs.

## Configuration Options

`MigrationOptions` mirrors Exposed’s capabilities:

| Option            | Default                        | Description |
|-------------------|--------------------------------|-------------|
| `schemaTable`     | `katalyst_schema_migrations`   | History table storing applied migrations. |
| `runAtStartup`    | `true`                         | Automatically execute pending migrations when the app boots. |
| `includeTags`     | `emptySet()`                   | Allow-list of tags (only matching migrations run). |
| `excludeTags`     | `emptySet()`                   | Deny-list of tags (skip migrations with these tags). |
| `dryRun`          | `false`                        | Log what would run without touching the DB. |
| `stopOnFailure`   | `true`                         | Stop bootstrap if a *blocking* migration fails. |
| `baselineVersion` | `null`                         | Mark all migrations up to this id as already applied. |
| `targetVersion`   | `null`                         | Stop execution once the target id is reached. |

Tags are useful for environment-specific content: e.g., seed data with `tags = setOf("dev")` and configure `includeTags = setOf("prod")` to skip them in production.

## Running migrations outside startup

Set `runAtStartup = false` to disable automatic execution. You can then resolve `MigrationRunner` from Koin (e.g., inside a CLI or Gradle task) and invoke:

```kotlin
val runner = koin.get<MigrationRunner>()
val migrations = koin.getAll<KatalystMigration>()
runner.runMigrations(migrations)
```

This allows deploying code and running migrations during a separate release step while still recording history in the same table.

## Limitations (per Exposed)

- No automatic dependency graphs—migrations must be ordered manually (usually by timestamped ids).
- Rollbacks (`down()`) are optional and often limited; destructive changes should be carefully reviewed.
- Migrations are sequential and run in a single application instance to avoid concurrent writers.

For advanced authoring patterns (data backfills, transactional scopes, etc.), refer to JetBrains’ examples: https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-migrations.

That’s all you need: define migrations, enable the feature, and Katalyst will manage discovery, ordering, and execution automatically. Happy migrating!
