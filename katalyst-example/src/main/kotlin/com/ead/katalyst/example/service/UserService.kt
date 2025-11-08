package com.ead.katalyst.example.service

import com.ead.katalyst.example.api.CreateUserRequest
import com.ead.katalyst.example.domain.User
import com.ead.katalyst.example.domain.UserValidator
import com.ead.katalyst.example.domain.events.UserAuditEvent
import com.ead.katalyst.example.domain.events.UserNotificationEvent
import com.ead.katalyst.example.domain.exception.TestException
import com.ead.katalyst.example.domain.exception.UserExampleValidationException
import com.ead.katalyst.example.infra.database.entities.UserEntity
import com.ead.katalyst.example.infra.database.repositories.UserRepository
import com.ead.katalyst.example.infra.mappers.toUser
import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.services.Service
import com.ead.katalyst.services.cron.CronExpression
import com.ead.katalyst.services.service.ScheduleConfig
import com.ead.katalyst.services.service.requireScheduler
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.time.Duration.Companion.minutes

/**
 * User Service
 *
 * Automatically discovered and injected by Katalyst framework.
 * Demonstrates comprehensive service-to-service dependency injection.
 *
 * **Automatic Dependencies (Auto-Injected):**
 * - userRepository: UserRepository - repository for data access
 * - userValidator: UserValidator - domain validator
 * - auditService: AuditService - service for audit logging
 * - eventBus: EventBus - event dispatcher for downstream processing
 *
 * **Built-in Service Features:**
 * - transactionManager: DatabaseTransactionManager - provided by Service interface
 * - scheduler: SchedulerService - accessed via requireScheduler()
 *
 * **Usage in Route Handlers:**
 * ```kotlin
 * post("/users") {
 *     val service = call.inject<UserService>()
 *     val user = service.createUser(request)
 *     call.respond(user)
 * }
 * ```
 *
 * **Pattern Benefits:**
 * - All dependencies automatically wired by framework
 * - Clean separation of concerns
 * - Easy to test (inject mock dependencies)
 * - Transaction management handled automatically
 * - Scheduler integration for background tasks
 */
class UserService(
    private val userRepository: UserRepository,
    private val userValidator: UserValidator,
    private val auditService: AuditService,
    private val eventBus: EventBus
) : Service {
    private val logger = KtorSimpleLogger("UserService")
    private val scheduler = requireScheduler()

    init {
        scheduleRemoveInactiveUsers()
        failingRemoveInactiveUsers()
    }

    suspend fun createUser(request: CreateUserRequest): User = transactionManager.transaction {
        userValidator.validate(request)
        if (userRepository.findByEmail(request.email) != null) {
            throw UserExampleValidationException("Email '${request.email}' is already registered")
        }

        val user = userRepository.save(
            UserEntity(
                name = request.name.trim(),
                email = request.email.lowercase()
            )
        ).toUser()

        logger.info("User created: ${user.id} - ${user.email}")


        eventBus.publish(UserAuditEvent.Created(userId = user.id, email = user.email))
        eventBus.publish(UserNotificationEvent(email = user.email, name = user.name))

        return@transaction user
    }

    suspend fun getUser(id: Long): User = transactionManager.transaction {
        val user = userRepository.findById(id)?.toUser()
            ?: throw UserExampleValidationException("User with id=$id not found")
        logger.info("User retrieved: $id")
        eventBus.publish(UserAuditEvent.GetData(userId = user.id, email = user.email))
        auditService.logApiCall("GET", "/api/users/$id", 200, 0)

        return@transaction user
    }

    suspend fun listUsers(): List<User> = transactionManager.transaction {
        val users = userRepository.findAll().map { it.toUser() }
        logger.info("Listed ${users.size} users")
        auditService.logApiCall("GET", "/api/users", 200, 0)
        return@transaction users
    }

    private suspend fun removeInactiveUsers() = transactionManager.transaction {
        println("""
            -----------------------------------------------------------------
            SCHEDULER REMOVE_INACTIVE_USERS STARTED
            -----------------------------------------------------------------
        """.trimIndent())
        userRepository.deleteInactive()
    }

    private fun scheduleRemoveInactiveUsers() {
        scheduler.scheduleCron(
            config = ScheduleConfig(
                taskName = "users.cleanup-inactive",
                tags = setOf("database", "maintenance"), // OPTIONAL
                maxExecutionTime = 5.minutes, //OPTIONAL
                onSuccess = { taskName, executionTime -> //OPTIONAL
                    println("Task '$taskName' completed successfully in $executionTime")
                    println("""
                    -----------------------------------------------------------------    
                    SCHEDULER REMOVE_INACTIVE_USERS FINISHED
                    -----------------------------------------------------------------
                    """.trimIndent()
                    )
                },
                onError = { taskName, exception, executionCount -> //OPTIONAL
                    println("Task '$taskName' failed on attempt #$executionCount: ${exception.message}")
                    println("""
                    -----------------------------------------------------------------    
                    SCHEDULER REMOVE_INACTIVE_USERS FINISHED
                    -----------------------------------------------------------------
                    """.trimIndent()
                    )
                    // Continue scheduling on errors
                    true
                }
            ),
            task = { removeInactiveUsers() },
            cronExpression = CronExpression("0/30 * * * * ?")
        )
    }

    private fun failingRemoveInactiveUsers() {
        scheduler.scheduleCron(
            config = ScheduleConfig(taskName = "users.failing-cleanup-inactive",),
            task = { throw TestException("test exception threw on purpose") },
            cronExpression = CronExpression("0/45 * * * * ?")
        )
    }
}
