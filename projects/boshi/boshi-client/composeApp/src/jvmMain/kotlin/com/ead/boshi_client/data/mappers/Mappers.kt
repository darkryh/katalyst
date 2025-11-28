package com.ead.boshi_client.data.mappers

import com.ead.boshi_client.data.network.models.EmailDto
import com.ead.boshi_client.data.network.models.EmailStatusResponse
import com.ead.boshi_client.data.network.models.SendEmailResponse
import com.ead.boshi_client.data.db.entities.EmailEntity
import com.ead.boshi_client.domain.models.Email
import com.ead.boshi_client.domain.models.EmailStatus
import com.ead.boshi_client.domain.models.EmailType
import com.ead.boshi_client.ui.models.UiEmail
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== NETWORK DTOs -> DOMAIN ====================

/**
 * Maps EmailDto from backend to domain Email model.
 * Used when fetching list of emails from /emails endpoint.
 */
fun EmailDto.toEmail(type: EmailType = EmailType.SENT): Email {
    return Email(
        messageId = messageId,
        sender = sender,
        recipient = recipient,
        subject = subject,
        body = body,
        status = status.toEmailStatus(),
        type = type,
        timestamp = timestamp,
        tags = tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    )
}

/**
 * Maps SendEmailResponse from backend to domain Email model.
 * Used when initially sending an email.
 */
fun SendEmailResponse.toEmail(to: String, from: String = "boshi@ead.company"): Email {
    return Email(
        messageId = messageId,
        sender = from,
        recipient = to,
        subject = "",  // Not available in response
        body = "",     // Not available in response
        status = status.toEmailStatus(),
        type = EmailType.SENT,
        timestamp = System.currentTimeMillis()
    )
}

/**
 * Maps EmailStatusResponse from backend to domain Email status.
 * Used when checking delivery status of a sent email.
 */
fun EmailStatusResponse.toEmailStatus(): EmailStatus {
    return when (status) {
        "DELIVERED" -> EmailStatus.DELIVERED
        "PENDING" -> EmailStatus.PENDING
        "FAILED" -> EmailStatus.FAILED
        "PERMANENTLY_FAILED" -> EmailStatus.PERMANENTLY_FAILED
        else -> EmailStatus.UNKNOWN
    }
}

// ==================== DOMAIN -> ENTITY ====================

/**
 * Maps domain Email to Room EmailEntity for database storage.
 */
fun Email.toEntity(): EmailEntity {
    return EmailEntity(
        messageId = messageId,
        sender = sender,
        recipient = recipient,
        subject = subject,
        body = body,
        status = status.toString(),
        type = type.toString(),
        timestamp = timestamp,
        expiresAtMillis = expiresAtMillis,
        spamScore = spamScore,
        spamDetected = spamDetected,
        contentHash = contentHash,
        contentSizeBytes = contentSizeBytes,
        tags = tags.joinToString(",").takeIf { it.isNotEmpty() },
        metadata = metadata?.let {
            try {
                Json.encodeToString(it)
            } catch (e: Exception) {
                null
            }
        }
    )
}

// ==================== ENTITY -> DOMAIN ====================

/**
 * Maps Room EmailEntity to domain Email model.
 */
fun EmailEntity.toDomain(): Email {
    return Email(
        id = id,
        messageId = messageId,
        sender = sender,
        recipient = recipient,
        subject = subject,
        body = body,
        status = status.toEmailStatus(),
        type = try { EmailType.valueOf(type) } catch (e: Exception) { EmailType.SENT },
        timestamp = timestamp,
        expiresAtMillis = expiresAtMillis,
        spamScore = spamScore,
        spamDetected = spamDetected,
        contentHash = contentHash,
        contentSizeBytes = contentSizeBytes,
        tags = tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        metadata = metadata?.let {
            try {
                Json.decodeFromString<Map<String, String>>(it)
            } catch (e: Exception) {
                null
            }
        }
    )
}

// ==================== DOMAIN -> UI ====================

/**
 * Maps domain Email to UiEmail for display in Compose UI.
 * Handles HTML stripping, text truncation, and formatting.
 */
fun Email.toUi(): UiEmail {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val cleanPreview = body.stripHtml().replace("\n", " ").take(80)
    val displaySubject = subject.takeIf { it.isNotEmpty() } ?: "(no subject)"

    return UiEmail(
        id = id,
        sender = sender,
        subject = displaySubject,
        preview = cleanPreview,
        status = status,
        type = type,
        date = dateFormat.format(Date(timestamp)),
        initial = sender.substringBefore("@").firstOrNull()?.uppercase() ?: "?"
    )
}

// ==================== HELPER FUNCTIONS ====================

/**
 * Converts string status from backend to EmailStatus enum.
 */
fun String.toEmailStatus(): EmailStatus {
    return try {
        EmailStatus.valueOf(this.uppercase())
    } catch (e: Exception) {
        EmailStatus.UNKNOWN
    }
}

/**
 * Strips HTML tags from a string.
 * Used for email body preview and display.
 */
fun String.stripHtml(): String {
    return try {
        Jsoup.parse(this).text()
    } catch (e: Exception) {
        this  // Return original if parsing fails
    }
}

/**
 * Serializes metadata Map to JSON string for database storage.
 */
fun Map<String, String>?.toMetadataJson(): String? {
    return this?.let {
        try {
            Json.encodeToString(it)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Deserializes JSON string to metadata Map.
 */
fun String?.parseMetadataJson(): Map<String, String>? {
    return this?.let {
        try {
            Json.decodeFromString<Map<String, String>>(it)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Converts comma-separated tags string to List.
 */
fun String?.parseTags(): List<String> {
    return this?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
}

/**
 * Converts List of tags to comma-separated string for storage.
 */
fun List<String>?.toTagsString(): String? {
    return this?.joinToString(",")?.takeIf { it.isNotEmpty() }
}
