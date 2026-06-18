package com.ead.boshi.storage.tables

import com.ead.boshi.shared.models.MxRecordEntity
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Exposed table definition for cached DNS MX records
 * Maps to mx_records table in database
 * Used to cache MX lookups (24 hour TTL)
 */
object MxRecordsTable : LongIdTable("dns_service.mx_records"), Table<Long, MxRecordEntity> {
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

    override val mapping = mapping<Long, MxRecordEntity> {
        generatedId(id, MxRecordEntity::id)
        field(domain, MxRecordEntity::domain)
        field(mxHostname, MxRecordEntity::mxHostname)
        field(priority, MxRecordEntity::priority)
        field(resolvedAtMillis, MxRecordEntity::resolvedAtMillis)
        field(expiresAtMillis, MxRecordEntity::expiresAtMillis)
        field(failedAttempts, MxRecordEntity::failedAttempts)
        field(lastFailedAtMillis, MxRecordEntity::lastFailedAtMillis)

        construct {
            MxRecordEntity(
                id = this[id],
                domain = this[domain],
                mxHostname = this[mxHostname],
                priority = this[priority],
                resolvedAtMillis = this[resolvedAtMillis],
                expiresAtMillis = this[expiresAtMillis],
                failedAttempts = this[failedAttempts],
                lastFailedAtMillis = this[lastFailedAtMillis]
            )
        }
    }
}
