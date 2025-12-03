package io.github.darkryh.katalyst.example.service

import io.github.darkryh.katalyst.events.bus.EventBus
import io.github.darkryh.katalyst.example.api.dto.AuthResponse
import io.github.darkryh.katalyst.example.api.dto.LoginRequest
import io.github.darkryh.katalyst.example.api.dto.RegisterRequest
import io.github.darkryh.katalyst.example.util.PasswordHasher
import io.github.darkryh.katalyst.example.domain.AuthAccount
import io.github.darkryh.katalyst.example.domain.AuthValidator
import io.github.darkryh.katalyst.example.domain.events.UserRegisteredEvent
import io.github.darkryh.katalyst.example.domain.exception.UserExampleValidationException
import io.github.darkryh.katalyst.example.infra.database.entities.AuthAccountEntity
import io.github.darkryh.katalyst.example.infra.database.repositories.AuthAccountRepository
import io.github.darkryh.katalyst.example.infra.mappers.toDomain
import io.github.darkryh.katalyst.example.config.JwtSettingsService
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import io.ktor.util.logging.*
import kotlin.time.Duration.Companion.minutes

class AuthenticationService(
    private val repository: AuthAccountRepository,
    private val validator: AuthValidator,
    private val passwordHasher: PasswordHasher,
    private val eventBus: EventBus,
    private val jwtSettings: JwtSettingsService
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
                createdAtMillis = System.currentTimeMillis(),
                status = "active"
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
            token = jwtSettings.generateToken(account.id, account.email)
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
