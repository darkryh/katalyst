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
 * Service for initial email delivery
 * Handles batch processing of PENDING emails waiting for first delivery attempt
 */
class InitialEmailDeliveryService(
    private val sentEmailRepository: SentEmailRepository,
    private val deliveryStatusRepository: DeliveryStatusRepository,
    private val smtpDeliveryService: SmtpDeliveryService
) : Service {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val scheduler = requireScheduler()

    companion object {
        private const val INITIAL_DELIVERY_BATCH_SIZE = 50
    }

    /**
     * Job: Process initial delivery of pending emails
     * Schedule: Every minute
     * Attempts initial delivery for emails in PENDING status
     */
    // Registered by Katalyst scheduler via return type
    fun initialEmailDeliveryJob() = scheduler.scheduleCron(
        config = ScheduleConfig("boshi.scheduler.initial-delivery"),
        task = {
            logger.info("Running scheduled initial delivery of pending emails")
            val delivered = deliverPendingEmails()
            logger.info("Initial delivery job completed: processed $delivered emails")
        },
        cronExpression = CronExpression("0 * * * * ?") // Every minute
    )

    /**
     * Process all pending emails and attempt initial delivery
     * Checks for emails in PENDING status and attempts delivery
     * @return number of emails processed
     */
    suspend fun deliverPendingEmails(): Long {
        logger.info("Starting initial delivery of pending emails")

        return transactionManager.transaction {
            var totalProcessed: Long = 0

            try {
                // Find emails in PENDING status
                val pendingDeliveries = deliveryStatusRepository.findPending()

                if (pendingDeliveries.isEmpty()) {
                    logger.debug("No emails pending initial delivery")
                    return@transaction totalProcessed
                }

                // Process in batches to avoid overwhelming the system
                val batches = pendingDeliveries.chunked(INITIAL_DELIVERY_BATCH_SIZE)

                for (batch in batches) {
                    logger.info("Processing batch of ${batch.size} emails for initial delivery")

                    for (deliveryStatus in batch) {
                        try {
                            // Find the associated sent email
                            val sentEmail = sentEmailRepository.findById(deliveryStatus.sentEmailId)
                                ?: run {
                                    logger.warn("Sent email not found for delivery status ${deliveryStatus.id}")
                                    continue
                                }

                            // Attempt initial delivery
                            logger.debug("Attempting initial delivery for message: ${sentEmail.messageId}")
                            val updatedStatus = smtpDeliveryService.deliverEmail(sentEmail, deliveryStatus)

                            // Update the delivery status record
                            deliveryStatusRepository.save(updatedStatus)

                            totalProcessed++

                            // Log success or failure
                            when (updatedStatus.status) {
                                DeliveryStatus.DELIVERED -> {
                                    logger.info("Initial delivery successful for message: ${sentEmail.messageId}")
                                }
                                DeliveryStatus.PERMANENTLY_FAILED -> {
                                    logger.warn("Message permanently failed on initial delivery: ${sentEmail.messageId}")
                                }
                                DeliveryStatus.FAILED -> {
                                    logger.info("Initial delivery failed, scheduled for retry: ${sentEmail.messageId}")
                                }
                                else -> {
                                    logger.debug("Message status: ${updatedStatus.status}, message: ${sentEmail.messageId}")
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error delivering message ${deliveryStatus.messageId}", e)
                        }
                    }
                }

                logger.info("Initial delivery batch completed: $totalProcessed emails processed")
            } catch (e: Exception) {
                logger.error("Error during initial email delivery processing", e)
            }

            totalProcessed
        }
    }
}
