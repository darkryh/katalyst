package com.ead.boshi.storage.tables

import com.ead.boshi.shared.models.SentEmailEntity
import com.ead.katalyst.core.persistence.Table
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/**
 * Exposed table definition for sent emails
 * Maps to sent_emails table in database
 */
object SentEmailsTable : LongIdTable("sent_emails"), Table<Long, SentEmailEntity> {
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

    override fun mapRow(row: ResultRow): SentEmailEntity = SentEmailEntity(
        id = row[id].value,
        messageId = row[messageId],
        userId = row[userId],
        senderEmail = row[senderEmail],
        recipientEmail = row[recipientEmail],
        subject = row[subject],
        body = row[body],
        contentHash = row[contentHash],
        contentSizeBytes = row[contentSizeBytes],
        submittedAtMillis = row[submittedAtMillis],
        expiresAtMillis = row[expiresAtMillis],
        spamScore = row[spamScore],
        spamDetected = row[spamDetected],
        ipAddress = row[ipAddress],
        tags = row[tags],
        metadata = row[metadata]
    )

    override fun assignEntity(
        statement: UpdateBuilder<*>,
        entity: SentEmailEntity,
        skipIdColumn: Boolean
    ) {
        if (!skipIdColumn && entity.id != null) { statement[id] = EntityID(entity.id as Long, this) }
        statement[messageId] = entity.messageId
        statement[userId] = entity.userId
        statement[senderEmail] = entity.senderEmail
        statement[recipientEmail] = entity.recipientEmail
        statement[subject] = entity.subject
        statement[body] = entity.body
        statement[contentHash] = entity.contentHash
        statement[contentSizeBytes] = entity.contentSizeBytes
        statement[submittedAtMillis] = entity.submittedAtMillis
        statement[expiresAtMillis] = entity.expiresAtMillis
        statement[spamScore] = entity.spamScore
        statement[spamDetected] = entity.spamDetected
        statement[ipAddress] = entity.ipAddress
        statement[tags] = entity.tags
        statement[metadata] = entity.metadata
    }
}
