package com.ead.katalyst.example.infra.database.repositories

import com.ead.katalyst.example.infra.database.entities.UserProfileEntity
import com.ead.katalyst.example.infra.database.tables.UserProfilesTable
import com.ead.katalyst.repositories.TrackedRepository
import com.ead.katalyst.transactions.workflow.OperationLog
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

/**
 * Repository for UserProfile entities with automatic operation tracking.
 *
 * All CRUD operations are automatically logged to the operation log via the TrackedRepository base class.
 * This enables automatic rollback on transaction failure.
 *
 * **Usage in Transactions**:
 * ```kotlin
 * transactionManager.transaction(workflowId) {
 *     userProfileRepository.save(profile)  // Automatically tracked
 *     userProfileRepository.findByAccountId(123)  // Not tracked (read-only)
 * }
 * ```
 */
class UserProfileRepository(
    operationLog: OperationLog
) : TrackedRepository<Long, UserProfileEntity>(operationLog) {

    override val table: LongIdTable = UserProfilesTable

    override fun map(row: ResultRow): UserProfileEntity = UserProfileEntity(
        id = row[UserProfilesTable.id].value,
        accountId = row[UserProfilesTable.accountId].value,
        displayName = row[UserProfilesTable.displayName],
        bio = row[UserProfilesTable.bio],
        avatarUrl = row[UserProfilesTable.avatarUrl]
    )

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
