package com.ead.katalyst.example.infra.database.repositories

import com.ead.katalyst.example.infra.database.entities.UserProfileEntity
import com.ead.katalyst.example.infra.database.tables.UserProfilesTable
import com.ead.katalyst.repositories.Repository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

class UserProfileRepository : Repository<Long, UserProfileEntity> {
    override val table: LongIdTable = UserProfilesTable
    override fun map(row: ResultRow): UserProfileEntity = UserProfileEntity(
        id = row[UserProfilesTable.id].value,
        accountId = row[UserProfilesTable.accountId].value,
        displayName = row[UserProfilesTable.displayName],
        bio = row[UserProfilesTable.bio],
        avatarUrl = row[UserProfilesTable.avatarUrl]
    )

    fun findByAccountId(accountId: Long): UserProfileEntity? =
        UserProfilesTable
            .selectAll().where { UserProfilesTable.accountId eq accountId }
            .limit(1)
            .firstOrNull()
            ?.let(::map)
}
