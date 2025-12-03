package com.ead.boshi.storage.repositories

import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.shared.models.DeliveryStatusEntity
import com.ead.boshi.storage.tables.DeliveryStatusTable
import io.github.darkryh.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory

/**
 * Repository for managing delivery status tracking
 * Extends CrudRepository for automatic CRUD operations
 */
class DeliveryStatusRepository : CrudRepository<Long, DeliveryStatusEntity> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val table: LongIdTable = DeliveryStatusTable

    /**
     * Find delivery status by message ID
     */
    fun findByMessageId(messageId: String): DeliveryStatusEntity? =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.messageId eq messageId }
            .limit(1)
            .firstOrNull()
            ?.let(::map)

    /**
     * Find delivery statuses by message IDs
     */
    fun findByMessageIds(messageIds: List<String>): List<DeliveryStatusEntity> =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.messageId inList messageIds }
            .map(::map)

    /**
     * Find all delivery statuses for a sent email
     */
    fun findBySentEmailId(sentEmailId: Long): List<DeliveryStatusEntity> =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.sentEmailId eq sentEmailId }
            .map(::map)

    /**
     * Find pending deliveries (emails that haven't been delivered yet)
     */
    fun findPending(): List<DeliveryStatusEntity> =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.status eq DeliveryStatus.PENDING }
            .map(::map)

    /**
     * Find failed deliveries due for retry
     * @param now current timestamp in milliseconds
     * @param limit maximum number to return
     */
    fun findFailedDueForRetry(now: Long, limit: Int = 100): List<DeliveryStatusEntity> =
        DeliveryStatusTable
            .selectAll()
            .where {
                (DeliveryStatusTable.status eq DeliveryStatus.FAILED) and
                (DeliveryStatusTable.nextRetryAtMillis lessEq now)
            }
            .limit(limit)
            .map(::map)

    /**
     * Find all statuses by given status values
     */
    fun findByStatus(statuses: List<String>): List<DeliveryStatusEntity> =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.status inList statuses }
            .map(::map)

    /**
     * Count emails by status
     */
    fun countByStatus(status: String): Long =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.status eq status }
            .count()

    /**
     * Count emails with given statuses
     */
    fun countByStatuses(statuses: List<String>): Long =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.status inList statuses }
            .count()

    /**
     * Find delivered emails within date range
     */
    fun findDeliveredInRange(startMillis: Long, endMillis: Long): List<DeliveryStatusEntity> =
        DeliveryStatusTable
            .selectAll()
            .where {
                (DeliveryStatusTable.status eq DeliveryStatus.DELIVERED) and
                (DeliveryStatusTable.deliveredAtMillis greaterEq startMillis) and
                (DeliveryStatusTable.deliveredAtMillis lessEq endMillis)
            }
            .map(::map)

    /**
     * Find status for emails that are permanently failed
     */
    fun findPermanentlyFailed(): List<DeliveryStatusEntity> =
        DeliveryStatusTable
            .selectAll()
            .where { DeliveryStatusTable.status eq DeliveryStatus.PERMANENTLY_FAILED }
            .map(::map)

    /**
     * Delete delivery status by ID
     * @param id status record ID to delete
     * @return number of rows deleted
     */
    fun deleteById(id: Long): Int =
        DeliveryStatusTable.deleteWhere { DeliveryStatusTable.id eq id }

    /**
     * Delete all delivery statuses with given status
     * @param status status value to delete (e.g., PERMANENTLY_FAILED)
     * @return number of rows deleted
     */
    fun deleteByStatus(status: String): Int =
        DeliveryStatusTable.deleteWhere { DeliveryStatusTable.status eq status }

    /**
     * Delete delivery status by message ID
     * @param messageId message ID to delete
     * @return number of rows deleted
     */
    fun deleteByMessageId(messageId: String): Int =
        DeliveryStatusTable.deleteWhere { DeliveryStatusTable.messageId eq messageId }
}
