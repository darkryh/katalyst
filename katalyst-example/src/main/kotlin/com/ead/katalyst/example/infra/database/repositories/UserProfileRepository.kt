package com.ead.katalyst.example.infra.database.repositories

import com.ead.katalyst.example.infra.database.entities.UserProfileEntity
import com.ead.katalyst.example.infra.database.tables.UserProfilesTable
import com.ead.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Repository for UserProfile entities.
 *
 * Transactions are handled at the service layer via transactionManager.
 * All service operations are executed within a transaction context that ensures:
 * - Database changes are committed together
 * - Events are published only after successful commit
 * - Rollback discards all changes and queued events
 *
 * **Usage in Transactions**:
 * ```kotlin
 * transactionManager.transaction(workflowId) {
 *     userProfileRepository.save(profile)
 *     userProfileRepository.findByAccountId(123)
 * }
 * ```
 */
class UserProfileRepository : CrudRepository<Long, UserProfileEntity> {

    override val table: LongIdTable = UserProfilesTable
    /**
     * Find a user profile by account ID.
     * This is a read-only operation, so it's not tracked.
     *
     * @param accountId The account ID to search for
     * @return The user profile if found, null otherwise
     */
    fun findByAccountId(accountId: Long): UserProfileEntity? =
        UserProfilesTable
            .selectAll().where { UserProfilesTable.accountId eq accountId }
            .limit(1)
            .firstOrNull()
            ?.let(::map)
}
