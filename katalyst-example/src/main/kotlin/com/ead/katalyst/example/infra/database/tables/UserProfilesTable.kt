package com.ead.katalyst.example.infra.database.tables

import com.ead.katalyst.core.persistence.Table
import com.ead.katalyst.example.infra.database.entities.UserProfileEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * Exposed definition + entity mappers for the `user_profiles` table.
 *
 * Maps between the persistence layer (ResultRow) and domain layer (UserProfileEntity).
 * The explicit mapping functions ensure type safety and performance by avoiding reflection.
 *
 * **Mapping Contract:**
 * - [mapRow] transforms a database row into a fully-populated UserProfileEntity
 * - [assignEntity] populates an INSERT/UPDATE statement from a UserProfileEntity
 */
object UserProfilesTable : LongIdTable("user_profiles"), Table<Long, UserProfileEntity> {
    val accountId = reference(
        name = "account_id",
        foreign = AuthAccountsTable,
        onDelete = ReferenceOption.CASCADE
    )
    val displayName = varchar("display_name", 120)
    val bio = text("bio").nullable()
    val avatarUrl = varchar("avatar_url", 255).nullable()

    override fun mapRow(row: ResultRow): UserProfileEntity = UserProfileEntity(
        id = row[id].value,
        accountId = row[accountId].value,
        displayName = row[displayName],
        bio = row[bio],
        avatarUrl = row[avatarUrl]
    )

    override fun assignEntity(
        statement: UpdateBuilder<*>,
        entity: UserProfileEntity,
        skipIdColumn: Boolean
    ) {
        if (!skipIdColumn && entity.id != null) { statement[id] = EntityID(entity.id, this) }
        statement[accountId] = EntityID(entity.accountId, AuthAccountsTable)
        statement[displayName] = entity.displayName
        statement[bio] = entity.bio
        statement[avatarUrl] = entity.avatarUrl
    }
}
