package com.ead.boshi_client.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "emails",
    indices = [
        Index("messageId", unique = true),
        Index("type"),
        Index("status"),
        Index("timestamp"),
        Index("expiresAtMillis"),
        Index(value = ["type", "timestamp"])  // for filtering sent/received
    ]
)
data class EmailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Email content
    val messageId: String,
    val sender: String,
    val recipient: String,
    val subject: String,
    val body: String,

    // Status tracking
    val status: String,  // PENDING|DELIVERED|FAILED|PERMANENTLY_FAILED
    val type: String,    // SENT|RECEIVED

    // Timestamps
    val timestamp: Long, // Submission time (millis)
    val expiresAtMillis: Long = 0,  // Auto-delete timestamp

    // Metadata
    val spamScore: Double = 0.0,
    val spamDetected: Boolean = false,
    val contentHash: String? = null,
    val contentSizeBytes: Long = 0,
    val tags: String? = null,           // comma-separated
    val metadata: String? = null        // JSON-serialized
)
