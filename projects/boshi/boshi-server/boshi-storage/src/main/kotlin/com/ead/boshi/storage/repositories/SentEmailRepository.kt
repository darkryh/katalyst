package com.ead.boshi.storage.repositories

import com.ead.boshi.shared.models.SentEmailEntity
import com.ead.boshi.storage.tables.SentEmailsTable
import com.ead.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Repository for managing sent emails
 * Extends CrudRepository for automatic CRUD operations
 */
class SentEmailRepository : CrudRepository<Long, SentEmailEntity> {

    override val table: LongIdTable = SentEmailsTable

    /**
     * Find email by message ID
     */
    fun findByMessageId(messageId: String): SentEmailEntity? =
        SentEmailsTable
            .selectAll()
            .where { SentEmailsTable.messageId eq messageId }
            .limit(1)
            .firstOrNull()
            ?.let(::map)

    /**
     * Find all emails for a specific user
     */
    fun findByUserId(userId: Long): List<SentEmailEntity> =
        SentEmailsTable
            .selectAll()
            .where { SentEmailsTable.userId eq userId }
            .map(::map)

    /**
     * Find expired emails (for cleanup)
     * @param now current timestamp in milliseconds
     * @param limit maximum number to return
     */
    fun findExpiredEmails(now: Long, limit: Int = 1000): List<SentEmailEntity> =
        SentEmailsTable
            .selectAll()
            .where { SentEmailsTable.expiresAtMillis lessEq now }
            .limit(limit)
            .map(::map)

    /**
     * Find emails for a user created within a date range
     */
    fun findByUserIdAndDateRange(userId: Long, startMillis: Long, endMillis: Long): List<SentEmailEntity> =
        SentEmailsTable
            .selectAll()
            .where {
                (SentEmailsTable.userId eq userId) and
                (SentEmailsTable.submittedAtMillis greaterEq startMillis) and
                (SentEmailsTable.submittedAtMillis lessEq endMillis)
            }
            .map(::map)

    /**
     * Count emails by user ID
     */
    fun countByUserId(userId: Long): Long =
        SentEmailsTable
            .selectAll()
            .where { SentEmailsTable.userId eq userId }
            .count()

    /**
     * Count spam emails
     */
    fun countSpamDetected(): Long =
        SentEmailsTable
            .selectAll()
            .where { SentEmailsTable.spamDetected eq true }
            .count()

    /**
     * Count expired emails
     */
    fun countExpired(now: Long): Long =
        SentEmailsTable
            .selectAll()
            .where { SentEmailsTable.expiresAtMillis lessEq now }
            .count()

    /**
     * Delete email by ID
     * @param id email ID to delete
     * @return number of rows deleted
     */
    fun deleteById(id: Long): Int =
        SentEmailsTable.deleteWhere { SentEmailsTable.id eq id }

    /**
     * Delete expired emails before given timestamp
     * @param beforeMillis delete emails that expired before this time
     * @return number of rows deleted
     */
    fun deleteExpiredBefore(beforeMillis: Long): Int =
        SentEmailsTable.deleteWhere { SentEmailsTable.expiresAtMillis lessEq beforeMillis }

    /**
     * Find all emails with pagination
     */
    fun findAll(page: Int, limit: Int): List<SentEmailEntity> =
        SentEmailsTable
            .selectAll()
            .limit(limit)
            .offset(((page - 1) * limit).toLong())
            .map(::map)
}
