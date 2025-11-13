package com.ead.katalyst.migrations.options

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuration options that control how Katalyst executes database migrations.
 *
 * These values intentionally mirror the recommendations from the official
 * Exposed migration guide:
 * https://www.jetbrains.com/help/exposed/migrations.html
 */
data class MigrationOptions(
    /**
     * Name of the schema history table. Defaults to `katalyst_schema_migrations`
     * to avoid clashing with user tables while remaining explicit.
     */
    val schemaTable: String = "katalyst_schema_migrations",

    /**
     * Whether migrations should run automatically during application startup.
     * Set to false when you prefer an external CLI/CI job to run them.
     */
    val runAtStartup: Boolean = true,

    /**
     * Optional allow-list of tags. When not empty only migrations that declare
     * at least one matching tag will run.
     */
    val includeTags: Set<String> = emptySet(),

    /**
     * Optional deny-list of tags. Any migration that declares a tag contained
     * in this set will be skipped.
     */
    val excludeTags: Set<String> = emptySet(),

    /**
     * Enable dry-run mode to log the migrations that would run without touching
     * the database or the schema history table.
     */
    val dryRun: Boolean = false,

    /**
     * Stop the runner as soon as a blocking migration fails. When false the
     * runner will continue with non-blocking migrations, logging failures.
     */
    val stopOnFailure: Boolean = true,

    /**
     * Optional baseline version. Every migration with an id less than or equal
     * to the baseline will be marked as applied without executing `up()`. This
     * mirrors Exposed's "baseline on migrate" capability.
     */
    val baselineVersion: String? = null,

    /**
     * Optional target version. When specified the runner stops once it reaches
     * the migration whose id matches the target (exclusive).
     */
    val targetVersion: String? = null,

    /**
     * Directory where generated migration scripts are stored when leveraging
     * [com.ead.katalyst.migrations.service.SchemaDiffService.generateMigrationScript]. Defaults to `db/migrations`.
     */
    val scriptDirectory: Path = Paths.get("db/migrations")
)
