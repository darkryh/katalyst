package io.github.darkryh.katalyst.example.infra.database.tables

import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.example.infra.database.entities.UserProfileEntity
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Exposed definition + entity mappers for the `user_profiles` table.
 *
 * Maps between the persistence layer (ResultRow) and domain layer (UserProfileEntity).
 * The explicit mapping functions ensure type safety and performance by avoiding reflection.
 *
 * **Mapping Contract:**
 * - [mapping] transforms database rows into fully-populated UserProfileEntity values
 * - [mapping] populates INSERT/UPDATE statements from UserProfileEntity values
 */
object UserProfilesTable : LongIdTable("katalyst_example_service.user_profiles"), Table<Long, UserProfileEntity> {
    val accountId = reference(
        name = "account_id",
        foreign = AuthAccountsTable,
        onDelete = ReferenceOption.CASCADE
    )
    val displayName = varchar("display_name", 120)
    val bio = text("bio").nullable()
    val avatarUrl = varchar("avatar_url", 255).nullable()

    override val mapping = mapping<Long, UserProfileEntity> {
        generatedId(id, UserProfileEntity::id)
        reference(accountId, UserProfileEntity::accountId)
        field(displayName, UserProfileEntity::displayName)
        field(bio, UserProfileEntity::bio)
        field(avatarUrl, UserProfileEntity::avatarUrl)

        construct {
            UserProfileEntity(
                id = this[id],
                accountId = this[accountId],
                displayName = this[displayName],
                bio = this[bio],
                avatarUrl = this[avatarUrl]
            )
        }
    }
}
