package io.github.darkryh.katalyst.example.infra.database.tables

import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.example.infra.database.entities.AuthAccountEntity
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Exposed definition + entity mappers for the `auth_accounts` table.
 *
 * Maps between the persistence layer (ResultRow) and domain layer (AuthAccountEntity).
 * The explicit mapping functions ensure type safety and performance by avoiding reflection.
 *
 * **Mapping Contract:**
 * - [mapping] transforms database rows into fully-populated AuthAccountEntity values
 * - [mapping] populates INSERT/UPDATE statements from AuthAccountEntity values
 */
object AuthAccountsTable : LongIdTable("katalyst_example_service.auth_accounts"), Table<Long, AuthAccountEntity> {
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAtMillis = long("created_at_millis")
    val lastLoginAtMillis = long("last_login_at_millis").nullable()
    val status = varchar("status", 32).default("active")

    override val mapping = mapping<Long, AuthAccountEntity> {
        generatedId(id, AuthAccountEntity::id)
        field(email, AuthAccountEntity::email)
        field(passwordHash, AuthAccountEntity::passwordHash)
        field(createdAtMillis, AuthAccountEntity::createdAtMillis)
        field(lastLoginAtMillis, AuthAccountEntity::lastLoginAtMillis)
        field(status, AuthAccountEntity::status)

        construct {
            AuthAccountEntity(
                id = this[id],
                email = this[email],
                passwordHash = this[passwordHash],
                createdAtMillis = this[createdAtMillis],
                lastLoginAtMillis = this[lastLoginAtMillis],
                status = this[status]
            )
        }
    }
}
