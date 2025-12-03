package io.github.darkryh.katalyst.example.infra.database.repositories

import io.github.darkryh.katalyst.example.infra.database.entities.AuthAccountEntity
import io.github.darkryh.katalyst.example.infra.database.tables.AuthAccountsTable
import io.github.darkryh.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

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
