@file:Suppress("unused")

package com.ead.boshi.retention.services

import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.smtp.services.SmtpDeliveryService
import com.ead.boshi.storage.repositories.DeliveryStatusRepository
import com.ead.boshi.storage.repositories.SentEmailRepository
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import org.slf4j.LoggerFactory

/**
 * Retries failed email deliveries in batches.
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
     * Job: Retry pending emails.
     * Schedule: Every 30 minutes.
     */
    // Registered by Katalyst scheduler via return type
    fun retryPendingEmailsJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.retry-pending-emails"),
        task = {
            logger.debug("Running scheduled retry of pending emails")
            val retried = retryPendingEmails()

            logger.info("Retry job completed: processed $retried emails")
        },
        cronExpression = CronExpression("0 */30 * * * ?") // Every 30 minutes
    )

    /**
     * Retries pending emails due for delivery.
     * @return number of emails retried
     */
    suspend fun retryPendingEmails(): Long {
        logger.debug("Starting retry of pending emails")

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

                logger.debug("Found ${pendingEmails.size} emails due for retry")

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
