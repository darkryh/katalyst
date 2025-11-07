package com.ead.katalyst.example.service

import com.ead.katalyst.example.component.EmailNotificationComponent
import com.ead.katalyst.services.Service
import io.ktor.util.logging.KtorSimpleLogger

/**
 * Notification Service
 *
 * Automatically discovered and injected by Katalyst framework.
 * Demonstrates service-to-component dependency injection pattern.
 *
 * **Automatic Features:**
 * - Depends on EmailNotificationComponent - automatically injected
 * - Registered as a Service - lifecycle managed by framework
 * - Available for injection in other services or route handlers
 * - No manual instantiation needed
 *
 * **Usage:**
 * In other services:
 * ```kotlin
 * class UserService(
 *     private val notificationService: NotificationService,
 *     // ... other dependencies
 * ) : Service
 * ```
 *
 * In route handlers:
 * ```kotlin
 * post {
 *     val service = call.inject<NotificationService>()
 *     service.notifyUserCreated("user@example.com")
 * }
 * ```
 *
 * **Pattern:**
 * Services typically:
 * - Depend on repositories for data access
 * - Depend on validators for input validation
 * - Depend on components for utility functionality
 * - Manage transactions via transactionManager
 * - Implement business logic
 */
class NotificationService(
    private val emailComponent: EmailNotificationComponent
) : Service {
    private val logger = KtorSimpleLogger("NotificationService")

    /**
     * Notifies a new user about account creation
     *
     * Demonstrates automatic dependency injection:
     * - EmailNotificationComponent is automatically injected
     * - transactionManager is automatically provided by Service interface
     *
     * @param email User's email address
     * @param name User's name
     */
    suspend fun notifyUserCreated(email: String, name: String) {
        logger.info("Notifying user '$name' at $email about account creation")

        val subject = "Welcome to Katalyst!"
        val body = """
            <h1>Welcome, $name!</h1>
            <p>Your account has been successfully created.</p>
            <p>You can now log in and start using the application.</p>
        """.trimIndent()

        emailComponent.send(email, subject, body)
        logger.info("User creation notification sent to $email")
    }

    /**
     * Notifies user about password change
     *
     * @param email User's email
     * @param name User's name
     */
    suspend fun notifyPasswordChange(email: String, name: String) {
        logger.info("Notifying $name about password change")

        val subject = "Password Changed"
        val body = """
            <h1>Password Changed</h1>
            <p>Hi $name,</p>
            <p>Your password was successfully changed.</p>
            <p>If you didn't make this change, please contact support immediately.</p>
        """.trimIndent()

        emailComponent.send(email, subject, body)
    }

    /**
     * Sends password reset email
     *
     * @param email User's email
     * @param name User's name
     * @param resetToken Secure reset token
     */
    suspend fun sendPasswordReset(email: String, name: String, resetToken: String) {
        logger.info("Sending password reset email to $email")

        val resetLink = "https://example.com/reset?token=$resetToken"
        val subject = "Reset Your Password"
        val body = """
            <h1>Reset Your Password</h1>
            <p>Hi $name,</p>
            <p>Click <a href="$resetLink">here</a> to reset your password.</p>
            <p>This link expires in 24 hours.</p>
        """.trimIndent()

        emailComponent.send(email, subject, body)
    }

    /**
     * Notifies about user deletion/account closure
     *
     * @param email User's email
     * @param name User's name
     */
    suspend fun notifyAccountClosure(email: String, name: String) {
        logger.info("Sending account closure notification to $email")

        val subject = "Account Closed"
        val body = """
            <h1>Account Closed</h1>
            <p>Hi $name,</p>
            <p>Your account has been successfully closed.</p>
            <p>If you change your mind, contact our support team within 30 days.</p>
        """.trimIndent()

        emailComponent.send(email, subject, body)
    }
}
