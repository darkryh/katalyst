package com.ead.boshi.storage.tables

import com.ead.boshi.shared.models.DeliveryStatusEntity
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Exposed table definition for delivery status tracking
 * Maps to delivery_status table in database
 * Has foreign key to sent_emails with CASCADE delete
 */
object DeliveryStatusTable : LongIdTable("delivering_service.delivery_status"), Table<Long, DeliveryStatusEntity> {
    // Foreign key to sent_emails
    val sentEmailId = reference(
        name = "sent_email_id",
        foreign = SentEmailsTable,
        onDelete = ReferenceOption.CASCADE
    )

    // Message identifier (denormalized for query efficiency)
    val messageId = varchar("message_id", 255).uniqueIndex()

    // Status tracking
    val status = varchar("status", 32).index()
    val statusChangedAtMillis = long("status_changed_at_millis")
    val lastAttemptAtMillis = long("last_attempt_at_millis")

    // Retry logic
    val attemptCount = integer("attempt_count")
    val nextRetryAtMillis = long("next_retry_at_millis").nullable().index()

    // Error details
    val errorMessage = text("error_message").nullable()
    val deliveredAtMillis = long("delivered_at_millis").nullable()

    // Metadata
    val metadata = text("metadata").nullable()

    // Composite index for retry queries
    init {
        index(false, status, nextRetryAtMillis)
    }

    override val mapping = mapping<Long, DeliveryStatusEntity> {
        generatedId(id, DeliveryStatusEntity::id)
        reference(sentEmailId, DeliveryStatusEntity::sentEmailId)
        field(messageId, DeliveryStatusEntity::messageId)
        field(status, DeliveryStatusEntity::status)
        field(statusChangedAtMillis, DeliveryStatusEntity::statusChangedAtMillis)
        field(lastAttemptAtMillis, DeliveryStatusEntity::lastAttemptAtMillis)
        field(attemptCount, DeliveryStatusEntity::attemptCount)
        field(nextRetryAtMillis, DeliveryStatusEntity::nextRetryAtMillis)
        field(errorMessage, DeliveryStatusEntity::errorMessage)
        field(deliveredAtMillis, DeliveryStatusEntity::deliveredAtMillis)
        field(metadata, DeliveryStatusEntity::metadata)

        construct {
            DeliveryStatusEntity(
                id = this[id],
                sentEmailId = this[sentEmailId],
                messageId = this[messageId],
                status = this[status],
                statusChangedAtMillis = this[statusChangedAtMillis],
                lastAttemptAtMillis = this[lastAttemptAtMillis],
                attemptCount = this[attemptCount],
                nextRetryAtMillis = this[nextRetryAtMillis],
                errorMessage = this[errorMessage],
                deliveredAtMillis = this[deliveredAtMillis],
                metadata = this[metadata]
            )
        }
    }
}
