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
 * Create missing schemas/tables for discovered tables. This is most useful for
 * local/test apps.
     */
    CREATE_MISSING,

    /**
     * Create missing schemas/tables and log pending migration statements. This
     * does not execute generated migration SQL.
     */
    CREATE_MISSING_AND_VALIDATE,
}

/**
 * Schema lifecycle options applied after component discovery has populated the
 * table registry.
 */
data class SchemaManagementOptions(
    val policy: SchemaPolicy = SchemaPolicy.VALIDATE,
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
