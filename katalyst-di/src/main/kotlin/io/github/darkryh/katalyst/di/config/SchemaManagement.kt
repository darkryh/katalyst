package io.github.darkryh.katalyst.di.config

import io.github.darkryh.katalyst.core.dsl.KatalystDslMarker

/**
 * Controls how Katalyst handles discovered database tables during bootstrap.
 *
 * The default is fail-fast validation. Local/test apps that need automatic
 * schema creation should explicitly choose [CREATE_MISSING].
 */
enum class SchemaPolicy {
    /**
     * Do not inspect or modify the schema during DI bootstrap.
     */
    NONE,

    /**
     * Verify the database schema matches discovered tables and fail startup when
     * Exposed reports pending migration statements.
     */
    VALIDATE,

    /**
     * Create whatever is missing for the discovered tables — schemas, tables, **and columns**. This
     * is most useful for local/test apps.
     *
     * "Missing" means the same thing at every level, which is the whole point of the policy:
     *  - a schema the tables declare and the database does not have is created;
     *  - a table that does not exist is created;
     *  - a column an existing table is missing is added (`ALTER TABLE ... ADD COLUMN`), along with
     *    the constraints that column needs.
     *
     * Adding a column to a Kotlin `Table` after the first boot is the common case here: `CREATE
     * TABLE IF NOT EXISTS` skips an existing table whole, so before this the new column silently
     * never reached the database and only surfaced as a SQL error on the first query that selected
     * it.
     *
     * Strictly additive. A column the database has and Kotlin does not is never dropped, and a
     * column whose *type* drifted is never altered — neither can be done without risking data, so
     * both are left to a real migration. Use [CREATE_MISSING_AND_VALIDATE] to be told about them
     * instead of silently carrying on.
     *
     * Column creation is the one part that is switchable:
     * [SchemaManagementOptions.createMissingColumns] defaults to `true` and can be set to `false`
     * for table-only creation.
     */
    CREATE_MISSING,

    /**
     * Create missing schemas/tables/columns, then verify the result matches the discovered tables.
     *
     * Identical to [CREATE_MISSING] whenever creation could close the whole gap — a fresh database,
     * or one that was only missing columns. The two diverge on drift that additive creation is not
     * allowed to fix (a column dropped from the Kotlin definition, or one whose type changed): this
     * policy reports the remaining pending statements and, like [VALIDATE], **fails startup** unless
     * [SchemaManagementOptions.failOnPendingStatements] is set to false. It never executes the
     * generated migration SQL.
     */
    CREATE_MISSING_AND_VALIDATE,
}

/**
 * Schema lifecycle options applied after component discovery has populated the
 * table registry.
 */
data class SchemaManagementOptions(
    val policy: SchemaPolicy = SchemaPolicy.VALIDATE,
    /**
     * Whether pending migration statements abort startup. Applies to both [SchemaPolicy.VALIDATE]
     * and [SchemaPolicy.CREATE_MISSING_AND_VALIDATE]; set false to log a warning and continue.
     */
    val failOnPendingStatements: Boolean = true,
    /**
     * Whether the creating policies also add columns an existing table is missing, on by default.
     *
     * Applies to [SchemaPolicy.CREATE_MISSING] and [SchemaPolicy.CREATE_MISSING_AND_VALIDATE];
     * ignored by [SchemaPolicy.NONE] and [SchemaPolicy.VALIDATE], which create nothing.
     *
     * On (the default), "missing" means the same thing for a column as for a table. Set it to
     * `false` to restore table-only creation — the right call when columns are owned by real
     * migrations, or when the generated `ALTER TABLE ... ADD COLUMN` cannot succeed on a table that
     * already has rows (a `NOT NULL` column with no default). Turning it off does not hide the
     * drift: pair it with [SchemaPolicy.CREATE_MISSING_AND_VALIDATE] and the missing column is
     * reported instead of applied.
     */
    val createMissingColumns: Boolean = true,
)

@KatalystDslMarker
class SchemaManagementBuilder {
    private var policy: SchemaPolicy = SchemaPolicy.VALIDATE
    private var failOnPendingStatements: Boolean = true
    private var createMissingColumns: Boolean = true

    fun none() {
        policy = SchemaPolicy.NONE
    }

    fun validateOnStartup(failOnPendingStatements: Boolean = true) {
        policy = SchemaPolicy.VALIDATE
        this.failOnPendingStatements = failOnPendingStatements
    }

    /**
     * Create every missing schema, table **and column** for the discovered tables. Strictly
     * additive: nothing is ever dropped or retyped. See [SchemaPolicy.CREATE_MISSING].
     *
     * @param createMissingColumns keep the default `true` to have a column added to a `Table` after
     * the first boot reach an already-existing table; pass `false` for table-only creation. See
     * [SchemaManagementOptions.createMissingColumns].
     */
    fun createMissing(createMissingColumns: Boolean = true) {
        policy = SchemaPolicy.CREATE_MISSING
        this.createMissingColumns = createMissingColumns
    }

    /**
     * Create missing tables and columns, then fail startup if the schema still does not match the
     * discovered tables — which happens on drift additive creation is not allowed to fix (a column
     * the database has and Kotlin does not, or one whose type changed). Pass
     * `failOnPendingStatements = false` to log a warning instead of aborting.
     *
     * @param createMissingColumns keep the default `true` to add missing columns before validating;
     * pass `false` to have them REPORTED by the validation step rather than applied. See
     * [SchemaManagementOptions.createMissingColumns].
     */
    fun createMissingAndValidate(
        failOnPendingStatements: Boolean = true,
        createMissingColumns: Boolean = true,
    ) {
        policy = SchemaPolicy.CREATE_MISSING_AND_VALIDATE
        this.failOnPendingStatements = failOnPendingStatements
        this.createMissingColumns = createMissingColumns
    }

    internal fun build(): SchemaManagementOptions =
        SchemaManagementOptions(
            policy = policy,
            failOnPendingStatements = failOnPendingStatements,
            createMissingColumns = createMissingColumns,
        )
}
