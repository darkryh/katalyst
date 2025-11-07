package com.ead.katalyst.example.service

import com.ead.katalyst.example.api.CreateUserRequest
import com.ead.katalyst.example.domain.exception.TestException
import com.ead.katalyst.example.domain.User
import com.ead.katalyst.example.domain.exception.UserExampleValidationException
import com.ead.katalyst.example.domain.UserValidator
import com.ead.katalyst.example.infra.database.entities.UserEntity
import com.ead.katalyst.example.infra.database.repositories.UserRepository
import com.ead.katalyst.example.infra.mappers.toUser
import com.ead.katalyst.services.Service
import com.ead.katalyst.services.cron.CronExpression
import com.ead.katalyst.services.service.ScheduleConfig
import com.ead.katalyst.services.service.requireScheduler
import kotlin.time.Duration.Companion.minutes

class UserService(
    private val userRepository: UserRepository,
    private val userValidator: UserValidator
) : Service {

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

        userRepository.save(
            UserEntity(
                name = request.name.trim(),
                email = request.email.lowercase()
            )
        ).toUser()
    }

    suspend fun getUser(id: Long): User = transactionManager.transaction {
        userRepository.findById(id)?.toUser() ?: throw UserExampleValidationException("User with id=$id not found")
    }

    suspend fun listUsers(): List<User> = transactionManager.transaction {
        throw TestException("ee")
        userRepository.findAll().map { it.toUser() }
    }

    private suspend fun removeInactiveUsers() = transactionManager.transaction {
        println("remove Inactive Users")
        userRepository.deleteInactive()
    }

    private fun scheduleRemoveInactiveUsers() {
        scheduler.scheduleCron(
            config = ScheduleConfig(
                taskName = "users.cleanup-inactive",
                tags = setOf("database", "maintenance"), // optional
                maxExecutionTime = 5.minutes, //optional
                onSuccess = { taskName, executionTime -> //optional
                    println("Task '$taskName' completed successfully in $executionTime")
                },
                onError = { taskName, exception, executionCount -> //optional
                    println("Task '$taskName' failed on attempt #$executionCount: ${exception.message}")
                    // Continue scheduling on errors
                    true
                }
            ),
            task = { removeInactiveUsers() },
            cronExpression = CronExpression("0/2 * * * * ?")
        )
    }

    private fun failingRemoveInactiveUsers() {
        /*scheduler.scheduleCron(
            config = ScheduleConfig(
                taskName = "users.cleanup-inactive",
                tags = setOf("example"), // optional
                onSuccess = { taskName, executionTime -> //optional
                    println("Task '$taskName' completed successfully in $executionTime")
                },
                onError = { taskName, exception, executionCount -> //optional
                    println("Task '$taskName' failed on attempt #$executionCount: ${exception.message}")
                    // Continue scheduling on errors
                    true
                }
            ),
            task = { throw TestException("test") },
            cronExpression = CronExpression("0/15 * * * * ?")
        )*/
    }
}