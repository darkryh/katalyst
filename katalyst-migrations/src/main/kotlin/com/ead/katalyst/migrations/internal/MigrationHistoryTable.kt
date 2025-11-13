package com.ead.katalyst.migrations.internal

import org.jetbrains.exposed.v1.core.Table

internal class MigrationHistoryTable(tableName: String) : Table(tableName) {
    val migrationId = varchar("migration_id", 255)
    val checksum = varchar("checksum", 255).nullable()
    val description = varchar("description", 1024).nullable()
    val executionTimeMs = long("execution_time_ms")
    val executedAtEpochMs = long("executed_at_ms")
    val tags = text("tags").nullable()
    val status = varchar("status", 32)

    override val primaryKey = PrimaryKey(migrationId)
}
