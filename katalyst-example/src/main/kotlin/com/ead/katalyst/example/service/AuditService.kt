package com.ead.katalyst.example.service

import com.ead.katalyst.services.Service
import io.ktor.util.logging.KtorSimpleLogger
import java.time.LocalDateTime

/**
 * Audit Service
 *
 * Automatically discovered and injected by Katalyst framework.
 * Demonstrates service implementation with transaction management.
 *
 * **Automatic Features:**
 * - Registered as a Service - lifecycle managed by framework
 * - transactionManager automatically injected via Service interface
 * - Available for injection in other services or route handlers
 * - No manual instantiation needed
 *
 * **Usage:**
 * In other services (automatic dependency):
 * ```kotlin
 * class UserService(
 *     private val auditService: AuditService,
 *     // ... other dependencies
 * ) : Service {
 *     suspend fun createUser(request: CreateUserRequest): User =
 *         transactionManager.transaction {
 *             val user = userRepository.save(entity)
 *             auditService.logAction("USER_CREATED", userId = user.id.toString())
 *             return user
 *         }
 * }
 * ```
 *
 * In route handlers:
 * ```kotlin
 * post {
 *     val auditService = call.inject<AuditService>()
 *     auditService.logAction("API_CALL", action = "POST /api/users")
 * }
 * ```
 *
 * **Pattern:**
 * This service demonstrates:
 * - Service-to-service dependencies
 * - Transaction management via transactionManager
 * - Business logic implementation
 * - Audit/logging functionality
 */
class AuditService : Service {
    private val logger = KtorSimpleLogger("AuditService")

    /**
     * Logs an audit action
     *
     * This would typically be written to a database table in production.
     * Demonstrates use of transactionManager for atomic operations.
     *
     * @param actionType Type of action (e.g., "USER_CREATED", "LOGIN", "DELETE")
     * @param userId Optional user ID associated with action
     * @param action Description of the action
     * @param details Additional metadata
     */
    suspend fun logAction(
        actionType: String,
        userId: String? = null,
        action: String = "",
        details: Map<String, String> = emptyMap()
    ) {
        logger.info("Audit Log: [$actionType] User: $userId | Action: $action | Timestamp: ${LocalDateTime.now()}")

        // In production, this would be saved to an audit table:
        // transactionManager.transaction {
        //     auditRepository.save(
        //         AuditLogEntity(
        //             actionType = actionType,
        //             userId = userId,
        //             action = action,
        //             details = details.toString(),
        //             timestamp = LocalDateTime.now()
        //         )
        //     )
        // }

        details.forEach { (key, value) ->
            logger.info("  - $key: $value")
        }
    }

    /**
     * Logs a user creation audit
     *
     * @param userId ID of created user
     * @param email Email of created user
     */
    suspend fun logUserCreated(userId: String, email: String) {
        logAction(
            actionType = "USER_CREATED",
            userId = userId,
            action = "New user created",
            details = mapOf("email" to email)
        )
    }

    /**
     * Logs a user update audit
     *
     * @param userId ID of updated user
     * @param changes Map of changed fields
     */
    suspend fun logUserUpdated(userId: String, changes: Map<String, String>) {
        logAction(
            actionType = "USER_UPDATED",
            userId = userId,
            action = "User profile updated",
            details = changes
        )
    }

    /**
     * Logs a user deletion audit
     *
     * @param userId ID of deleted user
     * @param reason Reason for deletion
     */
    suspend fun logUserDeleted(userId: String, reason: String = "Admin deletion") {
        logAction(
            actionType = "USER_DELETED",
            userId = userId,
            action = "User account deleted",
            details = mapOf("reason" to reason)
        )
    }

    /**
     * Logs a login event
     *
     * @param userId ID of user logging in
     * @param ipAddress IP address of login
     */
    suspend fun logLogin(userId: String, ipAddress: String) {
        logAction(
            actionType = "LOGIN",
            userId = userId,
            action = "User logged in",
            details = mapOf("ipAddress" to ipAddress)
        )
    }

    /**
     * Logs an API call
     *
     * @param method HTTP method
     * @param path API path
     * @param statusCode Response status code
     * @param duration Execution time in milliseconds
     */
    suspend fun logApiCall(method: String, path: String, statusCode: Int, duration: Long) {
        logAction(
            actionType = "API_CALL",
            action = "$method $path",
            details = mapOf(
                "statusCode" to statusCode.toString(),
                "duration" to "${duration}ms"
            )
        )
    }
}
