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
     * Create missing schemas/tables for discovered tables. This is most useful for local/test
     * apps.
     *
     * Creation only ever adds a table that is absent; it never alters one that already exists.
     * A table whose columns have drifted from its Kotlin definition therefore survives this
     * policy untouched and unreported — use [CREATE_MISSING_AND_VALIDATE] to catch that.
     */
    CREATE_MISSING,

    /**
     * Create missing schemas/tables, then verify the result matches the discovered tables.
     *
     * Identical to [CREATE_MISSING] on a fresh database. The two diverge against an *existing*
     * table that has drifted, which creation leaves alone: this policy reports the pending
     * statements and, like [VALIDATE], **fails startup** unless
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
)

@KatalystDslMarker
class SchemaManagementBuilder {
    private var policy: SchemaPolicy = SchemaPolicy.VALIDATE
    private var failOnPendingStatements: Boolean = true

    fun none() {
        policy = SchemaPolicy.NONE
    }

    fun validateOnStartup(failOnPendingStatements: Boolean = true) {
        policy = SchemaPolicy.VALIDATE
        this.failOnPendingStatements = failOnPendingStatements
    }

    fun createMissing() {
        policy = SchemaPolicy.CREATE_MISSING
    }

    /**
     * Create missing tables, then fail startup if the schema still does not match the discovered
     * tables — which happens when an *existing* table has drifted, since creation never alters
     * one. Pass `failOnPendingStatements = false` to log a warning instead of aborting.
     */
    fun createMissingAndValidate(failOnPendingStatements: Boolean = true) {
        policy = SchemaPolicy.CREATE_MISSING_AND_VALIDATE
        this.failOnPendingStatements = failOnPendingStatements
    }

    internal fun build(): SchemaManagementOptions =
        SchemaManagementOptions(
            policy = policy,
            failOnPendingStatements = failOnPendingStatements,
        )
}
