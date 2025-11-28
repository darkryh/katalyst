package com.ead.boshi.retention.services

import com.ead.boshi.shared.constants.DataRetention
import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.storage.repositories.DeliveryStatusRepository
import com.ead.boshi.storage.repositories.MxRecordsRepository
import com.ead.boshi.storage.repositories.SentEmailRepository
import com.ead.katalyst.core.component.Component
import org.slf4j.LoggerFactory

/**
 * Cleans up expired and obsolete data.
 */
class CleanupService(
    private val sentEmailRepository: SentEmailRepository,
    private val deliveryStatusRepository: DeliveryStatusRepository,
    private val mxRecordsRepository: MxRecordsRepository
) : Component {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Cleans up expired sent emails.
     * @param retentionDays number of days to retain emails (null = use default)
     * @param batchSize maximum number of records to delete in one operation
     * @return number of emails deleted
     */
    fun cleanupExpiredEmails(
        retentionDays: Int = DataRetention.DEFAULT_RETENTION_DAYS,
        batchSize: Int = DataRetention.DEFAULT_CLEANUP_BATCH_SIZE
    ): Long {
        logger.debug("Starting cleanup of expired emails (retention: $retentionDays days)")

        val now = System.currentTimeMillis()
        var totalDeleted: Long = 0

        try {
            // Delete all expired emails in batches
            val expiredBeforeTime = now - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
            val deleted = sentEmailRepository.deleteExpiredBefore(expiredBeforeTime)
            totalDeleted = deleted.toLong()

            if (totalDeleted > 0) {
                logger.info("Cleanup completed: deleted $totalDeleted expired emails")
            } else {
                logger.debug("Cleanup completed: deleted $totalDeleted expired emails")
            }
        } catch (e: Exception) {
            logger.error("Error during expired email cleanup", e)
        }

        return totalDeleted
    }

    /**
     * Cleans up failed delivery records that can be permanently discarded.
     * @param deliveryStatusRepository repository to perform cleanup
     * @param batchSize maximum number of records to delete in one operation
     * @return number of failed records deleted
     */
    fun cleanupFailedDeliveries(
        batchSize: Int = DataRetention.DEFAULT_CLEANUP_BATCH_SIZE
    ): Long {
        logger.debug("Starting cleanup of permanently failed deliveries")

        var totalDeleted: Long = 0

        try {
            // Delete all permanently failed delivery records
            val deleted = deliveryStatusRepository.deleteByStatus(DeliveryStatus.PERMANENTLY_FAILED)
            totalDeleted = deleted.toLong()

            if (totalDeleted > 0) {
                logger.info("Cleanup completed: deleted $totalDeleted permanently failed deliveries")
            } else {
                logger.debug("Cleanup completed: deleted $totalDeleted permanently failed deliveries")
            }
        } catch (e: Exception) {
            logger.error("Error during failed delivery cleanup", e)
        }

        return totalDeleted
    }

    /**
     * Cleans up expired MX record cache entries.
     * @param mxRecordsRepository repository to perform cleanup
     * @param batchSize maximum number of records to delete in one operation
     * @return number of cache entries cleaned
     */
    fun cleanupExpiredMxCache(

        batchSize: Int = DataRetention.DEFAULT_CLEANUP_BATCH_SIZE
    ): Long {
        logger.debug("Starting cleanup of expired MX record cache")

        val now = System.currentTimeMillis()
        var totalDeleted: Long = 0

        try {
            // Delete all expired MX cache entries
            val deleted = mxRecordsRepository.deleteExpiredBefore(now)
            totalDeleted = deleted.toLong()

            if (totalDeleted > 0) {
                logger.info("Cleanup completed: cleaned $totalDeleted expired MX cache entries")
            } else {
                logger.debug("Cleanup completed: cleaned $totalDeleted expired MX cache entries")
            }
        } catch (e: Exception) {
            logger.error("Error during MX cache cleanup", e)
        }

        return totalDeleted
    }

    /**
     * Performs full cleanup cycle.
     */
    fun performFullCleanup(
        retentionDays: Int = DataRetention.DEFAULT_RETENTION_DAYS
    ): CleanupStats {
        logger.debug("Starting full cleanup cycle")

        val startTime = System.currentTimeMillis()

        val emailsDeleted = cleanupExpiredEmails(retentionDays)
        val deliveriesDeleted = cleanupFailedDeliveries()
        val mxCacheDeleted = cleanupExpiredMxCache()

        val duration = System.currentTimeMillis() - startTime

        logger.info(
            "Cleanup cycle completed in ${duration}ms: " +
            "emails=$emailsDeleted, deliveries=$deliveriesDeleted, mx_cache=$mxCacheDeleted"
        )

        return CleanupStats(
            emailsDeleted = emailsDeleted,
            deliveriesDeleted = deliveriesDeleted,
            mxCacheDeleted = mxCacheDeleted,
            durationMillis = duration
        )
    }
}

/**
 * Statistics from a cleanup operation.
 */
data class CleanupStats(
    val emailsDeleted: Long,
    val deliveriesDeleted: Long,
    val mxCacheDeleted: Long,
    val durationMillis: Long
) {
    val totalRecordsDeleted: Long
        get() = emailsDeleted + deliveriesDeleted + mxCacheDeleted
}
