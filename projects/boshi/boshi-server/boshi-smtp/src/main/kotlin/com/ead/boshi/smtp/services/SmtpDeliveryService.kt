package com.ead.boshi.smtp.services

import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.shared.constants.RetryPolicy
import com.ead.boshi.shared.exceptions.DeliveryException
import com.ead.boshi.shared.models.DeliveryStatusEntity
import com.ead.boshi.shared.models.SentEmailEntity
import com.ead.boshi.smtp.clients.SmtpClient
import com.ead.boshi.smtp.dns.MxRecordResolver
import com.ead.katalyst.core.component.Component
import org.slf4j.LoggerFactory

/**
 * Service orchestrating email delivery via SMTP
 * Handles delivery attempts, retries, and status tracking
 */
class SmtpDeliveryService(
    val smtpClient : SmtpClient,
    val mxResolver : MxRecordResolver
) : Component {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 300_000L // 5 minutes
    }

    /**
     * Attempt to deliver an email
     * @param sentEmail the email entity to deliver
     * @param deliveryStatus current delivery status
     * @return updated delivery status
     */
    fun deliverEmail(
        sentEmail: SentEmailEntity,
        deliveryStatus: DeliveryStatusEntity
    ): DeliveryStatusEntity {
        logger.info("Attempting delivery of message: ${sentEmail.messageId}, attempt ${deliveryStatus.attemptCount + 1}")

        return try {
            // Resolve MX records for recipient domain
            val recipientDomain = sentEmail.recipientEmail.substringAfterLast("@")

            val mxRecords = try {
                mxResolver.resolveMxRecords(recipientDomain)
            } catch (e: Exception) {
                logger.warn("Failed to resolve MX records for $recipientDomain: ${e.message}", e)
                emptyList()
            }

            if (mxRecords.isEmpty()) {
                // DNS failure or no MX records found - treat as temporary failure to allow retries
                logger.error("No MX records found for domain: $recipientDomain - will retry")
                val errorMsg = "DNS resolution failed or no MX records found for domain: $recipientDomain"
                return handleDeliveryFailure(deliveryStatus, errorMsg)
            }

            // Try each MX record in priority order
            var lastError: String? = null
            for (mxRecord in mxRecords) {
                try {
                    logger.debug("Trying MX host: ${mxRecord.mxHostname} (priority: ${mxRecord.priority})")

                    smtpClient.sendEmail(
                        smtpHost = mxRecord.mxHostname,
                        senderEmail = sentEmail.senderEmail,
                        recipientEmail = sentEmail.recipientEmail,
                        subject = sentEmail.subject,
                        body = sentEmail.body,
                        messageId = sentEmail.messageId
                    )

                    logger.info("Email delivered successfully: ${sentEmail.messageId} to ${sentEmail.recipientEmail}")
                    return deliveryStatus.copy(
                        status = DeliveryStatus.DELIVERED,
                        deliveredAtMillis = System.currentTimeMillis(),
                        statusChangedAtMillis = System.currentTimeMillis(),
                        attemptCount = deliveryStatus.attemptCount + 1,
                        lastAttemptAtMillis = System.currentTimeMillis()
                    )
                } catch (e: DeliveryException) {
                    logger.warn("Delivery attempt failed for MX host ${mxRecord.mxHostname}: ${e.message}")
                    lastError = e.message
                    // Continue to next MX record
                }
            }

            // All MX records failed, determine if permanent failure or retry
            handleDeliveryFailure(deliveryStatus, lastError)

        } catch (e: Exception) {
            logger.error("Unexpected error during delivery of ${sentEmail.messageId}", e)
            handleDeliveryFailure(deliveryStatus, e.message ?: "Unknown error")
        }
    }

    /**
     * Determine delivery status after failure
     */
    private fun handleDeliveryFailure(
        deliveryStatus: DeliveryStatusEntity,
        errorMessage: String?
    ): DeliveryStatusEntity {
        val newAttemptCount = deliveryStatus.attemptCount + 1
        val now = System.currentTimeMillis()

        return if (newAttemptCount >= RetryPolicy.MAX_RETRIES) {
            logger.warn("Email permanently failed after $newAttemptCount attempts")
            deliveryStatus.copy(
                status = DeliveryStatus.PERMANENTLY_FAILED,
                errorMessage = errorMessage,
                statusChangedAtMillis = now,
                attemptCount = newAttemptCount,
                lastAttemptAtMillis = now
            )
        } else {
            // Schedule retry with exponential backoff
            val nextRetryDelay = calculateRetryDelay(newAttemptCount)
            val nextRetryTime = now + nextRetryDelay

            logger.info("Scheduling retry for message (attempt $newAttemptCount), next retry at ${nextRetryTime}ms")
            deliveryStatus.copy(
                status = DeliveryStatus.FAILED,
                errorMessage = errorMessage,
                statusChangedAtMillis = now,
                attemptCount = newAttemptCount,
                lastAttemptAtMillis = now,
                nextRetryAtMillis = nextRetryTime
            )
        }
    }

    /**
     * Calculate retry delay with exponential backoff
     */
    private fun calculateRetryDelay(attemptNumber: Int): Long {
        if (RetryPolicy.USE_EXPONENTIAL_BACKOFF) {
            val multiplier = Math.pow(RetryPolicy.BACKOFF_MULTIPLIER, attemptNumber.toDouble()).toLong()
            val exponentialDelay = RetryPolicy.INITIAL_RETRY_DELAY_SECONDS.toLong() * multiplier
            return exponentialDelay * 1000L
        }
        return RetryPolicy.INITIAL_RETRY_DELAY_SECONDS * 1000L
    }
}
