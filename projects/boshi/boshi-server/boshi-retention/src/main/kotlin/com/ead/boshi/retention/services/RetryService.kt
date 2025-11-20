@file:Suppress("unused")

package com.ead.boshi.retention.services

import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.smtp.services.SmtpDeliveryService
import com.ead.boshi.storage.repositories.DeliveryStatusRepository
import com.ead.boshi.storage.repositories.SentEmailRepository
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.cron.CronExpression
import com.ead.katalyst.scheduler.extension.requireScheduler
import org.slf4j.LoggerFactory

/**
 * Service for retrying failed email deliveries
 * Handles batch processing of emails pending retry
 */
class RetryService(
    private val sentEmailRepository: SentEmailRepository,
    private val deliveryStatusRepository: DeliveryStatusRepository,
    private val smtpDeliveryService: SmtpDeliveryService
) : Service {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val scheduler = requireScheduler()

    companion object {
        private const val RETRY_BATCH_SIZE = 100
    }


    /**
     * Job: Retry pending emails
     * Schedule: Every 30 minutes
     * Attempts delivery for emails scheduled for retry
     */
    // Registered by Katalyst scheduler via return type
    fun retryPendingEmailsJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.retry-pending-emails"),
        task = {
            logger.info("Running scheduled retry of pending emails")
            val retried = retryPendingEmails()
            logger.info("Retry job completed: processed $retried emails")
        },
        cronExpression = CronExpression("0 */30 * * * ?") // Every 30 minutes
    )

    /**
     * Retry all pending emails that are due for retry
     * Checks nextRetryAtMillis and attempts delivery for eligible emails
     * @return number of emails retried
     */
    suspend fun retryPendingEmails(): Long {
        logger.info("Starting retry of pending emails")

        return transactionManager.transaction {
            val now = System.currentTimeMillis()
            var totalRetried: Long = 0

            try {
                // Find emails due for retry
                val pendingEmails = deliveryStatusRepository.findFailedDueForRetry(now, RETRY_BATCH_SIZE)

                if (pendingEmails.isEmpty()) {
                    logger.debug("No emails pending retry")
                    return@transaction totalRetried
                }

                logger.info("Found ${pendingEmails.size} emails due for retry")

                for (deliveryStatus in pendingEmails) {
                    try {
                        // Find the associated sent email
                        val sentEmail = sentEmailRepository.findById(deliveryStatus.sentEmailId)
                            ?: run {
                                logger.warn("Sent email not found for delivery status ${deliveryStatus.id}")
                                continue
                            }

                        // Attempt delivery
                        logger.debug("Retrying delivery for message: ${sentEmail.messageId}")
                        val updatedStatus = smtpDeliveryService.deliverEmail(sentEmail, deliveryStatus)

                        // Update the delivery status record
                        deliveryStatusRepository.save(updatedStatus)

                        totalRetried++

                        // Log success or failure
                        when (updatedStatus.status) {
                            DeliveryStatus.DELIVERED -> {
                                logger.info("Retry successful for message: ${sentEmail.messageId}")
                            }
                            DeliveryStatus.PERMANENTLY_FAILED -> {
                                logger.warn("Message permanently failed after retry: ${sentEmail.messageId}")
                            }
                            else -> {
                                logger.debug("Message scheduled for next retry: ${sentEmail.messageId}")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error retrying message ${deliveryStatus.messageId}", e)
                    }
                }

                logger.info("Retry batch completed: $totalRetried emails processed")
            } catch (e: Exception) {
                logger.error("Error during email retry processing", e)
            }

            totalRetried
        }
    }
}
