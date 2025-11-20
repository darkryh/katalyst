package com.ead.boshi.shared.models

import com.ead.katalyst.repositories.Identifiable
import kotlinx.serialization.Serializable

/**
 * Email submitted via API for delivery
 * Stored in sent_emails table
 */
data class SentEmailEntity(
    override val id: Long? = null,
    val messageId: String,
    val userId: Long,
    val senderEmail: String,
    val recipientEmail: String,
    val subject: String,
    val body: String,
    val contentHash: String,
    val contentSizeBytes: Long,
    val submittedAtMillis: Long,
    val expiresAtMillis: Long,
    val spamScore: Double,
    val spamDetected: Boolean,
    val ipAddress: String,
    val tags: String? = null,
    val metadata: String? = null
) : Identifiable<Long>

/**
 * Tracks delivery status of an email
 * Stored in delivery_status table
 */
data class DeliveryStatusEntity(
    override val id: Long? = null,
    val sentEmailId: Long,
    val messageId: String,
    val status: String,
    val statusChangedAtMillis: Long,
    val lastAttemptAtMillis: Long,
    val attemptCount: Int,
    val nextRetryAtMillis: Long? = null,
    val errorMessage: String? = null,
    val deliveredAtMillis: Long? = null,
    val metadata: String? = null
) : Identifiable<Long>

/**
 * Cached MX records from DNS lookups
 * Stored in mx_records table
 */
data class MxRecordEntity(
    override val id: Long? = null,
    val domain: String,
    val mxHostname: String,
    val priority: Int,
    val resolvedAtMillis: Long,
    val expiresAtMillis: Long,
    val failedAttempts: Int = 0,
    val lastFailedAtMillis: Long? = null
) : Identifiable<Long>

/**
 * Result of email validation (not stored, returned from validation service)
 */
data class EmailValidationResult(
    val isValid: Boolean,
    val senderValidated: Boolean,
    val recipientValidated: Boolean,
    val spamScore: Double,
    val isSpam: Boolean,
    val validationDetails: Map<String, Any>
)

/**
 * HTTP API request for sending an email
 */
@Serializable
data class SendEmailRequest(
    val from: String,
    val to: String,
    val subject: String,
    val html: String,
    val tags: String? = null,
    val metadata: Map<String, String>? = null
)

/**
 * HTTP API response for email submission
 */
@Serializable
data class SendEmailResponse(
    val messageId: String,
    val status: String,
    val message: String
)

/**
 * HTTP API response for delivery status
 */
@Serializable
data class EmailStatusResponse(
    val messageId: String,
    val status: String,
    val attempts: Int,
    val errorMessage: String? = null,
    val deliveredAt: Long? = null
)

/**
 * HTTP API response for server statistics
 */
@Serializable
data class EmailStatsResponse(
    val totalReceived: Long,
    val pending: Long,
    val delivered: Long,
    val failed: Long,
    val permanentlyFailed: Long,
    val uniqueSenders: Long,
    val uniqueRecipients: Long
)
