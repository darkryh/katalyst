package com.ead.boshi.storage.tables

import com.ead.boshi.shared.models.DeliveryStatusEntity
import com.ead.katalyst.core.persistence.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

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

    override fun mapRow(row: ResultRow): DeliveryStatusEntity = DeliveryStatusEntity(
        id = row[id].value,
        sentEmailId = row[sentEmailId].value,
        messageId = row[messageId],
        status = row[status],
        statusChangedAtMillis = row[statusChangedAtMillis],
        lastAttemptAtMillis = row[lastAttemptAtMillis],
        attemptCount = row[attemptCount],
        nextRetryAtMillis = row[nextRetryAtMillis],
        errorMessage = row[errorMessage],
        deliveredAtMillis = row[deliveredAtMillis],
        metadata = row[metadata]
    )

    override fun assignEntity(
        statement: UpdateBuilder<*>,
        entity: DeliveryStatusEntity,
        skipIdColumn: Boolean
    ) {
        if (!skipIdColumn && entity.id != null) {
            statement[id] = EntityID(entity.id as Long, this)
        }
        statement[sentEmailId] = EntityID(entity.sentEmailId, SentEmailsTable)
        statement[messageId] = entity.messageId
        statement[status] = entity.status
        statement[statusChangedAtMillis] = entity.statusChangedAtMillis
        statement[lastAttemptAtMillis] = entity.lastAttemptAtMillis
        statement[attemptCount] = entity.attemptCount
        statement[nextRetryAtMillis] = entity.nextRetryAtMillis
        statement[errorMessage] = entity.errorMessage
        statement[deliveredAtMillis] = entity.deliveredAtMillis
        statement[metadata] = entity.metadata
    }
}
