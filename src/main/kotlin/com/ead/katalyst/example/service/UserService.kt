package com.ead.katalyst.example.service

import com.ead.katalyst.example.api.CreateUserRequest
import com.ead.katalyst.example.domain.User
import com.ead.katalyst.example.domain.UserExampleValidationException
import com.ead.katalyst.example.domain.UserValidator
import com.ead.katalyst.example.infra.database.entities.UserEntity
import com.ead.katalyst.example.infra.database.repositories.UserRepository
import com.ead.katalyst.example.infra.mappers.toUser
import com.ead.katalyst.services.Service
import com.ead.katalyst.services.service.requireScheduler
import kotlin.time.Duration.Companion.minutes

class UserService(
    private val userRepository: UserRepository,
    private val userValidator: UserValidator
) : Service {

    private val scheduler = requireScheduler()

    init {
        scheduleRemoveInactiveUsers()
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
        userRepository.findAll().map { it.toUser() }
    }

    private suspend fun removeInactiveUsers() = transactionManager.transaction {
        userRepository.deleteInactive()
    }

    private fun scheduleRemoveInactiveUsers() {
        scheduler.scheduleFixedDelay(
            taskName = "users.cleanup-inactive",
            task = { removeInactiveUsers() },
            initialDelay = 5.minutes,
            fixedDelay = 60.minutes
        )
    }
}