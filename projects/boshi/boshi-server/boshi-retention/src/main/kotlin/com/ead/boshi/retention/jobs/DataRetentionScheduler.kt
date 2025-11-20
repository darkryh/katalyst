@file:Suppress("unused")

package com.ead.boshi.retention.jobs

import com.ead.boshi.retention.services.CleanupService
import com.ead.boshi.retention.services.RetryService
import com.ead.boshi.storage.repositories.DeliveryStatusRepository
import com.ead.boshi.storage.repositories.MxRecordsRepository
import com.ead.boshi.storage.repositories.SentEmailRepository
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.cron.CronExpression
import com.ead.katalyst.scheduler.extension.requireScheduler
import org.slf4j.LoggerFactory

/**
 * Service registering scheduled jobs for data retention and cleanup
 * Automatically discovered and initialized by Katalyst
 */
class DataRetentionScheduler(
    private val cleanupService: CleanupService,
    private val retryService: RetryService,
    private val sentEmailRepository: SentEmailRepository,
    private val deliveryStatusRepository: DeliveryStatusRepository,
    private val mxRecordsRepository: MxRecordsRepository
) : Service {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scheduler = requireScheduler()

    init {
        println("Running scheduled retry of pending emails")
    }

    /**
     * Job: Clean up expired emails
     * Schedule: Daily at 2:00 AM
     * Default retention: 14 days
     */
    // Registered by Katalyst scheduler via return type
    @Suppress("unused")
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
     * Job: Clean up permanently failed deliveries
     * Schedule: Daily at 3:00 AM
     * Removes delivery records marked as permanently failed
     */
    // Registered by Katalyst scheduler via return type
    @Suppress("unused")
    fun cleanupFailedDeliveriesJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.cleanup-failed"),
        task = {
            logger.info("Running scheduled cleanup of permanently failed deliveries")
            val deleted = cleanupService.cleanupFailedDeliveries()
            logger.info("Cleanup job completed: deleted $deleted permanently failed deliveries")
        },
        cronExpression = CronExpression("0 3 * * * ?") // 3:00 AM daily
    )

    /**
     * Job: Clean up expired MX record cache
     * Schedule: Daily at 4:00 AM
     * Removes stale cached MX records
     */
    // Registered by Katalyst scheduler via return type
    @Suppress("unused")
    fun cleanupMxCacheJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.cleanup-mx-cache"),
        task = {
            logger.info("Running scheduled cleanup of expired MX record cache")
            val deleted = cleanupService.cleanupExpiredMxCache()
            logger.info("Cleanup job completed: cleaned $deleted expired MX cache entries")
        },
        cronExpression = CronExpression("0 4 * * * ?") // 4:00 AM daily
    )

    /**
     * Job: Retry pending emails
     * Schedule: Every 30 minutes
     * Attempts delivery for emails scheduled for retry
     */
    // Registered by Katalyst scheduler via return type
    @Suppress("unused")
    fun retryPendingEmailsJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.retry-pending-emails"),
        task = {
            logger.info("Running scheduled retry of pending emails")
            val retried = retryService.retryPendingEmails()
            logger.info("Retry job completed: processed $retried emails")
        },
        cronExpression = CronExpression("0 0/1 * * * ?") // Every 30 minutes
    )
}
