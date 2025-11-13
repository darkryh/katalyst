package com.ead.katalyst.migrations.service

import com.ead.katalyst.database.DatabaseFactory
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.core.Table
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.LoggerFactory

/**
 * Thin wrapper around Exposed [MigrationUtils] that centralises schema diff operations.
 *
 * The service can be injected into migrations or tooling to generate SQL statements,
 * validate drift, or emit migration scripts to disk.
 */
class SchemaDiffService(
    private val databaseFactory: DatabaseFactory,
    private val scriptDirectory: Path
) {

    private val logger = LoggerFactory.getLogger(SchemaDiffService::class.java)

    /**
     * Generate the SQL statements required to align the database with the provided tables.
     *
     * Mirrors `MigrationUtils.statementsRequiredForDatabaseMigration`.
     */
    fun statementsRequiredForDatabaseMigration(vararg tables: Table): List<String> =
        transaction(databaseFactory.database) {
            MigrationUtils.statementsRequiredForDatabaseMigration(*tables)
        }

    /**
     * Generate statements for missing columns only (non-destructive diff).
     */
    fun missingColumnStatements(vararg tables: Table): List<String> =
        transaction(databaseFactory.database) {
            SchemaUtils.addMissingColumnsStatements(*tables)
        }

    /**
     * Generate statements for removing columns that no longer exist in code.
     */
    fun dropUnmappedColumnsStatements(vararg tables: Table): List<String> =
        transaction(databaseFactory.database) {
            MigrationUtils.dropUnmappedColumnsStatements(*tables)
        }

    /**
     * Produce a migration script file using Exposed's generator.
     *
     * @param scriptName File name (e.g. `V2__add_primary_key.sql`)
     * @param overwrite Whether to overwrite existing file (default true)
     */
    @OptIn(ExperimentalDatabaseMigrationApi::class)
    fun generateMigrationScript(
        scriptName: String,
        vararg tables: Table,
        overwrite: Boolean = true,
        withLogs: Boolean = true
    ): Path {
        if (tables.isEmpty()) error("At least one table is required to generate a migration script")

        Files.createDirectories(scriptDirectory)
        val normalizedName = scriptName.removeSuffix(".sql")
        val target = scriptDirectory.resolve("$normalizedName.sql")
        if (!overwrite && Files.exists(target)) {
            logger.warn("Migration script {} already exists and overwrite=false, skipping generation", target)
            return target
        }

        return transaction(databaseFactory.database) {
            MigrationUtils.generateMigrationScript(
                *tables,
                scriptDirectory = scriptDirectory.pathString,
                scriptName = normalizedName,
                withLogs = withLogs
            ).toPath()
        }
    }
}
