package com.ead.katalyst.example.service

import com.ead.katalyst.example.api.AuthResponse
import com.ead.katalyst.example.api.LoginRequest
import com.ead.katalyst.example.api.RegisterRequest
import com.ead.katalyst.example.component.PasswordHasher
import com.ead.katalyst.example.domain.AuthAccount
import com.ead.katalyst.example.domain.AuthValidator
import com.ead.katalyst.example.domain.events.UserRegisteredEvent
import com.ead.katalyst.example.domain.exception.UserExampleValidationException
import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.repositories.AuthAccountRepository
import com.ead.katalyst.example.infra.mappers.toDomain
import com.ead.katalyst.example.security.JwtSettings
import com.ead.katalyst.events.bus.EventBus
import com.ead.katalyst.services.Service
import com.ead.katalyst.services.cron.CronExpression
import com.ead.katalyst.services.service.ScheduleConfig
import com.ead.katalyst.services.service.requireScheduler
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.time.Duration.Companion.minutes

class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val validator: AuthValidator,
    private val passwordHasher: PasswordHasher,
    private val eventBus: EventBus
) : Service {

    private val scheduler = requireScheduler()
    private val logger = KtorSimpleLogger("AuthenticationService")

    init {
        scheduleAuthDigest()
    }

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


        println("publishing new register")

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

    private fun scheduleAuthDigest() {
        scheduler.scheduleCron(
            config = ScheduleConfig(
                taskName = "profiles.broadcast",
                tags = setOf("demo"),
                maxExecutionTime = 1.minutes
            ),
            task = { broadcastAuth() },
            cronExpression = CronExpression("0 0/1 * * * ?")
        )
    }

    private suspend fun broadcastAuth() = transactionManager.transaction {
        val profiles = repository.findAll().map { it.toDomain() }
        logger.info("Broadcasting ${profiles.size} user profiles")
        profiles.forEach { profile ->
            logger.info(" - ${profile.email} (account=${profile.id})")
        }
    }
}
