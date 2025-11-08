package com.ead.katalyst.example.infra.database.repositories

import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.tables.AuthAccountsTable
import com.ead.katalyst.repositories.TrackedRepository
import com.ead.katalyst.transactions.workflow.OperationLog
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

/**
 * Repository for AuthAccount entities with automatic operation tracking.
 *
 * All CRUD operations are automatically logged to the operation log via the TrackedRepository base class.
 * This enables automatic rollback on transaction failure.
 *
 * **Usage in Transactions**:
 * ```kotlin
 * transactionManager.transaction(workflowId) {
 *     authAccountRepository.save(account)  // Automatically tracked
 *     authAccountRepository.findByEmail("test@example.com")  // Not tracked (read-only)
 * }
 * ```
 */
class AuthAccountRepository(
    operationLog: OperationLog
) : TrackedRepository<Long, AuthAccountEntity>(operationLog) {

    override val table: LongIdTable = AuthAccountsTable

    override fun map(row: ResultRow): AuthAccountEntity = AuthAccountEntity(
        id = row[AuthAccountsTable.id].value,
        email = row[AuthAccountsTable.email],
        passwordHash = row[AuthAccountsTable.passwordHash],
        createdAtMillis = row[AuthAccountsTable.createdAtMillis],
        lastLoginAtMillis = row[AuthAccountsTable.lastLoginAtMillis]
    )

    /**
     * Find an auth account by email address.
     * This is a read-only operation, so it's not tracked.
     *
     * @param email The email to search for
     * @return The auth account if found, null otherwise
     */
    fun findByEmail(email: String): AuthAccountEntity? =
        AuthAccountsTable
            .selectAll().where { AuthAccountsTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?.let(::map)
}
