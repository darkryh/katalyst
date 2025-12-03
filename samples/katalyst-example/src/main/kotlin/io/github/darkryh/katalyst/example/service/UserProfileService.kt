package io.github.darkryh.katalyst.example.service

import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.example.domain.UserProfile
import io.github.darkryh.katalyst.example.domain.exception.UserExampleValidationException
import io.github.darkryh.katalyst.example.infra.database.entities.UserProfileEntity
import io.github.darkryh.katalyst.example.infra.database.repositories.UserProfileRepository
import io.github.darkryh.katalyst.example.infra.mappers.toDomain
import io.github.darkryh.katalyst.scheduler.config.ScheduleConfig
import io.github.darkryh.katalyst.scheduler.cron.CronExpression
import io.github.darkryh.katalyst.scheduler.extension.requireScheduler
import io.ktor.util.logging.*

class UserProfileService(
    private val repository: UserProfileRepository
) : Service {
    private val scheduler = requireScheduler()
    private val logger = KtorSimpleLogger("UserProfileService")

    suspend fun createProfileForAccount(
        accountId: Long,
        displayName: String
    ): UserProfile = transactionManager.transaction {
        val existing = repository.findByAccountId(accountId)
        existing?.toDomain()
            ?: repository.save(
                UserProfileEntity(
                    accountId = accountId,
                    displayName = displayName
                )
            ).toDomain()
    }

    suspend fun getProfile(id: Long): UserProfile = transactionManager.transaction {
        repository.findById(id)?.toDomain()
            ?: throw UserExampleValidationException("Profile with id=$id not found")
    }

    suspend fun listProfiles(): List<UserProfile> = transactionManager.transaction {
        repository.findAll().map { it.toDomain() }
    }

    suspend fun getProfileForAccount(accountId: Long): UserProfile? = transactionManager.transaction {
        repository.findByAccountId(accountId)?.toDomain()
    }

    @Suppress("unused")
    fun scheduleProfileDigest() = scheduler.scheduleCron(
        config = ScheduleConfig(
            taskName = "profiles.broadcast",
            tags = setOf("demo")
        ),
        task = { broadcastProfiles() },
        cronExpression = CronExpression("0 0/1 * * * ?")
    )

    private suspend fun broadcastProfiles() = transactionManager.transaction {
        val profiles = repository.findAll().map { it.toDomain() }
        logger.info("Broadcasting ${profiles.size} user profiles")
        profiles.forEach { profile ->
            logger.info(" - ${profile.displayName} (account=${profile.accountId})")
        }
    }
}
