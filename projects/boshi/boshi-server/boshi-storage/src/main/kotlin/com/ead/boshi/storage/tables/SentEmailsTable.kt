package com.ead.boshi.storage.tables

import com.ead.boshi.shared.models.SentEmailEntity
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Exposed table definition for sent emails
 * Maps to sent_emails table in database
 */
object SentEmailsTable : LongIdTable("email_service.sent_emails"), Table<Long, SentEmailEntity> {
    // Core fields
    val messageId = varchar("message_id", 255).uniqueIndex()
    val userId = long("user_id").index()
    val senderEmail = varchar("sender_email", 255)
    val recipientEmail = varchar("recipient_email", 255)
    val subject = varchar("subject", 500)
    val body = text("body")
    val contentHash = varchar("content_hash", 64)
    val contentSizeBytes = long("content_size_bytes")

    // Timestamps
    val submittedAtMillis = long("submitted_at_millis")
    val expiresAtMillis = long("expires_at_millis").index()

    // Spam/Security
    val spamScore = double("spam_score")
    val spamDetected = bool("spam_detected")
    val ipAddress = varchar("ip_address", 45)

    // Metadata
    val tags = varchar("tags", 500).nullable()
    val metadata = text("metadata").nullable()

    // Composite index for efficient queries
    init {
        index(false, userId, expiresAtMillis)
    }

    override val mapping = mapping<Long, SentEmailEntity> {
        generatedId(id, SentEmailEntity::id)
        field(messageId, SentEmailEntity::messageId)
        field(userId, SentEmailEntity::userId)
        field(senderEmail, SentEmailEntity::senderEmail)
        field(recipientEmail, SentEmailEntity::recipientEmail)
        field(subject, SentEmailEntity::subject)
        field(body, SentEmailEntity::body)
        field(contentHash, SentEmailEntity::contentHash)
        field(contentSizeBytes, SentEmailEntity::contentSizeBytes)
        field(submittedAtMillis, SentEmailEntity::submittedAtMillis)
        field(expiresAtMillis, SentEmailEntity::expiresAtMillis)
        field(spamScore, SentEmailEntity::spamScore)
        field(spamDetected, SentEmailEntity::spamDetected)
        field(ipAddress, SentEmailEntity::ipAddress)
        field(tags, SentEmailEntity::tags)
        field(metadata, SentEmailEntity::metadata)

        construct {
            SentEmailEntity(
                id = this[id],
                messageId = this[messageId],
                userId = this[userId],
                senderEmail = this[senderEmail],
                recipientEmail = this[recipientEmail],
                subject = this[subject],
                body = this[body],
                contentHash = this[contentHash],
                contentSizeBytes = this[contentSizeBytes],
                submittedAtMillis = this[submittedAtMillis],
                expiresAtMillis = this[expiresAtMillis],
                spamScore = this[spamScore],
                spamDetected = this[spamDetected],
                ipAddress = this[ipAddress],
                tags = this[tags],
                metadata = this[metadata]
            )
        }
    }
}
