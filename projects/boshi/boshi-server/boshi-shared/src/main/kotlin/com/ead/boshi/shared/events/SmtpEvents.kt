package com.ead.boshi.shared.events

import io.github.darkryh.katalyst.events.DomainEvent

/**
 * Published when an email is received via API and stored in database
 */
data class EmailReceivedEvent(
    val sentEmailId: Long,
    val messageId: String,
    val senderEmail: String,
    val recipientEmail: String,
    val subject: String,
    val receivedAtMillis: Long,
    val userId: Long
) : DomainEvent

/**
 * Published when an email passes validation checks
 */
data class EmailValidatedEvent(
    val sentEmailId: Long,
    val senderEmail: String,
    val recipientEmail: String,
    val validationLevel: String,
    val isValid: Boolean,
    val spamScore: Double,
    val validatedAtMillis: Long
) : DomainEvent

/**
 * Published when MX records are resolved for a domain
 */
data class MxRecordResolvedEvent(
    val domain: String,
    val mxHostnames: List<String>,
    val resolvedAtMillis: Long,
    val cacheExpiresAt: Long
) : DomainEvent

/**
 * Published when a delivery attempt is made
 */
data class DeliveryAttemptedEvent(
    val sentEmailId: Long,
    val messageId: String,
    val recipientEmail: String,
    val mxHostname: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val attemptedAtMillis: Long
) : DomainEvent

/**
 * Published when delivery status changes
 */
data class DeliveryStatusChangedEvent(
    val sentEmailId: Long,
    val messageId: String,
    val oldStatus: String,
    val newStatus: String,
    val changedAtMillis: Long,
    val reason: String? = null
) : DomainEvent

/**
 * Published when final delivery attempt fails (will not retry)
 */
data class DeliveryFailedEvent(
    val sentEmailId: Long,
    val messageId: String,
    val recipientEmail: String,
    val errorMessage: String?,
    val attemptCount: Int,
    val failedAtMillis: Long,
    val isPermanent: Boolean
) : DomainEvent

/**
 * Published when an email expires and is deleted
 */
data class EmailExpiredEvent(
    val sentEmailId: Long,
    val messageId: String,
    val senderEmail: String,
    val recipientEmail: String,
    val expiredAtMillis: Long,
    val retainedForDays: Int
) : DomainEvent

/**
 * Published when cleanup job completes
 */
data class CleanupCompletedEvent(
    val deletedCount: Int,
    val batchesProcessed: Int,
    val completedAtMillis: Long,
    val durationMillis: Long
) : DomainEvent

/**
 * Published when DNS/MX lookup fails
 */
data class DnsLookupFailedEvent(
    val domain: String,
    val failureReason: String,
    val failedAtMillis: Long
) : DomainEvent
