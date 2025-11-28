package com.ead.boshi_client.ui.util

import java.time.Instant
import java.time.temporal.ChronoUnit

data class Account(
    val id: Int,
    val email: String,
    val name: String
)

data class Attachment(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val content: ByteArray? = null
)

data class Email(
    val id: String,
    val sender: String,
    val recipient: String,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val isHtml: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: java.time.Instant,
    val isRead: Boolean = false
)

data class EmailStats(
    val totalReceived: Long,
    val pending: Long,
    val delivered: Long,
    val failed: Long,
    val permanentlyFailed: Long,
    val uniqueSenders: Long,
    val uniqueRecipients: Long
)

object Stubs {
    val accounts = listOf(
        Account(1, "admin@boshi.com", "Admin"),
    )

    val emails = listOf(
        Email(
            id = "1",
            sender = "user1@example.com",
            recipient = "admin@boshi.com",
            cc = listOf("manager@example.com"),
            subject = "Hello",
            body = "<h2>Welcome!</h2><p>This is a <strong>test email</strong> with HTML content.</p>",
            isHtml = true,
            attachments = listOf(
                Attachment("a1", "document.pdf", 245760, "application/pdf")
            ),
            timestamp = Instant.now().minus(1, ChronoUnit.HOURS),
            isRead = true
        ),
        Email(
            id = "2",
            sender = "user2@example.com",
            recipient = "admin@boshi.com",
            cc = emptyList(),
            subject = "Support Request",
            body = "I need help with my account. Can you please assist?",
            isHtml = false,
            attachments = emptyList(),
            timestamp = Instant.now().minus(2, ChronoUnit.HOURS),
            isRead = false
        ),
        Email(
            id = "3",
            sender = "admin@boshi.com",
            recipient = "user3@example.com",
            cc = listOf("support@boshi.com"),
            bcc = listOf("log@boshi.com"),
            subject = "Welcome to Boshi",
            body = "<div style='font-family: Arial;'><h1>Welcome!</h1><p>Thank you for joining <em>Boshi</em>.</p><ul><li>Feature 1</li><li>Feature 2</li></ul></div>",
            isHtml = true,
            attachments = listOf(
                Attachment("a2", "welcome.pdf", 512000, "application/pdf"),
                Attachment("a3", "guide.docx", 128000, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            ),
            timestamp = Instant.now().minus(5, ChronoUnit.HOURS),
            isRead = true
        ),
        Email(
            id = "4",
            sender = "newsletter@company.com",
            recipient = "admin@boshi.com",
            subject = "Monthly Newsletter - December 2025",
            body = "<html><body style='background: #f0f0f0; padding: 20px;'><div style='max-width: 600px; margin: 0 auto; background: white; padding: 20px;'><h1 style='color: #333;'>Monthly Update</h1><p>Here are the latest updates from our team...</p><img src='https://via.placeholder.com/600x200' style='width: 100%;' /></div></body></html>",
            isHtml = true,
            attachments = emptyList(),
            timestamp = Instant.now().minus(1, ChronoUnit.DAYS),
            isRead = false
        ),
        Email(
            id = "5",
            sender = "noreply@service.com",
            recipient = "admin@boshi.com",
            subject = "Password Reset Request",
            body = "Someone requested a password reset for your account. If this wasn't you, please ignore this email.",
            isHtml = false,
            attachments = emptyList(),
            timestamp = Instant.now().minus(3, ChronoUnit.DAYS),
            isRead = true
        )
    )

    val stats = EmailStats(
        totalReceived = 1250,
        pending = 45,
        delivered = 1180,
        failed = 20,
        permanentlyFailed = 5,
        uniqueSenders = 300,
        uniqueRecipients = 800
    )
}
