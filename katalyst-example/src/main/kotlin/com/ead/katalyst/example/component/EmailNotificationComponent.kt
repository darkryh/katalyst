package com.ead.katalyst.example.component

import com.ead.katalyst.components.Component
import io.ktor.util.logging.KtorSimpleLogger

/**
 * Email Notification Component
 *
 * Automatically discovered and injected by Katalyst framework.
 * Demonstrates the Component pattern - reusable utility functionality.
 *
 * **Automatic Features:**
 * - Auto-discovered as a Component implementation
 * - Automatically injected into services that depend on it
 * - No manual instantiation needed
 * - Singleton lifecycle by default
 *
 * **Usage:**
 * Any service can depend on this component:
 * ```kotlin
 * class NotificationService(
 *     private val emailComponent: EmailNotificationComponent
 * ) : Service {
 *     suspend fun notifyUser(email: String) {
 *         emailComponent.send(email, "Welcome!", "...")
 *     }
 * }
 * ```
 *
 * **Pattern:**
 * Components are for cross-cutting concerns:
 * - Email sending
 * - SMS notifications
 * - Cache management
 * - Configuration providers
 * - Utility services
 */
class EmailNotificationComponent : Component {
    private val logger = KtorSimpleLogger("EmailNotificationComponent")

    /**
     * Sends an email notification
     *
     * @param recipient Email address of the recipient
     * @param subject Subject line
     * @param body HTML/text body of the email
     */
    suspend fun send(recipient: String, subject: String, body: String) {
        logger.info("Sending email to: $recipient")
        logger.info("Subject: $subject")
        // In a real application, this would integrate with SMTP server
        // For demo purposes, we just log the action
        logger.info("Email sent successfully to $recipient")
    }

    /**
     * Sends a batch of emails
     *
     * @param recipients List of email addresses
     * @param subject Subject line
     * @param body Email body
     */
    suspend fun sendBatch(recipients: List<String>, subject: String, body: String) {
        logger.info("Sending batch emails to ${recipients.size} recipients")
        recipients.forEach { recipient ->
            send(recipient, subject, body)
        }
        logger.info("Batch email sending completed")
    }

    /**
     * Validates email format
     */
    fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@(.+)$")
        return emailRegex.matches(email)
    }
}
