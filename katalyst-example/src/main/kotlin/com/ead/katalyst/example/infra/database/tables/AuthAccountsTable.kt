package com.ead.katalyst.example.infra.database.tables

import com.ead.katalyst.core.persistence.Table
import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.tables.AuthAccountsTable.assignEntity
import com.ead.katalyst.example.infra.database.tables.AuthAccountsTable.mapRow
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/**
 * Exposed definition + entity mappers for the `auth_accounts` table.
 *
 * Maps between the persistence layer (ResultRow) and domain layer (AuthAccountEntity).
 * The explicit mapping functions ensure type safety and performance by avoiding reflection.
 *
 * **Mapping Contract:**
 * - [mapRow] transforms a database row into a fully-populated AuthAccountEntity
 * - [assignEntity] populates an INSERT/UPDATE statement from an AuthAccountEntity
 */
object AuthAccountsTable : LongIdTable("auth_accounts"), Table<Long, AuthAccountEntity> {
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAtMillis = long("created_at_millis")
    val lastLoginAtMillis = long("last_login_at_millis").nullable()

    override fun mapRow(row: ResultRow): AuthAccountEntity = AuthAccountEntity(
        id = row[id].value,
        email = row[email],
        passwordHash = row[passwordHash],
        createdAtMillis = row[createdAtMillis],
        lastLoginAtMillis = row[lastLoginAtMillis]
    )

    override fun assignEntity(
        statement: UpdateBuilder<*>,
        entity: AuthAccountEntity,
        skipIdColumn: Boolean
    ) {
        if (!skipIdColumn && entity.id != null) {
            statement[id] = EntityID(entity.id, this)
        }
        statement[email] = entity.email
        statement[passwordHash] = entity.passwordHash
        statement[createdAtMillis] = entity.createdAtMillis
        statement[lastLoginAtMillis] = entity.lastLoginAtMillis
    }
}
