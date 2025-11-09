package com.ead.katalyst.example.service

import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.example.api.dto.AuthResponse
import com.ead.katalyst.example.api.dto.LoginRequest
import com.ead.katalyst.example.api.dto.RegisterRequest
import com.ead.katalyst.example.util.PasswordHasher
import com.ead.katalyst.example.domain.AuthAccount
import com.ead.katalyst.example.domain.AuthValidator
import com.ead.katalyst.example.domain.events.UserRegisteredEvent
import com.ead.katalyst.example.domain.exception.UserExampleValidationException
import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.repositories.AuthAccountRepository
import com.ead.katalyst.example.infra.mappers.toDomain
import com.ead.katalyst.example.config.security.JwtSettings
import com.ead.katalyst.core.component.Service
import com.ead.katalyst.scheduler.cron.CronExpression
import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.extension.requireScheduler
import io.ktor.util.logging.*
import kotlin.time.Duration.Companion.minutes

class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val validator: AuthValidator,
    private val passwordHasher: PasswordHasher,
    private val eventBus: EventBus
) : Service {

    private val scheduler = requireScheduler()
    private val logger = KtorSimpleLogger("AuthenticationService")

    suspend fun register(request: RegisterRequest): AuthResponse = transactionManager.transaction {
        validator.validate(request)
        if (repository.findByEmail(request.email) != null) {
            throw UserExampleValidationException("Email '${request.email}' is already registered")
        }

        val account = repository.save(
            AuthAccountEntity(
                email = request.email.lowercase(),
                passwordHash = passwordHasher.hash(request.password),
                createdAtMillis = System.currentTimeMillis()
            )
        ).toDomain()

        eventBus.publish(
            UserRegisteredEvent(
                accountId = account.id,
                email = account.email,
                displayName = request.displayName
            )
        )

        issueToken(account)
    }

    suspend fun login(request: LoginRequest): AuthResponse = transactionManager.transaction {
        validator.validate(request)

        val account = repository.findByEmail(request.email.lowercase())
            ?.toDomain()
            ?: throw UserExampleValidationException("Invalid email/password combination")

        if (!passwordHasher.verify(request.password, account.passwordHash)) {
            throw UserExampleValidationException("Invalid email/password combination")
        }

        issueToken(account)
    }

    private fun issueToken(account: AuthAccount): AuthResponse =
        AuthResponse(
            accountId = account.id,
            email = account.email,
            token = JwtSettings.generateToken(account.id, account.email)
        )

    @Suppress("unused")
    fun scheduleAuthDigest() = scheduler.scheduleCron(
        config = ScheduleConfig(
            taskName = "authentication.broadcast",
            tags = setOf("demo"),
            maxExecutionTime = 1.minutes
        ),
        task = { broadcastAuth() },
        cronExpression = CronExpression("0 0/1 * * * ?")
    )

    private suspend fun broadcastAuth() = transactionManager.transaction {
        val profiles = repository.findAll().map { it.toDomain() }
        logger.info("Broadcasting ${profiles.size} user profiles")
        profiles.forEach { profile ->
            logger.info(" - ${profile.email} (account=${profile.id})")
        }
    }
}
