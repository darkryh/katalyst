package com.ead.boshi.smtp.services

import com.ead.boshi.shared.constants.DeliveryStatus
import com.ead.boshi.shared.exceptions.EmailCantBeBlankOrNull
import com.ead.boshi.shared.exceptions.EmailRequestedNotFound
import com.ead.boshi.shared.exceptions.EmailSpamException
import com.ead.boshi.shared.models.DeliveryStatusEntity
import com.ead.boshi.shared.models.EmailDto
import com.ead.boshi.shared.models.EmailStatsResponse
import com.ead.boshi.shared.models.SendEmailRequest
import com.ead.boshi.shared.models.SentEmailEntity
import com.ead.boshi.storage.repositories.DeliveryStatusRepository
import com.ead.boshi.storage.repositories.SentEmailRepository
import io.github.darkryh.katalyst.core.component.Service
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class EmailService(
    private val sentEmailRepository : SentEmailRepository,
    private val deliveryStatusRepository : DeliveryStatusRepository,
    private val emailValidationService: EmailValidationService,
) : Service {

    suspend fun send(request: SendEmailRequest,remoteHost : String) = transactionManager.transaction {
    // Validate email addresses
        val validationResult = emailValidationService.validateEmail(
            senderEmail = request.from,
            recipientEmail = request.to,
            subject = request.subject,
            body = request.html
        )

        // Reject if detected as spam
        if (validationResult.isSpam) {
            throw EmailSpamException(validationResult.spamScore)
        }

        // Generate unique message ID
        val messageId = UUID.randomUUID().toString()
        val nowInstant = Instant.now()
        val now = nowInstant.epochSecond
        val expiresAt = nowInstant.plus(7L, ChronoUnit.DAYS).epochSecond

        // Calculate content hash
        val contentHash = calculateHash(request.subject + request.html)

        // Create sent email entity
        val sentEmail = SentEmailEntity(
            messageId = messageId,
            userId = 0, // Will be set by middleware in full implementation
            senderEmail = request.from,
            recipientEmail = request.to,
            subject = request.subject,
            body = request.html,
            contentHash = contentHash,
            contentSizeBytes = request.html.length.toLong(),
            submittedAtMillis = now,
            expiresAtMillis = expiresAt,
            spamScore = validationResult.spamScore,
            spamDetected = validationResult.isSpam,
            ipAddress = remoteHost,
            tags = request.tags,
            metadata = request.metadata?.let { Json.encodeToString(it) }
        )

        // Save sent email
        val savedEmail = sentEmailRepository.save(sentEmail)

        // Create initial delivery status
        val deliveryStatus = DeliveryStatusEntity(
            sentEmailId = savedEmail.id!!,
            messageId = messageId,
            status = DeliveryStatus.PENDING,
            statusChangedAtMillis = now,
            lastAttemptAtMillis = now,
            attemptCount = 0,
            nextRetryAtMillis = now
        )

        deliveryStatusRepository.save(deliveryStatus)
    }

    suspend fun getMessageStatus(messageId : String?) : DeliveryStatusEntity = transactionManager.transaction {
        if ( messageId == null || messageId.isBlank()) {
            throw EmailCantBeBlankOrNull()
        }

        return@transaction deliveryStatusRepository
            .findByMessageId(messageId)
            ?: throw EmailRequestedNotFound()
    }

    suspend fun getEmailStats() = transactionManager.transaction {
        val totalReceived = sentEmailRepository.count()

        val pending = deliveryStatusRepository.countByStatus(DeliveryStatus.PENDING)
        val delivered = deliveryStatusRepository.countByStatus(DeliveryStatus.DELIVERED)
        val failed = deliveryStatusRepository.countByStatus(DeliveryStatus.FAILED)
        val permanentlyFailed = deliveryStatusRepository.countByStatus(DeliveryStatus.PERMANENTLY_FAILED)

        val uniqueSenders = minOf(totalReceived, totalReceived)
        val uniqueRecipients = minOf(totalReceived, totalReceived)

        EmailStatsResponse(
            totalReceived = totalReceived,
            pending = pending,
            delivered = delivered,
            failed = failed,
            permanentlyFailed = permanentlyFailed,
            uniqueSenders = uniqueSenders,
            uniqueRecipients = uniqueRecipients
        )
    }

    suspend fun getEmails(page: Int, limit: Int): List<EmailDto> = transactionManager.transaction {
        val emails = sentEmailRepository.findAll(page, limit)
        if (emails.isEmpty()) return@transaction emptyList()

        val messageIds = emails.map { it.messageId }
        val statuses = deliveryStatusRepository.findByMessageIds(messageIds).associateBy { it.messageId }

        emails.map { email ->
            val status = statuses[email.messageId]
            EmailDto(
                id = email.id ?: 0,
                messageId = email.messageId,
                sender = email.senderEmail,
                recipient = email.recipientEmail,
                subject = email.subject,
                body = email.body,
                status = status?.status ?: DeliveryStatus.PENDING,
                timestamp = email.submittedAtMillis,
                tags = email.tags
            )
        }
    }

    /**
     * Calculate SHA-256 hash of content
     */
    private fun calculateHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
