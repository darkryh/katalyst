@file:Suppress("unused")

package com.ead.boshi.retention.jobs

import com.ead.boshi.retention.services.CleanupService
import com.ead.boshi.storage.repositories.DeliveryStatusRepository
import com.ead.boshi.storage.repositories.MxRecordsRepository
import com.ead.boshi.storage.repositories.SentEmailRepository
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.cron.CronExpression
import com.ead.katalyst.scheduler.extension.requireScheduler
import org.slf4j.LoggerFactory

/**
 * Registers scheduled jobs for data retention and cleanup.
 */
class DataRetentionScheduler(
    private val cleanupService: CleanupService,
    private val sentEmailRepository: SentEmailRepository,
    private val deliveryStatusRepository: DeliveryStatusRepository,
    private val mxRecordsRepository: MxRecordsRepository
) : Service {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scheduler = requireScheduler()

    /**
     * Job: Clean up expired emails.
     * Schedule: Daily at 2:00 AM.
     */
    // Registered by Katalyst scheduler via return type
    fun cleanupExpiredEmailsJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.cleanup-emails"),
        task = {
            logger.info("Running scheduled cleanup of expired emails")

            val deleted = cleanupService.cleanupExpiredEmails()

            logger.info("Cleanup job completed: deleted $deleted expired emails")
        },
        cronExpression = CronExpression("0 2 * * * ?") // 2:00 AM daily
    )

    /**
     * Job: Clean up permanently failed deliveries.
     * Schedule: Daily at 3:00 AM.
     */
    // Registered by Katalyst scheduler via return type
    fun cleanupFailedDeliveriesJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.cleanup-failed"),
        task = {
            logger.info("Running scheduled cleanup of permanently failed deliveries")

            val deleted = transactionManager.transaction {
                cleanupService.cleanupFailedDeliveries()
            }

            logger.info("Cleanup job completed: deleted $deleted permanently failed deliveries")
        },
        cronExpression = CronExpression("0 3 * * * ?") // 3:00 AM daily
    )

    /**
     * Job: Clean up expired MX record cache.
     * Schedule: Daily at 4:00 AM.
     */
    // Registered by Katalyst scheduler via return type
    fun cleanupMxCacheJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.cleanup-mx-cache"),
        task = {
            logger.debug("Running scheduled cleanup of expired MX record cache")

            val deleted = transactionManager.transaction {
                cleanupService.cleanupExpiredMxCache()
            }

            logger.info("Cleanup job completed: cleaned $deleted expired MX cache entries")
        },
        cronExpression = CronExpression("0 4 * * * ?") // 4:00 AM daily
    )
}
