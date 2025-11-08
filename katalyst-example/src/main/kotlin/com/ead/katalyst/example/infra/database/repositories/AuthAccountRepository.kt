package com.ead.katalyst.example.infra.database.repositories

import com.ead.katalyst.example.infra.database.entities.AuthAccountEntity
import com.ead.katalyst.example.infra.database.tables.AuthAccountsTable
import com.ead.katalyst.repositories.Repository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

class AuthAccountRepository : Repository<Long, AuthAccountEntity> {
    override val table: LongIdTable = AuthAccountsTable

    override fun mapper(row: ResultRow): AuthAccountEntity = AuthAccountEntity(
        id = row[AuthAccountsTable.id].value,
        email = row[AuthAccountsTable.email],
        passwordHash = row[AuthAccountsTable.passwordHash],
        createdAtMillis = row[AuthAccountsTable.createdAtMillis],
        lastLoginAtMillis = row[AuthAccountsTable.lastLoginAtMillis]
    )

    fun findByEmail(email: String): AuthAccountEntity? =
        AuthAccountsTable
            .selectAll().where { AuthAccountsTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?.let(::mapper)
}
