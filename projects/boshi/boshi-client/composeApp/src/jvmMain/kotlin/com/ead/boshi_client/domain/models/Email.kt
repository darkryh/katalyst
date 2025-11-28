package com.ead.boshi_client.domain.models

data class Email(
    val id: Long = 0,
    val messageId: String,
    val sender: String,
    val recipient: String,
    val subject: String,
    val body: String,
    val status: EmailStatus,
    val type: EmailType,
    val timestamp: Long,
    val expiresAtMillis: Long = 0,
    val spamScore: Double = 0.0,
    val spamDetected: Boolean = false,
    val contentHash: String? = null,
    val contentSizeBytes: Long = 0,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String>? = null
)

enum class EmailStatus {
    PENDING,              // Awaiting delivery (sent emails only)
    DELIVERED,            // Successfully sent
    FAILED,               // Temporary failure
    PERMANENTLY_FAILED,   // Give up on retries
    SENT,                 // Initial status for locally sent
    UNKNOWN               // Fallback
}

enum class EmailType {
    SENT,     // Submitted to backend for delivery
    RECEIVED  // Test/mock received emails (local only)
}
