package com.ead.katalyst.example.service

import com.ead.katalyst.core.component.Service
import com.ead.katalyst.example.domain.UserProfile
import com.ead.katalyst.example.domain.exception.UserExampleValidationException
import com.ead.katalyst.example.infra.database.entities.UserProfileEntity
import com.ead.katalyst.example.infra.database.repositories.UserProfileRepository
import com.ead.katalyst.example.infra.mappers.toDomain
import com.ead.katalyst.scheduler.config.ScheduleConfig
import com.ead.katalyst.scheduler.cron.CronExpression
import com.ead.katalyst.scheduler.extension.requireScheduler
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
        throw Exception("error")
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

    suspend fun getProfileForAccount(accountId: Long): UserProfile? =
        transactionManager.transaction {
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
