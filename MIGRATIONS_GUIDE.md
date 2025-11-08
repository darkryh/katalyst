# Database Migrations (Exposed)

Katalyst now ships an optional `katalyst-migrations` module that wraps the official Exposed migration workflow described in JetBrains' guide: https://www.jetbrains.com/help/exposed/migrations.html. The implementation follows the documented limitations—migrations execute sequentially, are recorded in a schema history table, and checksum mismatches abort startup—while integrating seamlessly with Katalyst's automatic DI/bootstrap process.

## How it works

1. Create migration classes that implement `com.ead.katalyst.migrations.KatalystMigration`. Each migration encapsulates a *single* schema change and exposes metadata such as `id`, `tags`, and `checksum`.
2. Because `KatalystMigration` lives in `katalyst-di`, the scanner automatically discovers these classes (no annotations required). They are registered in Koin like any other component.
3. Add the optional module dependency:

```kotlin
dependencies {
    implementation(projects.katalystMigrations)
}
```

4. Enable the feature inside your `katalystApplication { ... }` block:

```kotlin
import com.ead.katalyst.migrations.extensions.enableMigrations

katalystApplication(args) {
    database(DatabaseConfig(...))
    scanPackages("com.ead.myapp")
    enableMigrations {
        schemaTable = "schema_migrations"
        includeTags = setOf("prod")      // optional filters
    }
}
```

5. On startup the feature constructs a `MigrationRunner`, ensures the history table exists (default `katalyst_schema_migrations`), filters migrations by tags/target version, and executes them sequentially within Exposed transactions. Results are recorded with timing/metadata for auditing.

## Defining migrations

```kotlin
object CreateUsersTable : KatalystMigration {
    override val id = "2024100901_create_users_table"
    override val description = "Creates the users table with e-mail uniqueness."
    override val tags = setOf("prod", "dev-seed")

    override fun up() {
        SchemaUtils.create(UsersTable)
    }
}
```

* `id` controls ordering—follow Exposed's recommendation of timestamp prefixes.
* `checksum` defaults to `id`, but override it to detect drift when the body changes.
* `tags` let you include/exclude migrations per environment via `MigrationOptions`.
* `blocking` defaults to `true`; set to `false` for best-effort data backfills.

## Configuration surface (`MigrationOptions`)

| Option            | Description                                                                 |
|-------------------|-----------------------------------------------------------------------------|
| `schemaTable`     | History table name (default `katalyst_schema_migrations`).                  |
| `runAtStartup`    | Automatically run migrations during bootstrap (default `true`).             |
| `includeTags`     | Allow-list of tags to execute.                                              |
| `excludeTags`     | Deny-list of tags to skip.                                                  |
| `dryRun`          | Log migrations without executing or recording them.                         |
| `stopOnFailure`   | Whether blocking migrations abort startup.                                  |
| `baselineVersion` | Treat migrations `<= baselineVersion` as already applied without running.   |
| `targetVersion`   | Stop after reaching the target version (inclusive).                         |

These mirror Exposed's documented policies for baselining and target versions, ensuring parity with the official tooling.

## CLI / external runners

When `runAtStartup = false`, the feature only registers `MigrationRunner` in Koin. You can inject it in a CLI/Gradle task and call `runMigrations(koin.getAll())` to execute migrations in a controlled environment, keeping application startup lean while respecting the same history table/checksum rules.

## Limitations

* Migrations remain sequential with no automatic dependency graphs (official Exposed limitation).
* Rollbacks (`down()`) are optional; destructive migrations should be tagged and reviewed carefully.
* Checksum mismatches abort startup to avoid silent drift, matching Exposed's guidance.

Refer to JetBrains' official documentation and examples (https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-migrations) for migration authoring patterns—the Katalyst integration simply automates discovery, ordering, and execution within the DI lifecycle.
