@file:Suppress("unused")

package com.ead.boshi.retention.jobs

import com.ead.boshi.retention.services.CleanupService
import com.ead.boshi.storage.repositories.DeliveryStatusRepository
import com.ead.boshi.storage.repositories.MxRecordsRepository
import com.ead.boshi.storage.repositories.SentEmailRepository
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
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

    fun cleanupExpiredEmailsJob() = scheduler.jobs {
        cron("boshi.scheduler.cleanup-emails", "0 2 * * * ?") {
            logger.info("Running scheduled cleanup of expired emails")

            val deleted = cleanupService.cleanupExpiredEmails()

            logger.info("Cleanup job completed: deleted $deleted expired emails")
        }
    }

    fun cleanupFailedDeliveriesJob() = scheduler.jobs {
        cron("boshi.scheduler.cleanup-failed", "0 3 * * * ?") {
            logger.info("Running scheduled cleanup of permanently failed deliveries")

            val deleted = transactionManager.transaction {
                cleanupService.cleanupFailedDeliveries()
            }

            logger.info("Cleanup job completed: deleted $deleted permanently failed deliveries")
        }
    }

    fun cleanupMxCacheJob() = scheduler.jobs {
        cron("boshi.scheduler.cleanup-mx-cache", "0 4 * * * ?") {
            logger.debug("Running scheduled cleanup of expired MX record cache")

            val deleted = transactionManager.transaction {
                cleanupService.cleanupExpiredMxCache()
            }

            logger.info("Cleanup job completed: cleaned $deleted expired MX cache entries")
        }
    }
}
