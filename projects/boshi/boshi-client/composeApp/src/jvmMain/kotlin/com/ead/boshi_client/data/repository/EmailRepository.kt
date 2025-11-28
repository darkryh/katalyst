package com.ead.boshi_client.data.repository

import com.ead.boshi_client.data.db.dao.EmailDao
import com.ead.boshi_client.data.mappers.toDomain
import com.ead.boshi_client.data.mappers.toEmail
import com.ead.boshi_client.data.mappers.toEntity
import com.ead.boshi_client.data.network.BoshiService
import com.ead.boshi_client.data.network.models.SendEmailRequest
import com.ead.boshi_client.domain.models.Email
import com.ead.boshi_client.domain.models.EmailStatus
import com.ead.boshi_client.domain.models.EmailType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for email operations.
 * Coordinates between local Room database and remote Boshi API.
 *
 * Default account: boshi@ead.company
 * Handles sent and received emails locally, syncs with backend for sent emails.
 */
class EmailRepository(
    private val emailDao: EmailDao,
    private val boshiService: BoshiService
) {
    companion object {
        private const val DEFAULT_SENDER = "boshi@ead.company"
    }

    // ==================== REACTIVE FLOWS (for UI binding) ====================

    /**
     * Flow of all sent emails from local database.
     * Updates automatically when emails are added/updated.
     */
    fun getSentEmails(): Flow<List<Email>> {
        return emailDao.getByTypeFlow("SENT")
            .map { entities -> entities.map { it.toDomain() } }
    }

    /**
     * Flow of all received emails from local database.
     */
    fun getReceivedEmails(): Flow<List<Email>> {
        return emailDao.getByTypeFlow("RECEIVED")
            .map { entities -> entities.map { it.toDomain() } }
    }

    /**
     * Flow of all emails (sent and received).
     */
    fun getAllEmails(): Flow<List<Email>> {
        return emailDao.getAll()
            .map { entities -> entities.map { it.toDomain() } }
    }

    // ==================== SEND EMAIL OPERATIONS ====================

    /**
     * Send email via backend SMTP service.
     *
     * Flow:
     * 1. Create SendEmailRequest with provided details
     * 2. Call backend API (POST /emails/send)
     * 3. Receive messageId and initial PENDING status
     * 4. Store in local database
     * 5. Return domain Email object
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param body HTML email body
     * @param tags Optional comma-separated tags
     * @return Result<Email> - Success with stored email or failure with exception
     */
    suspend fun sendEmail(
        to: String,
        subject: String,
        body: String,
        tags: String? = null
    ): Result<Email> = runCatching {
        val request = SendEmailRequest(
            from = DEFAULT_SENDER,
            to = to,
            subject = subject,
            html = body,
            tags = tags
        )

        // Call backend API
        val response = boshiService.sendEmail(request)

        // Map response to domain and store locally
        val email = response.toEmail(to, DEFAULT_SENDER).copy(
            subject = subject,
            body = body,
            tags = tags?.split(",")?.map { it.trim() } ?: emptyList()
        )

        // Store in local database
        val entity = email.toEntity()
        emailDao.insert(entity)

        email
    }

    // ==================== SYNC & REFRESH OPERATIONS ====================

    /**
     * Sync sent emails from backend.
     * Fetches paginated list of emails and stores locally.
     *
     * Used on app startup to populate initial data from backend.
     * Only fetches sent emails - received emails are local-only for testing.
     *
     * @param page Page number for pagination (1-indexed)
     * @param limit Emails per page
     * @return Result<Unit> - Success if sync completed, failure if API error
     */
    suspend fun syncSentEmails(page: Int = 1, limit: Int = 50): Result<Unit> = runCatching {
        val dtos = boshiService.getEmails(page, limit)
        val entities = dtos.map { dto ->
            dto.toEmail(type = EmailType.SENT).toEntity()
        }
        emailDao.insertAll(entities)
    }

    /**
     * Check delivery status of a sent email from backend.
     * Updates local database with latest status.
     *
     * @param messageId The message ID from backend (returned when sending)
     * @return Result<Email> - Updated email with latest delivery status
     */
    suspend fun getEmailStatus(messageId: String): Result<Email> = runCatching {
        val statusResponse = boshiService.getEmailStatus(messageId)

        // Update local database with new status
        emailDao.updateStatusByMessageId(messageId, statusResponse.status)

        // Fetch and return updated email from local DB
        val entity = emailDao.getByMessageId(messageId)
            ?: throw IllegalStateException("Email with messageId $messageId not found after status update")

        entity.toDomain()
    }

    /**
     * Clean up expired emails from local database.
     * Only deletes emails with expiresAtMillis set and past the expiration time.
     *
     * Emails without expiration (expiresAtMillis = 0) are kept indefinitely.
     *
     * @return Number of emails deleted
     */
    suspend fun cleanupExpiredEmails(): Int {
        val now = System.currentTimeMillis()
        return emailDao.deleteExpired(now)
    }

    // ==================== QUERY & UTILITY OPERATIONS ====================

    /**
     * Get a single email by messageId.
     *
     * @param messageId The unique message ID
     * @return Email if found, null otherwise
     */
    suspend fun getEmailByMessageId(messageId: String): Email? {
        return emailDao.getByMessageId(messageId)?.toDomain()
    }

    /**
     * Get count of sent emails.
     */
    suspend fun getSentEmailsCount(): Long {
        return emailDao.countByType("SENT")
    }

    /**
     * Get count of received emails.
     */
    suspend fun getReceivedEmailsCount(): Long {
        return emailDao.countByType("RECEIVED")
    }

    /**
     * Get count of emails with specific status.
     *
     * @param type Email type (SENT or RECEIVED)
     * @param status Email status (PENDING, DELIVERED, FAILED, PERMANENTLY_FAILED)
     */
    suspend fun getCountByTypeAndStatus(type: EmailType, status: EmailStatus): Long {
        return emailDao.countByTypeAndStatus(type.name, status.name)
    }

    /**
     * Verify that an email still exists on the backend.
     * Used to validate against backend data loss.
     *
     * @param messageId The message ID to verify
     * @return true if email exists on backend, false otherwise
     */
    suspend fun verifyEmailExistsOnBackend(messageId: String): Boolean = runCatching {
        val status = boshiService.getEmailStatus(messageId)
        status.messageId.isNotEmpty()
    }.getOrNull() ?: false
}
