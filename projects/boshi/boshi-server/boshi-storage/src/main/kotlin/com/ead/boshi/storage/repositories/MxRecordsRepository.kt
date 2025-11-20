package com.ead.boshi.storage.repositories

import com.ead.boshi.shared.models.MxRecordEntity
import com.ead.boshi.storage.tables.MxRecordsTable
import com.ead.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory

/**
 * Repository for managing cached DNS MX records
 * Extends CrudRepository for automatic CRUD operations
 */
@Suppress("unused")
class MxRecordsRepository : CrudRepository<Long, MxRecordEntity> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val table: LongIdTable = MxRecordsTable

    /**
     * Find cached MX records for a domain
     * @param domain domain name to look up
     * @param now current timestamp in milliseconds
     * @return MX record if cached and not expired, null otherwise
     */
    fun findValidByDomain(domain: String, now: Long): MxRecordEntity? =
        MxRecordsTable
            .selectAll()
            .where {
                (MxRecordsTable.domain eq domain) and
                (MxRecordsTable.expiresAtMillis greaterEq now)
            }
            .limit(1)
            .firstOrNull()
            ?.let(::map)

    /**
     * Find all MX records for a domain (including expired ones)
     */
    fun findByDomain(domain: String): List<MxRecordEntity> =
        MxRecordsTable
            .selectAll()
            .where { MxRecordsTable.domain eq domain }
            .map(::map)

    /**
     * Find expired cache entries for cleanup
     * @param now current timestamp in milliseconds
     * @param limit maximum number to return
     */
    fun findExpiredEntries(now: Long, limit: Int = 100): List<MxRecordEntity> =
        MxRecordsTable
            .selectAll()
            .where { MxRecordsTable.expiresAtMillis lessEq now }
            .limit(limit)
            .map(::map)

    /**
     * Check if a domain is cached
     */
    fun isCached(domain: String, now: Long): Boolean =
        MxRecordsTable
            .selectAll()
            .where {
                (MxRecordsTable.domain eq domain) and
                (MxRecordsTable.expiresAtMillis greaterEq now)
            }
            .count() > 0

    /**
     * Count cached entries
     */
    fun countCached(now: Long): Long =
        MxRecordsTable
            .selectAll()
            .where { MxRecordsTable.expiresAtMillis greaterEq now }
            .count()

    /**
     * Count total cache entries
     */
    fun countAll(): Long =
        MxRecordsTable.selectAll().count()

    /**
     * Delete MX record by ID
     * @param id record ID to delete
     * @return number of rows deleted
     */
    fun deleteById(id: Long): Int =
        MxRecordsTable.deleteWhere { MxRecordsTable.id eq id }

    /**
     * Delete expired MX cache entries
     * @param beforeMillis delete entries that expired before this time
     * @return number of rows deleted
     */
    fun deleteExpiredBefore(beforeMillis: Long): Int =
        MxRecordsTable.deleteWhere { MxRecordsTable.expiresAtMillis lessEq beforeMillis }

    /**
     * Delete all MX records for a domain
     * @param domain domain name to delete
     * @return number of rows deleted
     */
    fun deleteByDomain(domain: String): Int =
        MxRecordsTable.deleteWhere { MxRecordsTable.domain eq domain }
}
