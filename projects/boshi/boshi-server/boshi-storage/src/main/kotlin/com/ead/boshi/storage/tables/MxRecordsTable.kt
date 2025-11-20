package com.ead.boshi.storage.tables

import com.ead.boshi.shared.models.MxRecordEntity
import com.ead.katalyst.core.persistence.Table
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/**
 * Exposed table definition for cached DNS MX records
 * Maps to mx_records table in database
 * Used to cache MX lookups (24 hour TTL)
 */
object MxRecordsTable : LongIdTable("mx_records"), Table<Long, MxRecordEntity> {
    // Domain being cached
    val domain = varchar("domain", 255).uniqueIndex()

    // MX record details
    val mxHostname = varchar("mx_hostname", 255)
    val priority = integer("priority")

    // Cache management
    val resolvedAtMillis = long("resolved_at_millis")
    val expiresAtMillis = long("expires_at_millis").index()

    // Failure tracking
    val failedAttempts = integer("failed_attempts").default(0)
    val lastFailedAtMillis = long("last_failed_at_millis").nullable()

    override fun mapRow(row: ResultRow): MxRecordEntity = MxRecordEntity(
        id = row[id].value,
        domain = row[domain],
        mxHostname = row[mxHostname],
        priority = row[priority],
        resolvedAtMillis = row[resolvedAtMillis],
        expiresAtMillis = row[expiresAtMillis],
        failedAttempts = row[failedAttempts],
        lastFailedAtMillis = row[lastFailedAtMillis]
    )

    override fun assignEntity(
        statement: UpdateBuilder<*>,
        entity: MxRecordEntity,
        skipIdColumn: Boolean
    ) {
        if (!skipIdColumn && entity.id != null) {
            statement[id] = EntityID(entity.id as Long, this)
        }
        statement[domain] = entity.domain
        statement[mxHostname] = entity.mxHostname
        statement[priority] = entity.priority
        statement[resolvedAtMillis] = entity.resolvedAtMillis
        statement[expiresAtMillis] = entity.expiresAtMillis
        statement[failedAttempts] = entity.failedAttempts
        statement[lastFailedAtMillis] = entity.lastFailedAtMillis
    }
}
