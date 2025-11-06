package com.ead.katalyst.example.infra.database.repositories

import com.ead.katalyst.example.infra.database.entities.UserEntity
import com.ead.katalyst.example.infra.database.tables.UsersTable
import com.ead.katalyst.repositories.Repository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select

class UserRepository : Repository<Long, UserEntity> {
    override val table: LongIdTable = UsersTable

    override fun mapper(row: ResultRow): UserEntity = UserEntity(
        id = row[UsersTable.id].value,
        name = row[UsersTable.name],
        email = row[UsersTable.email],
        active = row[UsersTable.active]
    )

    fun findByEmail(email: String): UserEntity? =
        UsersTable
            .select { UsersTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?.let(::mapper)

    fun deleteInactive() {
        table.deleteWhere { UsersTable.active eq false }
    }

    fun countActive(): Long =
        UsersTable
            .select { UsersTable.active eq true }
            .count()
}
