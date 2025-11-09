package com.ead.katalyst.example.infra.database.repositories

import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.tables.AuthAccountsTable
import com.ead.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

/**
 * Repository for AuthAccount entities.
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
 *     authAccountRepository.save(account)
 *     authAccountRepository.findByEmail("test@example.com")
 * }
 * ```
 */
class AuthAccountRepository : CrudRepository<Long, AuthAccountEntity> {

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
